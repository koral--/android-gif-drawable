#include "gif.h"
#include <android/bitmap.h>

bool lockPixels(JNIEnv *env, jobject jbitmap, GifInfo *info, void **pixels) {
    AndroidBitmapInfo bitmapInfo;
    if (AndroidBitmap_getInfo(env, jbitmap, &bitmapInfo) == ANDROID_BITMAP_RESULT_SUCCESS)
        info->stride = bitmapInfo.width;
    else {
        throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, "Could not get bitmap info");
        return false;
    }

    int i;
    int lockPixelsResult = ANDROID_BITMAP_RESULT_SUCCESS;
    for (i = 0; i < 20; i++) { //#122 workaround
        usleep(100);
        lockPixelsResult = AndroidBitmap_lockPixels(env, jbitmap, pixels);
        if (lockPixelsResult == ANDROID_BITMAP_RESULT_SUCCESS) {
            return true;
        }
    }
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
    throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, message);
    return false;
}

void unlockPixels(JNIEnv *env, jobject jbitmap) {
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
    throwException(env, ILLEGAL_STATE_EXCEPTION_BARE, message);
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_renderFrame(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jobject jbitmap) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL)
        return PACK_RENDER_FRAME_RESULT(-1, false);
    bool needRedraw = false;
    time_t rt = getRealTime();
    bool isAnimationCompleted;
    if (rt >= info->nextStartTime) {
        if (++info->currentIndex >= info->gifFilePtr->ImageCount)
            info->currentIndex = 0;
        needRedraw = true;
        isAnimationCompleted = info->currentIndex >= info->gifFilePtr->ImageCount - 1 && info->currentLoop >= info->loopCount;
    }
    else
        isAnimationCompleted = false;

    time_t invalidationDelay;
    if (needRedraw) {
        void *pixels;
        if (!lockPixels(env, jbitmap, info, &pixels)) {
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
    return PACK_RENDER_FRAME_RESULT(invalidationDelay, isAnimationCompleted);
}