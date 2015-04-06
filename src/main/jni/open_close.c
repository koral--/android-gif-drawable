#include "gif.h"

void cleanUp(GifInfo *info) {
    if (info->eventFd != -1)
        close(info->eventFd);
    info->eventFd = -1;
    free(info->surfaceBackupPtr);
    info->surfaceBackupPtr = NULL;
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

jobject createGifHandle(GifSourceDescriptor *descriptor, JNIEnv *env, jboolean justDecodeMetaData) {
    if (descriptor->startPos < 0) {
        descriptor->Error = D_GIF_ERR_NOT_READABLE;
        DGifCloseFile(descriptor->GifFileIn);
    }
    if (descriptor->Error != 0 || descriptor->GifFileIn == NULL) {
        throwGifIOException(descriptor->Error, env);
        return NULL;
    }
    uint_fast32_t wxh = descriptor->GifFileIn->SWidth * descriptor->GifFileIn->SHeight;
    if (wxh < 1 || wxh > INT_MAX) {
        DGifCloseFile(descriptor->GifFileIn);
        throwGifIOException(D_GIF_ERR_INVALID_SCR_DIMS, env);
        return NULL;
    }
    GifInfo *info = malloc(sizeof(GifInfo));
    if (info == NULL) {
        DGifCloseFile(descriptor->GifFileIn);
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
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
    info->sourceLength = descriptor->sourceLength;
    if (justDecodeMetaData == JNI_TRUE)
        info->rasterBits = NULL;
    else
        info->rasterBits = malloc(
                descriptor->GifFileIn->SHeight * descriptor->GifFileIn->SWidth * sizeof(GifPixelType));
    info->infos = NULL;
    info->backupPtr = NULL;
    info->rewindFunction = descriptor->rewindFunc;
    info->eventFd = -1;
    info->surfaceBackupPtr = NULL;

    if ((info->rasterBits == NULL && justDecodeMetaData != JNI_TRUE)) {
        cleanUp(info);
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL;
    }

    DDGifSlurp(descriptor->GifFileIn, info, false);
    if (descriptor->GifFileIn->Error == D_GIF_ERR_NOT_ENOUGH_MEM) {
        cleanUp(info);
        throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
        return NULL;
    }
#if defined(STRICT_FORMAT_89A)
        descriptor->Error = descriptor->GifFileIn->Error;
#endif

    if (descriptor->GifFileIn->ImageCount < 1) {
        descriptor->Error = D_GIF_ERR_NO_FRAMES;
    }
    else if (descriptor->GifFileIn->Error == D_GIF_ERR_REWIND_FAILED) {
        descriptor->Error = D_GIF_ERR_REWIND_FAILED;
    }
    if (descriptor->Error != 0) {
        cleanUp(info);
        throwGifIOException(descriptor->Error, env);
        return NULL;
    }
    jclass gifInfoHandleClass = (*env)->FindClass(env, "pl/droidsonroids/gif/GifInfoHandle");
    if (gifInfoHandleClass == NULL) {
        cleanUp(info);
        return NULL;
    }
    jmethodID gifInfoHandleCtorMID = (*env)->GetMethodID(env, gifInfoHandleClass, "<init>", "(JIII)V");
    if (gifInfoHandleCtorMID == NULL) {
        cleanUp(info);
        return NULL;
    }
    return (*env)->NewObject(env, gifInfoHandleClass, gifInfoHandleCtorMID,
                             (jlong) (intptr_t) info, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight,
                             info->gifFilePtr->ImageCount);
}