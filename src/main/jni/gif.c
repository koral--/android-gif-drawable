#include "gif.h"

/**
* Global VM reference, initialized in JNI_OnLoad
*/
static JavaVM *g_jvm;

static ColorMapObject *genDefColorMap(void) {
    ColorMapObject *cmap = GifMakeMapObject(256, NULL);
    if (cmap != NULL) {
        int iColor;
        for (iColor = 0; iColor < 256; iColor++) {
            cmap->Colors[iColor].Red = (GifByteType) iColor;
            cmap->Colors[iColor].Green = (GifByteType) iColor;
            cmap->Colors[iColor].Blue = (GifByteType) iColor;
        }
    }
    return cmap;
}

static void cleanUp(GifInfo *info) {
    free(info->backupPtr);
    info->backupPtr = NULL;
    free(info->infos);
    info->infos = NULL;
    free(info->rasterBits);
    info->rasterBits = NULL;
    free(info->comment);
    info->comment = NULL;

    GifFileType *GifFile = info->gifFilePtr;
    if (GifFile->SColorMap == defaultCmap)
        GifFile->SColorMap = NULL;

    DGifCloseFile(GifFile);
    free(info);
}

static int fileRead(GifFileType *gif, GifByteType *bytes, int size) {
    FILE *file = (FILE *) gif->UserData;
    return (int) fread(bytes, 1, (size_t) size, file);
}

static inline JNIEnv *getEnv(void) {
    JNIEnv *env;
    if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) == JNI_OK)
        return env;
    return NULL;
}

static int directByteBufferReadFun(GifFileType *gif, GifByteType *bytes, int size) {
    DirectByteBufferContainer *dbbc = gif->UserData;
    if (dbbc->pos + size > dbbc->capacity)
        size -= dbbc->pos + size - dbbc->capacity;
    memcpy(bytes, dbbc->bytes + dbbc->pos, (size_t) size);
    dbbc->pos += size;
    return size;
}

static int byteArrayReadFun(GifFileType *gif, GifByteType *bytes, int size) {
    ByteArrayContainer *bac = gif->UserData;
    JNIEnv *env;
    (*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL);
    if (bac->pos + size > bac->arrLen)
        size -= bac->pos + size - bac->arrLen;
    (*env)->GetByteArrayRegion(env, bac->buffer, (jsize) bac->pos, size, (jbyte *) bytes);
    bac->pos += size;
    return size;
}

static int streamReadFun(GifFileType *gif, GifByteType *bytes, int size) {
    StreamContainer *sc = gif->UserData;
    JNIEnv *env = getEnv();
    if (env == NULL)
        return 0;

    (*env)->MonitorEnter(env, sc->stream);

    if (sc->buffer == NULL) {
        jbyteArray buffer = (*env)->NewByteArray(env, size < 256 ? 256 : size);
        sc->buffer = (*env)->NewGlobalRef(env, buffer);
    }
    else {
        jsize bufLen = (*env)->GetArrayLength(env, sc->buffer);
        if (bufLen < size) {
            (*env)->DeleteGlobalRef(env, sc->buffer);
            sc->buffer = NULL;

            jbyteArray buffer = (*env)->NewByteArray(env, size);
            sc->buffer = (*env)->NewGlobalRef(env, buffer);
        }
    }

    int len = (*env)->CallIntMethod(env, sc->stream, sc->readMID, sc->buffer, 0, size);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        len = 0;
    }
    else if (len > 0) {
        (*env)->GetByteArrayRegion(env, sc->buffer, 0, len, (jbyte *) bytes);
    }

    (*env)->MonitorExit(env, sc->stream);

    return len >= 0 ? len : 0;
}

static int fileRewind(GifInfo *info) {
    return fseek(info->gifFilePtr->UserData, info->startPos, SEEK_SET);
}

static int streamRewind(GifInfo *info) {
    GifFileType *gif = info->gifFilePtr;
    StreamContainer *sc = gif->UserData;
    JNIEnv *env = getEnv();
    if (env == NULL)
        return -1;
    (*env)->CallVoidMethod(env, sc->stream, sc->resetMID);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    return 0;
}

static int byteArrayRewind(GifInfo *info) {
    GifFileType *gif = info->gifFilePtr;
    ByteArrayContainer *bac = gif->UserData;
    bac->pos = info->startPos;
    return 0;
}

static int directByteBufferRewindFun(GifInfo *info) {
    GifFileType *gif = info->gifFilePtr;
    DirectByteBufferContainer *dbbc = gif->UserData;
    dbbc->pos = info->startPos;
    return 0;
}

