#include "gif.h"

/**
* Global VM reference, initialized in JNI_OnLoad
*/
static JavaVM *g_jvm;

/**
* Global default color map, initialized by genDefColorMap(void)
*/
static ColorMapObject *defaultCmap;

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
    free(info->surfaceBackupPtr);
    info->surfaceBackupPtr = NULL;
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

static inline time_t getRealTime(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC_RAW, &ts) != -1)
        return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    return -1; //should not happen since ts is in addressable space and CLOCK_MONOTONIC_RAW should be present
}

static inline void throwException(JNIEnv *env, char *exceptionClass, char *message) {
    jclass exClass = (*env)->FindClass(env, exceptionClass);
    if (exClass != NULL)
        (*env)->ThrowNew(env, exClass, message);
}

static inline bool isSourceNull(void *ptr, JNIEnv *env) {
    if (ptr != NULL)
        return false;
    throwException(env, "java/lang/NullPointerException", "Input source is null");
    return true;
}

static bool lockPixels(JNIEnv *env, jobject jbitmap, void **pixels, bool throwOnError) {
    int i;
    int lockPixelsResult = 1;
    for (i = 0; i < 20; i++) { //#122 workaround
        lockPixelsResult = AndroidBitmap_lockPixels(env, jbitmap, pixels);
        if (lockPixelsResult == ANDROID_BITMAP_RESULT_SUCCESS) {
            return true;
        }
    }
    if (throwOnError) {
        char *message;
        switch (lockPixelsResult) {
            case ANDROID_BITMAP_RESULT_ALLOCATION_FAILED:
                message = "Lock pixels error, frame buffer allocation failed";
                break;
            case ANDROID_BITMAP_RESULT_BAD_PARAMETER:
                message = "Lock pixels error, bad parameter";
                break;
            case ANDROID_BITMAP_RESULT_JNI_EXCEPTION:
                message = "Lock pixels error, JNI exception";
                break;
            default:
                message = "Lock pixels error";
        }
        throwException(env, ILLEGAL_STATE_EXCEPTION, message);

    }
    return false;
}

static void unlockPixels(JNIEnv *env, jobject jbitmap) {
    const int unlockPixelsResult = AndroidBitmap_unlockPixels(env, jbitmap);
    if (unlockPixelsResult == ANDROID_BITMAP_RESULT_SUCCESS)
        return;
    char *message;
    switch (unlockPixelsResult) {
        case ANDROID_BITMAP_RESULT_BAD_PARAMETER:
            message = "Unlock pixels error, bad parameter";
            break;
        case ANDROID_BITMAP_RESULT_JNI_EXCEPTION:
            message = "Unlock pixels error, JNI exception";
            break;
        default:
            message = "Unlock pixels error";
    }
    throwException(env, ILLEGAL_STATE_EXCEPTION, message);
}

static int fileRead(GifFileType *gif, GifByteType *bytes, int size) {
    FILE *file = (FILE *) gif->UserData;
    return (int) fread(bytes, 1, (size_t) size, file);
}

