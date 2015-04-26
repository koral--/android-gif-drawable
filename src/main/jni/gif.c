#include "gif.h"

static ColorMapObject *defaultCmap;

/**
* Global VM reference, initialized in JNI_OnLoad
*/
static JavaVM *g_jvm;

static struct JavaVMAttachArgs attachArgs = {.version=JNI_VERSION_1_6, .group=NULL, .name="GifIOThread"};

inline JNIEnv *getEnv() {
    JNIEnv *env;

    if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, &attachArgs) == JNI_OK)
        return env;
    return NULL;
}

inline void DetachCurrentThread() {
    (*g_jvm)->DetachCurrentThread(g_jvm);
}

static uint_fast8_t fileRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
    FILE *file = (FILE *) gif->UserData;
    return (uint_fast8_t) fread(bytes, 1, size, file);
}

static uint_fast8_t directByteBufferReadFun(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
    DirectByteBufferContainer *dbbc = gif->UserData;
    if (dbbc->pos + size > dbbc->capacity)
        size -= dbbc->pos + size - dbbc->capacity;
    memcpy(bytes, dbbc->bytes + dbbc->pos, (size_t) size);
    dbbc->pos += size;
    return size;
}

static uint_fast8_t byteArrayReadFun(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
    ByteArrayContainer *bac = gif->UserData;
    JNIEnv *env = getEnv();
    if (!env)
        return 0;
    if (bac->pos + size > bac->arrLen)
        size -= bac->pos + size - bac->arrLen;
    (*env)->GetByteArrayRegion(env, bac->buffer, (jsize) bac->pos, size, (jbyte *) bytes);
    bac->pos += size;
    return size;
}

static uint_fast8_t streamReadFun(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
    StreamContainer *sc = gif->UserData;
    JNIEnv *env = getEnv();
    if (env == NULL || (*env)->MonitorEnter(env, sc->stream) != 0)
        return 0;

    if (sc->buffer == NULL) {
        jbyteArray buffer = (*env)->NewByteArray(env, size < 256 ? 256 : size);
        if (buffer == NULL)
            return 0;
        sc->buffer = (*env)->NewGlobalRef(env, buffer);
    }
    else {
        jsize bufLen = (*env)->GetArrayLength(env, sc->buffer);
        if (bufLen < size) {
            (*env)->DeleteGlobalRef(env, sc->buffer);

            jbyteArray buffer = (*env)->NewByteArray(env, size);
            if (buffer == NULL) {
                sc->buffer = NULL;
                return 0;
            }
            sc->buffer = (*env)->NewGlobalRef(env, buffer);
        }
    }
    if (sc->buffer == NULL)
        return 0;

    jint len = (*env)->CallIntMethod(env, sc->stream, sc->readMID, sc->buffer, 0, size);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        len = 0;
    }
    else if (len > 0) {
        (*env)->GetByteArrayRegion(env, sc->buffer, 0, len, (jbyte *) bytes);
    }
    if ((*env)->MonitorExit(env, sc->stream) != 0)
        len = 0;

    return (uint_fast8_t) (len >= 0 ? len : 0);
}

static int fileRewind(GifInfo *info) {
    if (fseek(info->gifFilePtr->UserData, info->startPos, SEEK_SET) == 0)
        return 0;
    info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
    return -1;
}

static int streamRewind(GifInfo *info) {
    GifFileType *gif = info->gifFilePtr;
    StreamContainer *sc = gif->UserData;
    JNIEnv *env = getEnv();
    if (env == NULL) {
        info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
        return -1;
    }
    (*env)->CallVoidMethod(env, sc->stream, sc->resetMID);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
        return -1;
    }
    return 0;
}

static int byteArrayRewind(GifInfo *info) {
    ByteArrayContainer *bac = info->gifFilePtr->UserData;
    bac->pos = (uint_fast32_t) info->startPos;
    return 0;
}

static int directByteBufferRewindFun(GifInfo *info) {
    DirectByteBufferContainer *dbbc = info->gifFilePtr->UserData;
    dbbc->pos = info->startPos;
    return 0;
}