static jobject createGifHandle(GifSourceDescriptor *descriptor, JNIEnv *env, jboolean justDecodeMetaData) {
    if (descriptor->startPos < 0) {
        descriptor->Error = D_GIF_ERR_NOT_READABLE;
        DGifCloseFile(descriptor->GifFileIn);
    }
    if (descriptor->Error != 0 || descriptor->GifFileIn == NULL) {
        throwGifIOException(descriptor->Error, env);
        return NULL;
    }
    int width = descriptor->GifFileIn->SWidth, height = descriptor->GifFileIn->SHeight;
    int wxh = width * height;
    if (wxh < 1 || wxh > INT_MAX) {
        DGifCloseFile(descriptor->GifFileIn);
        throwGifIOException(D_GIF_ERR_INVALID_SCR_DIMS, env);
        return NULL;
    }
    GifInfo *info = malloc(sizeof(GifInfo));
    if (info == NULL) {
        DGifCloseFile(descriptor->GifFileIn);
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
        return NULL;
    }
    info->gifFilePtr = descriptor->GifFileIn;
    info->startPos = descriptor->startPos;
    info->currentIndex = -1;
    info->nextStartTime = 0;
    info->lastFrameRemainder = ULONG_MAX;
    info->comment = NULL;
    info->loopCount = 1;
    info->currentLoop = 0;
    info->speedFactor = 1.0;
    info->stride = width;
    info->sourceLength = descriptor->sourceLength;
    if (justDecodeMetaData == JNI_TRUE)
        info->rasterBits = NULL;
    else
        info->rasterBits = malloc(descriptor->GifFileIn->SHeight * descriptor->GifFileIn->SWidth * sizeof(GifPixelType));
    info->infos = malloc(sizeof(FrameInfo));
    info->backupPtr = NULL;
    info->rewindFunction = descriptor->rewindFunc;

    if ((info->rasterBits == NULL && justDecodeMetaData != JNI_TRUE) || info->infos == NULL) {
        cleanUp(info);
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
        return NULL;
    }
    info->infos->duration = 0;
    info->infos->disposalMethod = DISPOSAL_UNSPECIFIED;
    info->infos->transpIndex = NO_TRANSPARENT_COLOR;
    if (descriptor->GifFileIn->SColorMap == NULL || descriptor->GifFileIn->SColorMap->ColorCount != (1 << descriptor->GifFileIn->SColorMap->BitsPerPixel)) {
        GifFreeMapObject(descriptor->GifFileIn->SColorMap);
        descriptor->GifFileIn->SColorMap = defaultCmap;
    }

#if defined(STRICT_FORMAT_89A)
	if (DDGifSlurp(descriptor->GifFileIn, info, false) == GIF_descriptor->Error)
		descriptor->Error = descriptor->GifFileIn->descriptor->Error;
#else
    DDGifSlurp(descriptor->GifFileIn, info, false);
#endif

    if (descriptor->GifFileIn->ImageCount < 1) {
        descriptor->Error = D_GIF_ERR_NO_FRAMES;
    }
    else if (info->rewindFunction(info) != 0) {
        descriptor->Error = D_GIF_ERR_REWIND_FAILED;
    }
    if (descriptor->Error != 0) {
        cleanUp(info);
        throwGifIOException(descriptor->Error, env);
        return NULL;
    }
    jclass gifInfoHandleClass = (*env)->FindClass(env, "pl/droidsonroids/gif/GifInfoHandle");
    if (gifInfoHandleClass == NULL)
        return NULL;
    jmethodID gifInfoHandleCtorMID = (*env)->GetMethodID(env, gifInfoHandleClass, "<init>", "(JIII)V");
    if (gifInfoHandleCtorMID == NULL)
        return NULL;

    return (*env)->NewObject(env, gifInfoHandleClass, gifInfoHandleCtorMID,
            (jlong) (intptr_t) info, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight,
            info->gifFilePtr->ImageCount);
}

__unused JNIEXPORT jobject JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openFile(JNIEnv *env, jclass __unused class,
        jstring jfname, jboolean justDecodeMetaData) {
    if (isSourceNull(jfname, env)) {
        return NULL;
    }

    const char *const fname = (*env)->GetStringUTFChars(env, jfname, 0);
    FILE *file = fopen(fname, "rb");
    (*env)->ReleaseStringUTFChars(env, jfname, fname);
    if (file == NULL) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }
    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(file, &fileRead, &descriptor.Error);
    descriptor.rewindFunc = fileRewind;
    descriptor.startPos = ftell(file);
    struct stat st;
    descriptor.sourceLength = stat(fname, &st) == 0 ? st.st_size : -1;
    return createGifHandle(&descriptor, env, justDecodeMetaData);
}

