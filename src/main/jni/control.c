#include "gif.h"

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
    int desiredIndex;
    for (desiredIndex = 0; desiredIndex < imgCount; desiredIndex++) {
        unsigned long newSum = sum + info->infos[desiredIndex].duration;
        if (newSum >= desiredPos)
            break;
        sum = newSum;
    }

    if (desiredIndex < info->currentIndex) {
        if (!reset(info)) {
            info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
            return;
        }
    }

    info->lastFrameRemainder = desiredPos - sum;
    if (desiredIndex == imgCount - 1 && info->lastFrameRemainder > info->infos[desiredIndex].duration)
        info->lastFrameRemainder = info->infos[desiredIndex].duration;
    if (desiredIndex > info->currentIndex) {
        void *pixels;
        if (lockPixels(env, jbitmap, info, &pixels) != 0) {
            return;
        }
        while (info->currentIndex < desiredIndex) {
            info->currentIndex++;
            getBitmap((argb *) pixels, info);
        }
        unlockPixels(env, jbitmap);
    }

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime() + info->lastFrameRemainder;
    else
        info->nextStartTime = getRealTime() + (time_t) (info->lastFrameRemainder * info->speedFactor);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToFrame(JNIEnv *env, jclass __unused handleClass,
                                                    jlong gifInfo, jint desiredIndex, jobject jbitmap) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return;

    const int imgCount = info->gifFilePtr->ImageCount;
    if (imgCount <= 1)
        return;
    if (desiredIndex <= info->currentIndex) {
        if (!reset(info)) {
            info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
            return;
        }
    }

    info->lastFrameRemainder = 0;
    if (desiredIndex >= imgCount)
        desiredIndex = imgCount - 1;

    void *pixels;
    if (lockPixels(env, jbitmap, info, &pixels) != 0) {
        return;
    }
    while (info->currentIndex < desiredIndex) {
        info->currentIndex++;
        getBitmap((argb *) pixels, info);
    }
    unlockPixels(env, jbitmap);

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime() + info->infos[info->currentIndex].duration;
    else
        info->nextStartTime = getRealTime() + (time_t) (info->infos[info->currentIndex].duration * info->speedFactor);
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