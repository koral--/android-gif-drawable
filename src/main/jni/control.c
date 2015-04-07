#include "gif.h"

bool reset(GifInfo *info) {
    if (info->rewindFunction(info) != 0)
        return false;
    info->nextStartTime = 0;
    info->currentLoop = 0;
    info->currentIndex = 0;
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
    if (info->gifFilePtr->ImageCount == 1)
        return;

    unsigned long sum = 0;
    int desiredIndex;
    for (desiredIndex = 0; desiredIndex < info->gifFilePtr->ImageCount; desiredIndex++) {
        unsigned long newSum = sum + info->infos[desiredIndex].DelayTime;
        if (newSum >= desiredPos)
            break;
        sum = newSum;
    }

    if (desiredIndex < info->currentIndex && !reset(info)) {
        info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
        return;
    }

    info->lastFrameRemainder = desiredPos - sum;
    if (desiredIndex == info->gifFilePtr->ImageCount - 1 && info->lastFrameRemainder > info->infos[desiredIndex].DelayTime)
        info->lastFrameRemainder = info->infos[desiredIndex].DelayTime;
    if (info->currentIndex < desiredIndex) {
        void *pixels;
        if (lockPixels(env, jbitmap, info, &pixels) != 0) {
            return;
        }
        while (info->currentIndex < desiredIndex) {
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
    if (info == NULL || info->gifFilePtr->ImageCount == 1)
        return;

    if (desiredIndex < info->currentIndex && !reset(info)) {
        info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
        return;
    }

    info->lastFrameRemainder = 0;
    if (desiredIndex >= info->gifFilePtr->ImageCount)
        desiredIndex = info->gifFilePtr->ImageCount - 1;

    uint_fast16_t lastFrameDuration = info->infos[info->currentIndex].DelayTime;
    if (info->currentIndex < desiredIndex) {
        void *pixels;
        if (lockPixels(env, jbitmap, info, &pixels) != 0) {
            return;
        }
        while (info->currentIndex < desiredIndex) {
            lastFrameDuration = getBitmap((argb *) pixels, info);
        }
        unlockPixels(env, jbitmap);
    }

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime() + lastFrameDuration;
    else
        info->nextStartTime = getRealTime() + (time_t) (lastFrameDuration * info->speedFactor);
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
    if (info == NULL || info->lastFrameRemainder == ULONG_MAX || info->gifFilePtr->ImageCount == 1)
        return;
    info->nextStartTime = getRealTime() + info->lastFrameRemainder;
    info->lastFrameRemainder = ULONG_MAX;
}