__unused JNIEXPORT jobject JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openByteArray(JNIEnv *env, jclass __unused class,
        jbyteArray bytes, jboolean justDecodeMetaData) {
    if (isSourceNull(bytes, env)) {
        return NULL;
    }
    ByteArrayContainer *container = malloc(sizeof(ByteArrayContainer));
    if (container == NULL) {
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
        return NULL;
    }
    container->buffer = (*env)->NewGlobalRef(env, bytes);
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
        jclass __unused class, jobject buffer, jboolean justDecodeMetaData) {
    jbyte *bytes = (*env)->GetDirectBufferAddress(env, buffer);
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    if (bytes == NULL || capacity <= 0) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }
    DirectByteBufferContainer *container = malloc(sizeof(DirectByteBufferContainer));
    if (container == NULL) {
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
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
    jclass streamCls = (*env)->NewGlobalRef(env,
            (*env)->GetObjectClass(env, stream));
    jmethodID mid = (*env)->GetMethodID(env, streamCls, "mark", "(I)V");
    jmethodID readMID = (*env)->GetMethodID(env, streamCls, "read", "([BII)I");
    jmethodID resetMID = (*env)->GetMethodID(env, streamCls, "reset", "()V");

    if (mid == 0 || readMID == 0 || resetMID == 0) {
        (*env)->DeleteGlobalRef(env, streamCls);
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }

    StreamContainer *container = malloc(sizeof(StreamContainer));
    if (container == NULL) {
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
        return NULL;
    }
    container->readMID = readMID;
    container->resetMID = resetMID;

    container->stream = (*env)->NewGlobalRef(env, stream);
    container->streamCls = streamCls;
    container->buffer = NULL;

    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(container, &streamReadFun, &descriptor.Error);
    descriptor.startPos = 0;
    descriptor.rewindFunc = streamRewind;
    descriptor.sourceLength = -1;

    (*env)->CallVoidMethod(env, stream, mid, LONG_MAX); //TODO better length?

    jobject gifInfoHandle = createGifHandle(&descriptor, env, justDecodeMetaData);
    if (gifInfoHandle == NULL) {
        (*env)->DeleteGlobalRef(env, streamCls);
        (*env)->DeleteGlobalRef(env, container->stream);
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
    jfieldID fdClassDescriptorFieldID = (*env)->GetFieldID(env, fdClass,
            "descriptor", "I");
    if (fdClassDescriptorFieldID == NULL) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }
    const int fd = dup((*env)->GetIntField(env, jfd, fdClassDescriptorFieldID));
    FILE *file = fdopen(fd, "rb");
    if (file == NULL || fseek(file, offset, SEEK_SET) != 0) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }

    GifSourceDescriptor descriptor;
    descriptor.GifFileIn = DGifOpen(file, &fileRead, &descriptor.Error);
    descriptor.rewindFunc = fileRewind;
    descriptor.startPos = ftell(file);
    struct stat st;
    descriptor.sourceLength = fstat(fd, &st) == 0 ? st.st_size : -1;
    return createGifHandle(&descriptor, env, justDecodeMetaData);
}