static JNIEnv *getEnv(void) {
    JNIEnv *env = NULL;
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
    JNIEnv *env = NULL;
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

static int getComment(GifByteType *Bytes, char **cmt) {
    unsigned int len = (unsigned int) Bytes[0];
    size_t offset = *cmt != NULL ? strlen(*cmt) : 0;
    char *ret = realloc(*cmt, (len + offset + 1) * sizeof(char));
    if (ret != NULL) {
        memcpy(ret + offset, &Bytes[1], len);
        ret[len + offset] = 0;
        *cmt = ret;
        return GIF_OK;
    }
    return GIF_ERROR;
}

static inline bool setupBackupBmp(GifInfo *info, int transpIndex) {
    GifFileType *fGIF = info->gifFilePtr;
    info->backupPtr = calloc((size_t) (fGIF->SWidth * fGIF->SHeight), sizeof(argb));
    if (!info->backupPtr) {
        info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
        return false;
    }
    int backgroundColor;
    if (transpIndex != NO_TRANSPARENT_COLOR)
        backgroundColor = fGIF->SBackGroundColor;
    else
        backgroundColor = 0;
    memset(info->backupPtr, backgroundColor, fGIF->SWidth * fGIF->SHeight * sizeof(argb));
    return true;
}

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info) {
    if (ExtData == NULL)
        return GIF_OK;
    if (ExtFunction == GRAPHICS_EXT_FUNC_CODE) {
        GraphicsControlBlock GCB;
        if (DGifExtensionToGCB(ExtData[0], ExtData + 1, &GCB) == GIF_ERROR)
            return GIF_ERROR;

        FrameInfo *fi = &info->infos[info->gifFilePtr->ImageCount];
        fi->disposalMethod = (unsigned char) GCB.DisposalMode;
        fi->duration = GCB.DelayTime > 1 ? (unsigned int) GCB.DelayTime * 10 : 100;
        fi->transpIndex = GCB.TransparentColor;
        if (fi->disposalMethod == DISPOSE_PREVIOUS && info->backupPtr == NULL) {
            if (!setupBackupBmp(info, fi->transpIndex))
                return GIF_ERROR;
        }
    }
    else if (ExtFunction == COMMENT_EXT_FUNC_CODE) {
        if (getComment(ExtData, &info->comment) == GIF_ERROR) {
            info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
            return GIF_ERROR;
        }
    }
    else if (ExtFunction == APPLICATION_EXT_FUNC_CODE) {
        char const *string = (char const *) (ExtData + 1);
        if (strncmp("NETSCAPE2.0", string, ExtData[0]) == 0
                || strncmp("ANIMEXTS1.0", string, ExtData[0]) == 0) {
            if (DGifGetExtensionNext(info->gifFilePtr, &ExtData,
                    &ExtFunction) == GIF_ERROR)
                return GIF_ERROR;
            if (ExtData[0] == 3
                    && ExtData[1] == 1) {
                info->loopCount = (unsigned short) (ExtData[2]
                        + (ExtData[3] << 8));
            }
        }
    }
    return GIF_OK;
}

static int DDGifSlurp(GifFileType *GifFile, GifInfo *info, bool shouldDecode) {
    GifRecordType RecordType;
    GifByteType *ExtData;
    int codeSize;
    int ExtFunction;
    do {
        if (DGifGetRecordType(GifFile, &RecordType) == GIF_ERROR)
            return (GIF_ERROR);
        switch (RecordType) {
            case IMAGE_DESC_RECORD_TYPE:

                if (DGifGetImageDesc(GifFile, !shouldDecode) == GIF_ERROR)
                    return (GIF_ERROR);
                SavedImage *sp = &GifFile->SavedImages[(shouldDecode ? info->currentIndex : GifFile->ImageCount - 1)];
                const GifWord width = sp->ImageDesc.Width, height = sp->ImageDesc.Height;
                const int ImageSize = width * height;

                if ((width < 1) || (height < 1) || ImageSize > (SIZE_MAX / sizeof(GifPixelType))) {
                    GifFile->Error = D_GIF_ERR_INVALID_IMG_DIMS;
                    return GIF_ERROR;
                }
                if (width > GifFile->SWidth
                        || height > GifFile->SHeight) {
                    GifFile->Error = D_GIF_ERR_IMG_NOT_CONFINED;
                    return GIF_ERROR;
                }
                if (shouldDecode) {
                    if (sp->ImageDesc.Interlace) {
                        int i, j;
                        /*
                         * The way an interlaced image should be read -
                         * offsets and jumps...
                         */
                        int InterlacedOffset[] =
                                {0, 4, 2, 1};
                        int InterlacedJumps[] =
                                {8, 8, 4, 2};
                        /* Need to perform 4 passes on the image */
                        for (i = 0; i < 4; i++)
                            for (j = InterlacedOffset[i]; j < sp->ImageDesc.Height;
                                 j += InterlacedJumps[i]) {
                                if (DGifGetLine(GifFile,
                                        info->rasterBits + j * sp->ImageDesc.Width,
                                        sp->ImageDesc.Width) == GIF_ERROR)
                                    return GIF_ERROR;
                            }
                    }
                    else {
                        if (DGifGetLine(GifFile, info->rasterBits,
                                ImageSize) == GIF_ERROR)
                            return (GIF_ERROR);
                    }
                    if (info->currentIndex >= GifFile->ImageCount - 1) {
                        if (info->loopCount > 0)
                            info->currentLoop++;
                        if (info->rewindFunction(info) != 0) {
                            info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
                            return GIF_ERROR;
                        }
                    }
                    return GIF_OK;
                }
                else {
                    if (DGifGetCode(GifFile, &codeSize, &ExtData) == GIF_ERROR)
                        return (GIF_ERROR);
                    while (ExtData != NULL) {
                        if (DGifGetCodeNext(GifFile, &ExtData) == GIF_ERROR)
                            return (GIF_ERROR);
                    }
                }
                break;

            case EXTENSION_RECORD_TYPE:
                if (DGifGetExtension(GifFile, &ExtFunction, &ExtData) == GIF_ERROR)
                    return (GIF_ERROR);
                if (!shouldDecode) {
                    FrameInfo *tmpInfos = realloc(info->infos,
                            (GifFile->ImageCount + 1) * sizeof(FrameInfo));
                    if (tmpInfos == NULL)
                        return GIF_ERROR;
                    info->infos = tmpInfos;
                    if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR)
                        return GIF_ERROR;
                }
                while (ExtData != NULL) {
                    if (DGifGetExtensionNext(GifFile, &ExtData,
                            &ExtFunction) == GIF_ERROR)
                        return (GIF_ERROR);
                    if (!shouldDecode) {
                        if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR)
                            return GIF_ERROR;
                    }
                }
                break;

            case TERMINATE_RECORD_TYPE:
                break;

            default: /* Should be trapped by DGifGetRecordType */
                break;
        }
    } while (RecordType != TERMINATE_RECORD_TYPE);
    bool ok = true;
    if (shouldDecode) {
        ok = (info->rewindFunction(info) == 0);
    }
    if (ok)
        return (GIF_OK);
    else {
        info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
        return (GIF_ERROR);
    }
}

