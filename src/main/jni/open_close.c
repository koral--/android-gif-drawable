#include "gif.h"

void cleanUp(GifInfo *info) {
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
    if (descriptor->GifFileIn->SColorMap != NULL && descriptor->GifFileIn->SColorMap->ColorCount != (1 << descriptor->GifFileIn->SColorMap->BitsPerPixel)) {
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
    info->eventFd = -1;
    return (*env)->NewObject(env, gifInfoHandleClass, gifInfoHandleCtorMID,
            (jlong) (intptr_t) info, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight,
            info->gifFilePtr->ImageCount);
}