bool reset(GifInfo *info) {
    if (info->rewindFunction(info) != 0)
        return false;
    info->nextStartTime = 0;
    info->currentLoop = 0;
    info->currentIndex = -1;
    info->lastFrameRemainder = ULONG_MAX;
    return true;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_reset(JNIEnv *__unused  env, jclass  __unused class,
        jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;
    reset(info);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_setSpeedFactor(JNIEnv __unused *env, jclass __unused handleClass,
        jlong gifInfo, jfloat factor) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;
    info->speedFactor = factor;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToTime(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jint desiredPos, jobject jbitmap) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;
    const int imgCount = info->gifFilePtr->ImageCount;
    if (imgCount <= 1)
        return;

    unsigned long sum = 0;
    int i;
    for (i = 0; i < imgCount; i++) {
        unsigned long newSum = sum + info->infos[i].duration;
        if (newSum >= desiredPos)
            break;
        sum = newSum;
    }

    if (i < info->currentIndex) {
        if (!reset(info)) {
            info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
            return;
        }
    }

    time_t lastFrameRemainder = desiredPos - sum;
    if (i == imgCount - 1 && lastFrameRemainder > info->infos[i].duration)
        lastFrameRemainder = info->infos[i].duration;
    if (i > info->currentIndex) {
        void *pixels;
        if (!lockPixels(env, jbitmap, &pixels, true)) {
            return;
        }
        while (info->currentIndex <= i) {
            info->currentIndex++;
            getBitmap((argb *) pixels, info);
        }
        unlockPixels(env, jbitmap);
    }
    info->lastFrameRemainder = lastFrameRemainder;

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime(env) + lastFrameRemainder;
    else
        info->nextStartTime = getRealTime(env) + (time_t) (lastFrameRemainder * info->speedFactor);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToFrame(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jint desiredIdx, jobject jbitmap) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;

    const int imgCount = info->gifFilePtr->ImageCount;
    if (imgCount <= 1)
        return;
    if (desiredIdx <= info->currentIndex) {
        if (!reset(info)) {
            info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
            return;
        }
    }

    void *pixels;
    if (!lockPixels(env, jbitmap, &pixels, true)) {
        return;
    }

    info->lastFrameRemainder = 0;
    if (desiredIdx >= imgCount)
        desiredIdx = imgCount - 1;

    while (info->currentIndex < desiredIdx) {
        info->currentIndex++;
        getBitmap((argb *) pixels, info);
    }
    unlockPixels(env, jbitmap);

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime(env) + info->infos[info->currentIndex].duration;
    else
        info->nextStartTime = getRealTime(env) + (time_t) (info->infos[info->currentIndex].duration * info->speedFactor);
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_renderFrame(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jobject jbitmap) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return PACK_RENDER_FRAME_RESULT(-1, false);
    bool needRedraw = false;
    time_t rt = getRealTime(env);
    bool isAnimationCompleted;
    if (rt >= info->nextStartTime) {
        if (++info->currentIndex >= info->gifFilePtr->ImageCount)
            info->currentIndex = 0;
        needRedraw = true;
        isAnimationCompleted = info->currentIndex >= info->gifFilePtr->ImageCount - 1;
    }
    else
        isAnimationCompleted = false;

    int invalidationDelay;
    if (needRedraw) {
        void *pixels = NULL;
        if (!lockPixels(env, jbitmap, &pixels, false)) {
            return PACK_RENDER_FRAME_RESULT(-1, false);
        }
        getBitmap((argb *) pixels, info);
        unlockPixels(env, jbitmap);
        invalidationDelay = calculateInvalidationDelay(info, rt, env);
    }
    else {
        time_t delay = info->nextStartTime - rt;
        if (delay < 0)
            invalidationDelay = -1;
        else //no need to check upper bound since info->nextStartTime<=rt+LONG_MAX always
            invalidationDelay = (int) delay;
    }
    if (invalidationDelay > 0) {//exclude rendering time
        invalidationDelay -= getRealTime(env) - rt;
        if (invalidationDelay < 0)
            invalidationDelay = 0;
    }
    return PACK_RENDER_FRAME_RESULT(invalidationDelay, isAnimationCompleted);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jobject jsurface, jlong startPosition) { //TODO start seeking

    jclass threadClass = (*env)->FindClass(env, "java/lang/Thread");
    if (threadClass == NULL)
        return;
    jmethodID currentThreadMID = (*env)->GetStaticMethodID(env, threadClass, "currentThread", "()Ljava/lang/Thread;");
    jobject jCurrentThread = (*env)->CallStaticObjectMethod(env, threadClass, currentThreadMID);
    jmethodID isInterruptedMID = (*env)->GetMethodID(env, threadClass, "isInterrupted", "()Z");
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info || !currentThreadMID || !jCurrentThread || !isInterruptedMID) {
        return;
    }

    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    if (ANativeWindow_setBuffersGeometry(window, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION, "Buffers geometry setting failed");
        return;
    }

    struct ANativeWindow_Buffer buffer;
    buffer.bits = NULL;
    struct timespec time_to_sleep;

    void *oldBufferBits;
    while ((*env)->CallBooleanMethod(env, jCurrentThread, isInterruptedMID) == JNI_FALSE) {
        if (++info->currentIndex >= info->gifFilePtr->ImageCount) {
            info->currentIndex = 0;
        }

        oldBufferBits = buffer.bits;
        if (ANativeWindow_lock(window, &buffer, NULL) != 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION, "Window lock failed");
            break;
        }
        if (oldBufferBits != NULL)
            memcpy(buffer.bits, oldBufferBits, buffer.stride * buffer.height * sizeof(argb));
        if (buffer.stride != info->stride) {
            if (info->backupPtr != NULL) {
                void *tmpBackupPtr = realloc(info->backupPtr, info->stride * info->gifFilePtr->SHeight * sizeof(argb));
                if (tmpBackupPtr == NULL) {
                    ANativeWindow_unlockAndPost(window);
                    throwException(env, OUT_OF_MEMORY_ERROR, "Failed to allocate native memory");
                    break;
                }
                info->backupPtr = tmpBackupPtr;
            }
            info->stride = buffer.stride;
        }
        getBitmap(buffer.bits, info);
        ANativeWindow_unlockAndPost(window);

        const int invalidationDelayMillis = calculateInvalidationDelay(info, 0, env);
        if (invalidationDelayMillis < 0) {
            break;
        }
        time_to_sleep.tv_nsec = (invalidationDelayMillis % 1000) * 1000000;
        time_to_sleep.tv_sec = invalidationDelayMillis / 1000;
        if (nanosleep(&time_to_sleep, NULL) != 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION, "Sleep failed");
            break;
        }
    }
    ANativeWindow_release(window);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_free(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo) {
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
        FILE *file = info->gifFilePtr->UserData;
        fclose(file);
    }
    else if (info->rewindFunction == byteArrayRewind) {
        ByteArrayContainer *bac = info->gifFilePtr->UserData;
        if (bac->buffer != NULL) {
            (*env)->DeleteGlobalRef(env, bac->buffer);
        }
        free(bac);
    }
    else if (info->rewindFunction == directByteBufferRewindFun) {
        DirectByteBufferContainer *dbbc = info->gifFilePtr->UserData;
        free(dbbc);
    }
    info->gifFilePtr->UserData = NULL;
    cleanUp(info);
}

