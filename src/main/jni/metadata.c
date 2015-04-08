#include "gif.h"

__unused JNIEXPORT jstring JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getComment(JNIEnv *env, jclass __unused handleClass,
                                                   jlong gifInfo) {
    if (gifInfo == 0) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, ((GifInfo *) (intptr_t) gifInfo)->comment);
}

__unused JNIEXPORT jboolean JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_isAnimationCompleted(JNIEnv __unused *env, jclass __unused handleClass,
                                                             jlong gifInfo) {
    if (gifInfo == 0) {
        return JNI_FALSE;
    }
    GifInfo *info = ((GifInfo *) (intptr_t) gifInfo);
    if (info->currentIndex == info->gifFilePtr->ImageCount) //TODO handle better
        return JNI_TRUE;
    else
        return JNI_FALSE;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getLoopCount(JNIEnv __unused *env, jclass __unused handleClass,
                                                     jlong gifInfo) {
    if (gifInfo == 0)
        return 0;
    return ((GifInfo *) (intptr_t) gifInfo)->loopCount;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getDuration(JNIEnv *__unused  env, jclass __unused handleClass,
                                                    jlong gifInfo) {
    if (gifInfo == 0) {
        return 0;
    }
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    int i;
    jint sum = 0;
    for (i = 0; i < info->gifFilePtr->ImageCount; i++)
        sum += info->infos[i].DelayTime;
    return sum;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getSourceLength(JNIEnv __unused *env, jclass __unused handleClass,
                                                        jlong gifInfo) {
    if (gifInfo == 0) {
        return -1;
    }
    return ((GifInfo *) (intptr_t) gifInfo)->sourceLength;
}

jint getCurrentPosition(GifInfo *info) {
    const uint_fast32_t idx = info->currentIndex;
    if (info->gifFilePtr->ImageCount == 1)
        return 0;
    int i;
    unsigned int sum = 0;
    for (i = 0; i < idx; i++)
        sum += info->infos[i].DelayTime;
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

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getCurrentPosition(JNIEnv *__unused env,
                                                           jclass __unused handleClass, jlong gifInfo) {
    if (gifInfo == 0) {
        return 0;
    }
    return getCurrentPosition((GifInfo *) (intptr_t) gifInfo);
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getAllocationByteCount(JNIEnv *__unused  env,
                                                               jclass __unused handleClass, jlong gifInfo) {
    if (gifInfo == 0) {
        return 0;
    }
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    GifWord pxCount = info->gifFilePtr->SWidth + info->gifFilePtr->SHeight;
    size_t sum = pxCount * sizeof(char);
    if (info->backupPtr != NULL)
        sum += pxCount * sizeof(argb);
    return (jlong) sum;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getNativeErrorCode(JNIEnv *__unused  env,
                                                           jclass __unused handleClass, jlong gifInfo) {
    if (gifInfo == 0)
        return 0;
    return ((GifInfo *) (intptr_t) gifInfo)->gifFilePtr->Error;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getCurrentLoop(JNIEnv __unused *env, jclass __unused handleClass,
                                                       jlong gifInfo) {
    if (gifInfo == 0)
        return 0;
    return ((GifInfo *) (intptr_t) gifInfo)->currentLoop;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getCurrentFrameIndex(JNIEnv __unused *env, jclass __unused handleClass,
                                                             jlong gifInfo) {
    if (gifInfo == 0)
        return -1;
    return ((GifInfo *) (intptr_t) gifInfo)->currentIndex;
}