static void throwGifIOException(int errorCode, JNIEnv *env) {//nullchecks just to prevent segfaults, LinkageError will be thrown if GifIOException cannot be instantiated
    jclass exClass = (*env)->FindClass(env,
            "pl/droidsonroids/gif/GifIOException");
    if (exClass == NULL)
        return;
    jmethodID mid = (*env)->GetMethodID(env, exClass, "<init>", "(I)V");
    if (mid == NULL)
        return;
    jobject exception = (*env)->NewObject(env, exClass, mid, errorCode);
    if (exception != NULL)
        (*env)->Throw(env, exception);
}

static jobject createGifHandle(GifFileType *GifFileIn, int Error, long startPos, RewindFunc rewindFunc, JNIEnv *env, const jboolean justDecodeMetaData) {
    if (startPos < 0) {
        Error = D_GIF_ERR_NOT_READABLE;
        DGifCloseFile(GifFileIn);
    }
    if (Error != 0 || GifFileIn == NULL) {
        throwGifIOException(Error, env);
        return NULL;
    }
    int width = GifFileIn->SWidth, height = GifFileIn->SHeight;
    int wxh = width * height;
    if (wxh < 1 || wxh > INT_MAX) {
        DGifCloseFile(GifFileIn);
        throwGifIOException(D_GIF_ERR_INVALID_SCR_DIMS, env);
        return NULL;
    }
    GifInfo *info = malloc(sizeof(GifInfo));
    if (info == NULL) {
        DGifCloseFile(GifFileIn);
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
        return NULL;
    }
    info->gifFilePtr = GifFileIn;
    info->startPos = startPos;
    info->currentIndex = -1;
    info->nextStartTime = 0;
    info->lastFrameRemainder = ULONG_MAX;
    info->comment = NULL;
    info->loopCount = 1;
    info->currentLoop = 0;
    info->speedFactor = 1.0;
    if (justDecodeMetaData == JNI_TRUE)
        info->rasterBits = NULL;
    else
        info->rasterBits = calloc((size_t) (GifFileIn->SHeight * GifFileIn->SWidth),
                sizeof(GifPixelType));
    info->infos = malloc(sizeof(FrameInfo));
    info->backupPtr = NULL;
    info->surfaceBackupPtr = NULL;
    info->rewindFunction = rewindFunc;

    if ((info->rasterBits == NULL && justDecodeMetaData != JNI_TRUE) || info->infos == NULL) {
        cleanUp(info);
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
        return NULL;
    }
    info->infos->duration = 0;
    info->infos->disposalMethod = DISPOSAL_UNSPECIFIED;
    info->infos->transpIndex = NO_TRANSPARENT_COLOR;
    if (GifFileIn->SColorMap == NULL
            || GifFileIn->SColorMap->ColorCount
            != (1 << GifFileIn->SColorMap->BitsPerPixel)) {
        GifFreeMapObject(GifFileIn->SColorMap);
        GifFileIn->SColorMap = defaultCmap;
    }

#if defined(STRICT_FORMAT_89A)
	if (DDGifSlurp(GifFileIn, info, false) == GIF_ERROR)
		Error = GifFileIn->Error;
#else
    DDGifSlurp(GifFileIn, info, false);
#endif

    if (GifFileIn->ImageCount < 1) {
        Error = D_GIF_ERR_NO_FRAMES;
    }
    else if (info->rewindFunction(info) != 0) {
        Error = D_GIF_ERR_REWIND_FAILED;
    }
    if (Error != 0) {
        cleanUp(info);
        throwGifIOException(Error, env);
        return NULL;
    }
    jclass gifInfoHandleClass = (*env)->FindClass(env,
            "pl/droidsonroids/gif/GifInfoHandle");
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
    int Error;
    GifFileType *GifFileIn = DGifOpen(file, &fileRead, &Error);
    return createGifHandle(GifFileIn, Error, ftell(file), fileRewind, env, justDecodeMetaData);
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
    int Error;
    GifFileType *GifFileIn = DGifOpen(container, &byteArrayReadFun, &Error);

    jobject gifInfoHandle = createGifHandle(GifFileIn, Error, container->pos, byteArrayRewind, env, justDecodeMetaData);

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
    DirectByteBufferContainer *container = malloc(
            sizeof(DirectByteBufferContainer));
    if (container == NULL) {
        throwGifIOException(D_GIF_ERR_NOT_ENOUGH_MEM, env);
        return NULL;
    }
    container->bytes = bytes;
    container->capacity = capacity;
    container->pos = 0;
    int Error;
    GifFileType *GifFileIn = DGifOpen(container, &directByteBufferReadFun, &Error);

    jobject gifInfoHandle = createGifHandle(GifFileIn, Error, container->pos, directByteBufferRewindFun, env, justDecodeMetaData);
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

    int Error = 0;
    GifFileType *GifFileIn = DGifOpen(container, &streamReadFun, &Error);

    (*env)->CallVoidMethod(env, stream, mid, LONG_MAX); //TODO better length?

    jobject gifInfoHandle = createGifHandle(GifFileIn, Error, 0, streamRewind, env, justDecodeMetaData);
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
    jint fd = (*env)->GetIntField(env, jfd, fdClassDescriptorFieldID);
    FILE *file = fdopen(dup(fd), "rb");
    if (file == NULL || fseek(file, offset, SEEK_SET) != 0) {
        throwGifIOException(D_GIF_ERR_OPEN_FAILED, env);
        return NULL;
    }

    int Error = 0;
    GifFileType *GifFileIn = DGifOpen(file, &fileRead, &Error);

    return createGifHandle(GifFileIn, Error, ftell(file), fileRewind, env, justDecodeMetaData);
}