__unused JNIEXPORT jobject JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openFile(JNIEnv *env, jclass __unused class,
                                                 jstring jfname, jboolean justDecodeMetaData) {
    if (isSourceNull(jfname, env)) {
        return NULL;
    }

    const char *const filename = (*env)->GetStringUTFChars(env, jfname, NULL);
    if (filename == NULL) {
        throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, "GetStringUTFChars failed");
        return NULL;
    }
    FILE *file = fopen(filename, "rb");
    (*env)->ReleaseStringUTFChars(env, jfname, filename);
    if (file == NULL) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }
    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(file, &fileRead, &descriptor.Error);
    descriptor.rewindFunc = fileRewind;
    descriptor.startPos = ftell(file);
    struct stat st;
    descriptor.sourceLength = stat(filename, &st) == 0 ? st.st_size : -1;
    jobject gifHandle = createGifHandle(&descriptor, env, justDecodeMetaData);
    if (gifHandle == NULL) {
        fclose(file);
    }
    return gifHandle;
}

__unused JNIEXPORT jobject JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openByteArray(JNIEnv *env, jclass __unused class,
                                                      jbyteArray bytes, jboolean justDecodeMetaData) {
    if (isSourceNull(bytes, env)) {
        return NULL;
    }
    ByteArrayContainer *container = malloc(sizeof(ByteArrayContainer));
    if (container == NULL) {
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL;
    }
    container->buffer = (*env)->NewGlobalRef(env, bytes);
    if (container->buffer == NULL) {
        free(container);
        throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, "NewGlobalRef failed");
        return NULL;
    }
    container->arrLen = (*env)->GetArrayLength(env, container->buffer);
    container->pos = 0;
    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(container, &byteArrayReadFun, &descriptor.Error);
    descriptor.rewindFunc = byteArrayRewind;
    descriptor.startPos = container->pos;
    descriptor.sourceLength = container->arrLen;

    jobject gifInfoHandle = createGifHandle(&descriptor, env, justDecodeMetaData);

    if (gifInfoHandle == NULL) {
        (*env)->DeleteGlobalRef(env, container->buffer);
        free(container);
    }
    return gifInfoHandle;
}

__unused JNIEXPORT jobject JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openDirectByteBuffer(JNIEnv *env,
                                                             jclass __unused class, jobject buffer,
                                                             jboolean justDecodeMetaData) {
    jbyte *bytes = (*env)->GetDirectBufferAddress(env, buffer);
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    if (bytes == NULL || capacity <= 0) {
        if (!isSourceNull(buffer, env))
            throwGifIOException(D_GIF_ERR_INVALID_BYTE_BUFFER, env);
        return NULL;
    }
    DirectByteBufferContainer *container = malloc(sizeof(DirectByteBufferContainer));
    if (container == NULL) {
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL;
    }
    container->bytes = bytes;
    container->capacity = capacity;
    container->pos = 0;

    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(container, &directByteBufferReadFun, &descriptor.Error);
    descriptor.rewindFunc = directByteBufferRewindFun;
    descriptor.startPos = container->pos;
    descriptor.sourceLength = container->capacity;

    jobject gifInfoHandle = createGifHandle(&descriptor, env, justDecodeMetaData);

    if (gifInfoHandle == NULL) {
        free(container);
    }
    return gifInfoHandle;
}

__unused JNIEXPORT jobject JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openStream(JNIEnv *env, jclass __unused class,
                                                   jobject stream, jboolean justDecodeMetaData) {
    jclass streamCls = (*env)->NewGlobalRef(env, (*env)->GetObjectClass(env, stream));
    if (streamCls == NULL) {
        throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, "NewGlobalRef failed");
        return NULL;
    }
    jmethodID markMID = (*env)->GetMethodID(env, streamCls, "mark", "(I)V");
    jmethodID readMID = (*env)->GetMethodID(env, streamCls, "read", "([BII)I");
    jmethodID resetMID = (*env)->GetMethodID(env, streamCls, "reset", "()V");

    if (markMID == 0 || readMID == 0 || resetMID == 0) {
        (*env)->DeleteGlobalRef(env, streamCls);
        return NULL;
    }

    StreamContainer *container = malloc(sizeof(StreamContainer));
    if (container == NULL) {
        (*env)->DeleteGlobalRef(env, streamCls);
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL;
    }
    container->readMID = readMID;
    container->resetMID = resetMID;
    container->stream = (*env)->NewGlobalRef(env, stream);
    if (container->stream == NULL) {
        free(container);
        (*env)->DeleteGlobalRef(env, streamCls);
        throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, "NewGlobalRef failed");
        return NULL;
    }
    container->streamCls = streamCls;
    container->buffer = NULL;

    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(container, &streamReadFun, &descriptor.Error);
    descriptor.startPos = 0;
    descriptor.rewindFunc = streamRewind;
    descriptor.sourceLength = -1;

    jobject gifInfoHandle;
    (*env)->CallVoidMethod(env, stream, markMID, LONG_MAX);
    if (!(*env)->ExceptionCheck(env))
        gifInfoHandle = createGifHandle(&descriptor, env, justDecodeMetaData);
    else {
        gifInfoHandle = NULL;
    }

    if (gifInfoHandle == NULL) {
        (*env)->DeleteGlobalRef(env, streamCls);
        (*env)->DeleteGlobalRef(env, container->stream);
        if (container->buffer != NULL)
            (*env)->DeleteGlobalRef(env, container->buffer);
        free(container);
    }
    return gifInfoHandle;
}

