#include "gif.h"

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jobject jsurface, jint startPosition) {

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

    int framesToSkip = getSkippedFramesCount(info, startPosition);
    struct ANativeWindow_Buffer buffer;
    buffer.bits = NULL;
    struct timespec time_to_sleep;

    void *oldBufferBits;
//    time_t start= getRealTime();
    while ((*env)->CallBooleanMethod(env, jCurrentThread, isInterruptedMID) == JNI_FALSE) {
        if (++info->currentIndex >= info->gifFilePtr->ImageCount) {
            info->currentIndex = 0;
//            time_t end= getRealTime();
//            LOGE("fps %ld %ld", 1000*info->gifFilePtr->ImageCount/(end-start));
//            start = end;
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
        if (framesToSkip > 0) {
            while (--framesToSkip >= 0) {
                getBitmap(buffer.bits, info);
                info->currentIndex++;
            }
        }
        getBitmap(buffer.bits, info);
        ANativeWindow_unlockAndPost(window);

        time_t invalidationDelayMillis = calculateInvalidationDelay(info, getRealTime(), env);
        if (invalidationDelayMillis < 0) {
            break;
        }
        if (info->lastFrameRemainder > 0) {
            invalidationDelayMillis = info->lastFrameRemainder;
            info->lastFrameRemainder = 0;
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

static int getSkippedFramesCount(GifInfo *info, jint desiredPos) {
    const int imgCount = info->gifFilePtr->ImageCount;
    if (imgCount <= 1)
        return 0;

    unsigned long sum = 0;
    int i;
    for (i = 0; i < imgCount; i++) {
        unsigned long newSum = sum + info->infos[i].duration;
        if (newSum >= desiredPos)
            break;
        sum = newSum;
    }

    time_t lastFrameRemainder = desiredPos - sum;
    if (i == imgCount - 1 && lastFrameRemainder > info->infos[i].duration)
        lastFrameRemainder = info->infos[i].duration;

    info->lastFrameRemainder = lastFrameRemainder;

    if (info->speedFactor == 1.0)
        info->nextStartTime = getRealTime() + lastFrameRemainder;
    else
        info->nextStartTime = getRealTime() + (time_t) (lastFrameRemainder * info->speedFactor);
    return i;
}