static void copyLine(argb *dst, const unsigned char *src,
        const ColorMapObject *cmap, int transparent, int width) {
    for (; width > 0; width--, src++, dst++) {
        if (*src != transparent) {
            int colIdx = (*src >= cmap->ColorCount) ? 0 : *src;
            GifColorType *col = &cmap->Colors[colIdx];
            dst->alpha = 0xFF;
            dst->red = col->Red;
            dst->green = col->Green;
            dst->blue = col->Blue;
        }
    }
}

static void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap, int widthOffset) {
    const GifWord width = info->gifFilePtr->SWidth;
    const GifWord height = info->gifFilePtr->SHeight;
    const unsigned char *src = info->rasterBits;
    argb *dst = GET_ADDR(bm, width, frame->ImageDesc.Left, frame->ImageDesc.Top);
    GifWord copyWidth = frame->ImageDesc.Width;
    if (frame->ImageDesc.Left + copyWidth > width) {
        copyWidth = width - frame->ImageDesc.Left;
    }

    GifWord copyHeight = frame->ImageDesc.Height;
    if (frame->ImageDesc.Top + copyHeight > height) {
        copyHeight = height - frame->ImageDesc.Top;
    }

    for (; copyHeight > 0; copyHeight--) {
        copyLine(dst, src, cmap, info->infos[info->currentIndex].transpIndex, copyWidth);
        src += frame->ImageDesc.Width;
        dst += width;
        dst += widthOffset;
    }
}

static void drawFrame(argb *bm, GifInfo *info, SavedImage *frame, int widthOffset) {
    ColorMapObject *cmap = info->gifFilePtr->SColorMap;

    if (frame->ImageDesc.ColorMap != NULL) {
        // use local color table
        cmap = frame->ImageDesc.ColorMap;
        if (cmap->ColorCount != (1 << cmap->BitsPerPixel))
            cmap = defaultCmap;
    }

    blitNormal(bm, info, frame, cmap, widthOffset);
}