__unused JNIEXPORT jobject JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openFd(JNIEnv *env, jclass __unused handleClass,
                                               jobject jfd, jlong offset, jboolean justDecodeMetaData) {
    if (isSourceNull(jfd, env)) {
        return NULL;
    }
    jclass fdClass = (*env)->GetObjectClass(env, jfd);
    jfieldID fdClassDescriptorFieldID = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
    if (fdClassDescriptorFieldID == NULL) {
        return NULL;
    }
    const int fd = dup((*env)->GetIntField(env, jfd, fdClassDescriptorFieldID));
    if (fd == -1) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }
    FILE *file = fdopen(fd, "rb");
    if (file == NULL) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }
    jobject gifHandle;
    if (fseek(file, offset, SEEK_SET) == 0) {
        GifSourceDescriptor descriptor;
        descriptor.GifFileIn = DGifOpen(file, &fileRead, &descriptor.Error);
        descriptor.rewindFunc = fileRewind;
        descriptor.startPos = ftell(file);
        struct stat st;
        descriptor.sourceLength = fstat(fd, &st) == 0 ? st.st_size : -1;
        gifHandle = createGifHandle(&descriptor, env, justDecodeMetaData);
    }
    else {
        fclose(file);
        gifHandle = NULL;
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
    }

    return gifHandle;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_free(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;
    if (info->rewindFunction == streamRewind) {
        StreamContainer *sc = info->gifFilePtr->UserData;
        jmethodID closeMID = (*env)->GetMethodID(env, sc->streamCls, "close", "()V");
        if (closeMID != NULL)
            (*env)->CallVoidMethod(env, sc->stream, closeMID);
        if ((*env)->ExceptionCheck(env))
            (*env)->ExceptionClear(env);

        (*env)->DeleteGlobalRef(env, sc->streamCls);
        (*env)->DeleteGlobalRef(env, sc->stream);

        if (sc->buffer != NULL) {
            (*env)->DeleteGlobalRef(env, sc->buffer);
        }

        free(sc);
    }
    else if (info->rewindFunction == fileRewind) {
        fclose(info->gifFilePtr->UserData);
    }
    else if (info->rewindFunction == byteArrayRewind) {
        ByteArrayContainer *bac = info->gifFilePtr->UserData;
        if (bac->buffer != NULL) {
            (*env)->DeleteGlobalRef(env, bac->buffer);
        }
        free(bac);
    }
    else if (info->rewindFunction == directByteBufferRewindFun) {
        free(info->gifFilePtr->UserData);
    }
    info->gifFilePtr->UserData = NULL;
    releaseSurfaceDescriptor(info->surfaceDescriptor, env);
    cleanUp(info);
}

__unused JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *__unused reserved) {
    g_jvm = vm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) (&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    defaultCmap = GifMakeMapObject(8, NULL);
    if (defaultCmap != NULL) {
        uint_fast16_t iColor;
        for (iColor = 1; iColor < 256; iColor++) {
            defaultCmap->Colors[iColor].Red = (GifByteType) iColor;
            defaultCmap->Colors[iColor].Green = (GifByteType) iColor;
            defaultCmap->Colors[iColor].Blue = (GifByteType) iColor;
        }
    }
    else
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);

    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC_RAW, &ts) == -1) {
        //sanity check here instead of on each clock_gettime() call
        throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, "CLOCK_MONOTONIC_RAW is not present");
    }
    return JNI_VERSION_1_6;
}

__unused JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *__unused vm, void *__unused reserved) {
    GifFreeMapObject(defaultCmap);
}

inline ColorMapObject *getDefColorMap() {
    return defaultCmap;
}