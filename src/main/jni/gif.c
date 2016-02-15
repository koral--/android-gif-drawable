#include "gif.h"

static ColorMapObject *defaultCmap;
static const jlong NULL_GIF_INFO = (jlong) (intptr_t) NULL;

/**
* Global VM reference, initialized in JNI_OnLoad
*/
static JavaVM *g_jvm;

static struct JavaVMAttachArgs attachArgs = {.version=JNI_VERSION_1_6, .group=NULL, .name="GifIOThread"};

static inline JNIEnv *getEnv() {
    JNIEnv *env;

    if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, &attachArgs) == JNI_OK)
        return env;
    return NULL;
}

void DetachCurrentThread() {
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

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openFile(JNIEnv *env, jclass __unused class,
                                                 jstring jfname, jboolean justDecodeMetaData) {
    if (isSourceNull(jfname, env)) {
        return NULL_GIF_INFO;
    }

    const char *const filename = (*env)->GetStringUTFChars(env, jfname, NULL);
    if (filename == NULL) {
        throwException(env, RUNTIME_EXCEPTION_BARE, "GetStringUTFChars failed");
        return NULL_GIF_INFO;
    }
    FILE *file = fopen(filename, "rb");
    (*env)->ReleaseStringUTFChars(env, jfname, filename);
    if (file == NULL) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL_GIF_INFO;
    }
    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(file, &fileRead, &descriptor.Error);
    descriptor.rewindFunc = fileRewind;
    descriptor.startPos = ftell(file);
    struct stat st;
    descriptor.sourceLength = stat(filename, &st) == 0 ? st.st_size : -1;
    GifInfo *info = createGifHandle(&descriptor, env, justDecodeMetaData);
    if (info == NULL) {
        fclose(file);
    }
    return (jlong) (intptr_t) info;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openByteArray(JNIEnv *env, jclass __unused class,
                                                      jbyteArray bytes, jboolean justDecodeMetaData) {
    if (isSourceNull(bytes, env)) {
        return NULL_GIF_INFO;
    }
    ByteArrayContainer *container = malloc(sizeof(ByteArrayContainer));
    if (container == NULL) {
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL_GIF_INFO;
    }
    container->buffer = (*env)->NewGlobalRef(env, bytes);
    if (container->buffer == NULL) {
        free(container);
        throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
        return NULL_GIF_INFO;
    }
    container->arrLen = (*env)->GetArrayLength(env, container->buffer);
    container->pos = 0;
    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(container, &byteArrayReadFun, &descriptor.Error);
    descriptor.rewindFunc = byteArrayRewind;
    descriptor.startPos = container->pos;
    descriptor.sourceLength = container->arrLen;

    GifInfo *info = createGifHandle(&descriptor, env, justDecodeMetaData);

    if (info == NULL) {
        (*env)->DeleteGlobalRef(env, container->buffer);
        free(container);
    }
    return (jlong) (intptr_t) info;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openDirectByteBuffer(JNIEnv *env, jclass __unused class, jobject buffer, jboolean justDecodeMetaData) {
    jbyte *bytes = (*env)->GetDirectBufferAddress(env, buffer);
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    if (bytes == NULL || capacity <= 0) {
        if (!isSourceNull(buffer, env))
            throwGifIOException(D_GIF_ERR_INVALID_BYTE_BUFFER, env);
        return NULL_GIF_INFO;
    }
    DirectByteBufferContainer *container = malloc(sizeof(DirectByteBufferContainer));
    if (container == NULL) {
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL_GIF_INFO;
    }
    container->bytes = bytes;
    container->capacity = capacity;
    container->pos = 0;

    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(container, &directByteBufferReadFun, &descriptor.Error);
    descriptor.rewindFunc = directByteBufferRewindFun;
    descriptor.startPos = container->pos;
    descriptor.sourceLength = container->capacity;

    GifInfo *info = createGifHandle(&descriptor, env, justDecodeMetaData);
    if (info == NULL) {
        free(container);
    }
    return (jlong) (intptr_t) info;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openStream(JNIEnv *env, jclass __unused class, jobject stream, jboolean justDecodeMetaData) {
    jclass streamCls = (*env)->NewGlobalRef(env, (*env)->GetObjectClass(env, stream));
    if (streamCls == NULL) {
        throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
        return NULL_GIF_INFO;
    }
    jmethodID markMID = (*env)->GetMethodID(env, streamCls, "mark", "(I)V");
    jmethodID readMID = (*env)->GetMethodID(env, streamCls, "read", "([BII)I");
    jmethodID resetMID = (*env)->GetMethodID(env, streamCls, "reset", "()V");

    if (markMID == 0 || readMID == 0 || resetMID == 0) {
        (*env)->DeleteGlobalRef(env, streamCls);
        return NULL_GIF_INFO;
    }

    StreamContainer *container = malloc(sizeof(StreamContainer));
    if (container == NULL) {
        (*env)->DeleteGlobalRef(env, streamCls);
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL_GIF_INFO;
    }

    container->buffer = (*env)->NewByteArray(env, 256);
    if (container->buffer == NULL) {
        (*env)->DeleteGlobalRef(env, streamCls);
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL_GIF_INFO;
    }
    container->buffer = (*env)->NewGlobalRef(env, container->buffer);
    if (container->buffer == NULL) {
        throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
        return NULL_GIF_INFO;
    }

    container->readMID = readMID;
    container->resetMID = resetMID;
    container->stream = (*env)->NewGlobalRef(env, stream);
    if (container->stream == NULL) {
        free(container);
        (*env)->DeleteGlobalRef(env, streamCls);
        throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
        return NULL_GIF_INFO;
    }
    container->streamCls = streamCls;

    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(container, &streamReadFun, &descriptor.Error);
    descriptor.startPos = 0;
    descriptor.rewindFunc = streamRewind;
    descriptor.sourceLength = -1;

    (*env)->CallVoidMethod(env, stream, markMID, LONG_MAX);
    if (!(*env)->ExceptionCheck(env)) {
        GifInfo *info = createGifHandle(&descriptor, env, justDecodeMetaData);
        return (jlong) (intptr_t) info;
    } else {
        (*env)->DeleteGlobalRef(env, streamCls);
        (*env)->DeleteGlobalRef(env, container->stream);
        (*env)->DeleteGlobalRef(env, container->buffer);
        free(container);
        return NULL_GIF_INFO;
    }
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openFd(JNIEnv *env, jclass __unused handleClass, jobject jfd, jlong offset, jboolean justDecodeMetaData) {
    if (isSourceNull(jfd, env)) {
        return NULL_GIF_INFO;
    }
    jclass fdClass = (*env)->GetObjectClass(env, jfd);
    jfieldID fdClassDescriptorFieldID = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
    if (fdClassDescriptorFieldID == NULL) {
        return NULL_GIF_INFO;
    }
    const int fd = dup((*env)->GetIntField(env, jfd, fdClassDescriptorFieldID));
    if (fd == -1) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL_GIF_INFO;
    }
    FILE *file = fdopen(fd, "rb");
    if (file == NULL) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL_GIF_INFO;
    }
    GifInfo *info;
    if (fseek(file, offset, SEEK_SET) == 0) {
        GifSourceDescriptor descriptor;
        descriptor.GifFileIn = DGifOpen(file, &fileRead, &descriptor.Error);
        descriptor.rewindFunc = fileRewind;
        descriptor.startPos = ftell(file);
        struct stat st;
        descriptor.sourceLength = fstat(fd, &st) == 0 ? st.st_size : -1;
        info = createGifHandle(&descriptor, env, justDecodeMetaData);
    }
    else {
        fclose(file);
        info = NULL;
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
    }

    return (jlong) (intptr_t) info;
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

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_setSampleSize(__unused JNIEnv *env, jclass __unused class, jlong gifInfo, jint sampleSize) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;

    info->sampleSize = (uint_fast16_t) sampleSize;
    info->gifFilePtr->SHeight /= info->sampleSize;
    info->gifFilePtr->SWidth /= info->sampleSize;
    if (info->gifFilePtr->SHeight == 0) {
        info->gifFilePtr->SHeight = 1;
    }
    if (info->gifFilePtr->SWidth == 0) {
        info->gifFilePtr->SWidth = 1;
    }

    SavedImage *sp;
    uint_fast32_t i;
    for (i = 0; i < info->gifFilePtr->ImageCount; i++) {
        sp = &info->gifFilePtr->SavedImages[i];
        sp->ImageDesc.Width /= info->sampleSize;
        sp->ImageDesc.Height /= info->sampleSize;
        sp->ImageDesc.Left /= info->sampleSize;
        sp->ImageDesc.Top /= info->sampleSize;
    }
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getWidth(__unused JNIEnv *env, jclass __unused class, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    return (jint) info->gifFilePtr->SWidth;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getHeight(__unused JNIEnv *env, jclass __unused class, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    return (jint) info->gifFilePtr->SHeight;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getNumberOfFrames(__unused JNIEnv *env, jclass __unused class, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    return (jint) info->gifFilePtr->ImageCount;
}

__unused JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *__unused reserved) {
    g_jvm = vm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
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
        throwException(env, RUNTIME_EXCEPTION_BARE, "CLOCK_MONOTONIC_RAW is not present");
    }
    return JNI_VERSION_1_6;
}

__unused JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *__unused vm, void *__unused reserved) {
    GifFreeMapObject(defaultCmap);
}

ColorMapObject *getDefColorMap() {
    return defaultCmap;
}