// return true if area of 'target' is completely covers area of 'covered'
static bool checkIfCover(const SavedImage *target, const SavedImage *covered) {
    if (target->ImageDesc.Left <= covered->ImageDesc.Left
            && covered->ImageDesc.Left + covered->ImageDesc.Width
            <= target->ImageDesc.Left + target->ImageDesc.Width
            && target->ImageDesc.Top <= covered->ImageDesc.Top
            && covered->ImageDesc.Top + covered->ImageDesc.Height
            <= target->ImageDesc.Top + target->ImageDesc.Height) {
        return true;
    }
    return false;
}

static inline void disposeFrameIfNeeded(argb *bm, GifInfo *info,
        int idx) {
    argb *backup = info->backupPtr;
    GifFileType *fGif = info->gifFilePtr;
    SavedImage *cur = &fGif->SavedImages[idx - 1];
    SavedImage *next = &fGif->SavedImages[idx];
    // We can skip disposal process if next frame is not transparent
    // and completely covers current area
    unsigned char curDisposal = info->infos[idx - 1].disposalMethod;
    bool nextTrans = info->infos[idx].transpIndex != NO_TRANSPARENT_COLOR;
    unsigned char nextDisposal = info->infos[idx].disposalMethod;
    if (nextTrans || !checkIfCover(next, cur)) {
        if (curDisposal == DISPOSE_BACKGROUND) {// restore to background (under this image) color
            uint32_t *dst = (uint32_t *) GET_ADDR(bm, fGif->SWidth, cur->ImageDesc.Left, cur->ImageDesc.Top);
            int copyWidth = cur->ImageDesc.Width;
            if (cur->ImageDesc.Left + copyWidth > fGif->SWidth) {
                copyWidth = fGif->SWidth - cur->ImageDesc.Left;
            }

            int copyHeight = cur->ImageDesc.Height;
            if (cur->ImageDesc.Top + copyHeight > fGif->SHeight) {
                copyHeight = fGif->SHeight - cur->ImageDesc.Top;
            }
            for (; copyHeight > 0; copyHeight--) {
                memset(dst, 0, copyWidth * sizeof(argb));
                dst += fGif->SWidth;
            }
        }
        else if (curDisposal == DISPOSE_PREVIOUS && nextDisposal == DISPOSE_PREVIOUS) {// restore to previous
            argb *tmp = bm;
            bm = backup;
            backup = tmp;
        }
    }

    // Save current image if next frame's disposal method == DISPOSE_PREVIOUS
    if (nextDisposal == DISPOSE_PREVIOUS)
        memcpy(backup, bm, fGif->SWidth * fGif->SHeight * sizeof(argb));
}

static bool reset(GifInfo *info) {
    if (info->rewindFunction(info) != 0)
        return false;
    info->nextStartTime = 0;
    info->currentLoop = 0;
    info->currentIndex = -1;
    info->lastFrameRemainder = ULONG_MAX;
    return true;
}

static void getBitmap(argb *bm, GifInfo *info, int widthOffset) {
    GifFileType *fGIF = info->gifFilePtr;
    if (fGIF->Error == D_GIF_ERR_REWIND_FAILED)
        return;

    if (DDGifSlurp(fGIF, info, true) == GIF_ERROR) {
        if (!reset(info))
            fGIF->Error = D_GIF_ERR_REWIND_FAILED;
        return;
    }
    if (info->currentIndex == 0) {
        int backgroundColor;
        if (info->infos[0].transpIndex != NO_TRANSPARENT_COLOR)
            backgroundColor = fGIF->SBackGroundColor;
        else
            backgroundColor = 0;
        memset(bm, backgroundColor, fGIF->SWidth * fGIF->SHeight * sizeof(argb));
    }
    else {
        disposeFrameIfNeeded(bm, info, info->currentIndex);
    }
    drawFrame(bm, info, &fGIF->SavedImages[info->currentIndex], widthOffset);
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
            getBitmap((argb *) pixels, info, 0);
        }
        unlockPixels(env, jbitmap);
    }
    info->lastFrameRemainder = lastFrameRemainder;

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime() + lastFrameRemainder;
    else
        info->nextStartTime = getRealTime()
                + (time_t) (lastFrameRemainder * info->speedFactor);
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
        getBitmap((argb *) pixels, info, 0);
    }
    unlockPixels(env, jbitmap);

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime()
                + info->infos[info->currentIndex].duration;
    else
        info->nextStartTime = getRealTime()
                + (time_t) (info->infos[info->currentIndex].duration * info->speedFactor);
}

