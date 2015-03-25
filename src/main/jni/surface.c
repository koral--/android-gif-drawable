#include "gif.h"
#include <sys/eventfd.h>
#include <poll.h>

typedef uint64_t POLL_TYPE;
#define POLL_TYPE_SIZE sizeof(POLL_TYPE)

__unused JNIEXPORT jboolean JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jobject jsurface, jint startPosition) {

    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info)
        return JNI_FALSE;

    info->eventFd = eventfd(0, 0);
    if (info->eventFd == -1) {
        throwException(env, ILLEGAL_STATE_EXCEPTION, "Could not create eventfd");
        return JNI_FALSE;
    }

    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    if (ANativeWindow_setBuffersGeometry(window, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION, "Buffers geometry setting failed");
        return JNI_FALSE;
    }

    int framesToSkip = getSkippedFramesCount(info, startPosition);
    struct ANativeWindow_Buffer buffer;
    buffer.bits = NULL;
    void *oldBufferBits;
    jboolean result = JNI_FALSE;

    struct pollfd eventPollFd;
    eventPollFd.fd = info->eventFd;
    eventPollFd.events = POLL_IN;

#ifdef DEBUG
    time_t start= getRealTime();
#endif

    while (info->eventFd != -1) {
        if (++info->currentIndex >= info->gifFilePtr->ImageCount) {
            info->currentIndex = 0;
#ifdef DEBUG
            time_t end= getRealTime();
            //LOGE("fps %ld", 1000*info->gifFilePtr->ImageCount/(end-start));
            start = end;
#endif
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

        int invalidationDelayMillis = calculateInvalidationDelay(info, getRealTime(), env);
        if (invalidationDelayMillis < 0) {
            result = JNI_TRUE;
            break;
        }
        else
            result = JNI_FALSE;

        if (info->lastFrameRemainder > 0) {
            invalidationDelayMillis = info->lastFrameRemainder;
            info->lastFrameRemainder = 0;
        }

        const int pollResult = poll(&eventPollFd, 1, invalidationDelayMillis);
        if (pollResult < 0) { //error
            throwException(env, ILLEGAL_STATE_EXCEPTION, "Poll failed");
            break;
        }
        else if (pollResult > 0) {
            POLL_TYPE eftd_ctr;
            if (read(eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
                throwException(env, ILLEGAL_STATE_EXCEPTION, "Eventfd read failed");
            }
            break;
        }
    }
    info->eventFd = -1;
    if (close(eventPollFd.fd) == -1) {
        if ((*env)->ExceptionCheck(env) == JNI_FALSE)
            throwException(env, ILLEGAL_STATE_EXCEPTION, "Eventfd closing failed");
        result = JNI_FALSE;
    }
    ANativeWindow_release(window);
    return result;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_postUnbindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info) {
        return 0;
    }
    if (info->eventFd != -1) {
        POLL_TYPE eftd_ctr;
        if (write(info->eventFd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
            if (info->eventFd != -1 || errno != EBADF)
                throwException(env, ILLEGAL_STATE_EXCEPTION, "Eventfd write failed");
        }
        info->eventFd = -1;
        info->lastFrameRemainder = info->nextStartTime - getRealTime();
        if (info->lastFrameRemainder < 0)
            info->lastFrameRemainder = 0;
    }
    return getCurrentPosition(info);
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