__unused JNIEXPORT jstring JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getComment(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return NULL;
    return (*env)->NewStringUTF(env, info->comment);
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getLoopCount(JNIEnv __unused *env, jclass __unused handleClass,
        jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    return info->loopCount;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getDuration(JNIEnv *__unused  env, jclass __unused handleClass,
        jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    int i;
    jint sum = 0;
    for (i = 0; i < info->gifFilePtr->ImageCount; i++)
        sum += info->infos[i].duration;
    return sum;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getSourceLength(JNIEnv __unused *env, jclass __unused handleClass,
        jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return -1;
    return info->sourceLength;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getCurrentPosition(JNIEnv *__unused env,
        jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    const int idx = info->currentIndex;
    if (idx < 0 || info->gifFilePtr->ImageCount <= 1)
        return 0;
    int i;
    unsigned int sum = 0;
    for (i = 0; i < idx; i++)
        sum += info->infos[i].duration;
    time_t remainder;
    if (info->lastFrameRemainder == ULONG_MAX) {
        remainder = info->nextStartTime - getRealTime(env);
        if (remainder < 0) //in case of if frame hasn't been rendered until nextStartTime passed
            remainder = 0;
    }
    else
        remainder = info->lastFrameRemainder;
    return (jint) (sum + remainder); //2^31-1[ms]>596[h] so jint is enough
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_saveRemainder(JNIEnv *__unused  env, jclass __unused handleClass,
        jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;
    info->lastFrameRemainder = info->nextStartTime - getRealTime(env);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_restoreRemainder(JNIEnv *__unused env,
        jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL || info->lastFrameRemainder == ULONG_MAX || info->gifFilePtr->ImageCount <= 1)
        return;
    info->nextStartTime = getRealTime(env) + info->lastFrameRemainder;
    info->lastFrameRemainder = ULONG_MAX;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getAllocationByteCount(JNIEnv *__unused  env,
        jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    GifWord pxCount = info->gifFilePtr->SWidth + info->gifFilePtr->SHeight;
    size_t sum = pxCount * sizeof(char);
    if (info->backupPtr != NULL)
        sum += pxCount * sizeof(argb);
    return (jlong) sum;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getNativeErrorCode(JNIEnv *__unused  env,
        jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return 0;
    return info->gifFilePtr->Error;
}

__unused JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *__unused reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) (&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    g_jvm = vm;
    defaultCmap = genDefColorMap();
    if (defaultCmap == NULL)
        return -1;
    return JNI_VERSION_1_6;
}

__unused JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *__unused vm, void *__unused reserved) {
    GifFreeMapObject(defaultCmap);
}