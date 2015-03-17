#include "gif.h"
#include <android/bitmap.h>

bool lockPixels(JNIEnv *env, jobject jbitmap, void **pixels, bool throwOnError) {
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
    throwException(env, ILLEGAL_STATE_EXCEPTION, message);
}