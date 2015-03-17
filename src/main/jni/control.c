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