static inline jlong packRenderFrameResult(int invalidationDelay, bool isAnimationCompleted) {
    return (jlong) ((invalidationDelay << 1) | (isAnimationCompleted & 1L));
}

static inline bool renderToBitmap(JNIEnv *env, jobject jbitmap, GifInfo *info) {
    void *pixels = NULL;
    if (!lockPixels(env, jbitmap, &pixels, false)) {
        return false;
    }
    getBitmap((argb *) pixels, info, 0);
    unlockPixels(env, jbitmap);
    return true;
}

static int calculateInvalidationDelay(GifInfo *info, time_t rt) {
    int invalidationDelay;
    if (info->gifFilePtr->ImageCount > 1 && (info->currentLoop < info->loopCount || info->loopCount == 0)) {
        unsigned int scaledDuration = info->infos[info->currentIndex].duration;
        if (info->speedFactor != 1.0) {
            scaledDuration /= info->speedFactor;
            if (scaledDuration <= 0)
                scaledDuration = 1;
            else if (scaledDuration > INT_MAX)
                scaledDuration = INT_MAX;
        }
        info->nextStartTime = rt + scaledDuration;
        invalidationDelay = scaledDuration;
    }
    else
        invalidationDelay = -1;
    return invalidationDelay;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_renderFrame(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jobject jbitmap) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return packRenderFrameResult(-1, false);
    bool needRedraw = false;
    time_t rt = getRealTime();
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
        bool renderingSuccessful = renderToBitmap(env, jbitmap, info);
        if (info->gifFilePtr->Error == D_GIF_ERR_NOT_ENOUGH_MEM) {
            throwException(env, "java/lang/OutOfMemoryError", "Failed to allocate native memory");
            renderingSuccessful = false;
        }
        if (!renderingSuccessful) {
            return packRenderFrameResult(-1, false);
        }
        invalidationDelay = calculateInvalidationDelay(info, rt);
    }
    else {
        long delay = info->nextStartTime - rt;
        if (delay < 0)
            invalidationDelay = -1;
        else //no need to check upper bound since info->nextStartTime<=rt+LONG_MAX always
            invalidationDelay = (int) delay;
    }
    if (invalidationDelay > 0) {//exclude rendering time
        invalidationDelay -= getRealTime() - rt;
        if (invalidationDelay < 0)
            invalidationDelay = 0;
    }
    return packRenderFrameResult(invalidationDelay, isAnimationCompleted);
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

    const size_t bufferSize = info->gifFilePtr->SWidth * info->gifFilePtr->SHeight * sizeof(argb);
    info->surfaceBackupPtr = malloc(bufferSize);
    if (info->surfaceBackupPtr == NULL) {
        throwException(env, "java/lang/OutOfMemoryError", "Cannot allocate surface frame buffer");
        return;
    }
    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    if (ANativeWindow_setBuffersGeometry(window, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION, "Buffers geometry setting failed");
        return;
    }
    struct ANativeWindow_Buffer buffer;
    struct timespec time_to_sleep;
    const int widthOffset = (16 - (info->gifFilePtr->SWidth % 16)) % 16;

    while ((*env)->CallBooleanMethod(env, jCurrentThread, isInterruptedMID) == JNI_FALSE) {
        if (++info->currentIndex >= info->gifFilePtr->ImageCount) {
            info->currentIndex = 0;
        }
        if (ANativeWindow_lock(window, &buffer, NULL) != 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION, "Window lock failed");
            break;
        }
        getBitmap(info->surfaceBackupPtr, info, widthOffset);
        memcpy(buffer.bits, info->surfaceBackupPtr, bufferSize);
        ANativeWindow_unlockAndPost(window);

        const int invalidationDelayMillis = calculateInvalidationDelay(info, 0);
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
        remainder = info->nextStartTime - getRealTime();
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
    info->lastFrameRemainder = info->nextStartTime - getRealTime();
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_restoreRemainder(JNIEnv *__unused env,
        jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL || info->lastFrameRemainder == ULONG_MAX || info->gifFilePtr->ImageCount <= 1)
        return;
    info->nextStartTime = getRealTime() + info->lastFrameRemainder;
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