#include "gif.h"

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