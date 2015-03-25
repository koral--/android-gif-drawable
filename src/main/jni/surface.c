#include "gif.h"
#include <sys/eventfd.h>
#include <poll.h>

typedef uint64_t POLL_TYPE;
#define POLL_TYPE_SIZE sizeof(POLL_TYPE)

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass,
        jlong gifInfo, jobject jsurface, jint startPosition) {

    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info)
        return 0;

    info->eventFd = eventfd(0, 0);
    if (info->eventFd == -1) {
        throwException(env, ILLEGAL_STATE_EXCEPTION, "Could not create eventfd");
        return 0;
    }

    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    if (ANativeWindow_setBuffersGeometry(window, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION, "Buffers geometry setting failed");
        return 0;
    }

    int framesToSkip = getSkippedFramesCount(info, startPosition);
    LOGE("fts %d cidx: %d", framesToSkip, info->currentIndex);

    struct ANativeWindow_Buffer buffer;
    buffer.bits = NULL;

    struct pollfd eventPollFd;
    eventPollFd.fd = info->eventFd;
    eventPollFd.events = POLL_IN;

    void *oldBufferBits;
//    time_t start= getRealTime();
    while (info->eventFd != -1) {
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

        int invalidationDelayMillis = calculateInvalidationDelay(info, getRealTime(), env);
        if (invalidationDelayMillis < 0) {
            break;
        }
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
            LOGE("pollResult %d", pollResult);
            POLL_TYPE eftd_ctr;
            if (read(eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
                LOGE("read error %d", errno);
                throwException(env, ILLEGAL_STATE_EXCEPTION, "Eventfd read failed");
            }
            break;
        }

    }
    info->eventFd = -1;
    if (close(eventPollFd.fd) == -1) {
        LOGE("close error %d", errno);
        if ((*env)->ExceptionCheck(env) == JNI_FALSE)
            throwException(env, ILLEGAL_STATE_EXCEPTION, "Eventfd closing failed");
    }
    ANativeWindow_release(window);
    LOGE("ended cidx %d", info->currentIndex);
    info->lastFrameRemainder = info->nextStartTime - getRealTime(); //TODO handle case when animation has been stopped, do not allow negative
    return getCurrentPosition(info);
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_interrupt(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info) {
        return 0;
    }
    if (info->eventFd != -1) {
        POLL_TYPE eftd_ctr;
        LOGE("interrupting");
        if (write(info->eventFd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
            LOGE("write error %d", errno);
            if (info->eventFd != -1 || errno != EBADF)
                throwException(env, ILLEGAL_STATE_EXCEPTION, "Eventfd write failed");
        }
        info->eventFd = -1;
    }
    info->lastFrameRemainder = info->nextStartTime - getRealTime(); //TODO handle case when animation has been stopped, do not allow negative
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
