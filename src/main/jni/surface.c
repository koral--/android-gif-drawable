#include "gif.h"
#include <sys/eventfd.h>
#include <poll.h>

typedef uint64_t POLL_TYPE;
#define POLL_TYPE_SIZE sizeof(POLL_TYPE)

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass,
                                                    jlong gifInfo, jobject jsurface, jint startPosition) {

    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info)
        return;

    if (info->eventFd == -1) {
        info->eventFd = eventfd(0, 0);
        if (info->eventFd == -1) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Could not create eventfd");
            return;
        }
    }
    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    if (ANativeWindow_setBuffersGeometry(window, info->gifFilePtr->SWidth, info->gifFilePtr->SHeight,
                                         WINDOW_FORMAT_RGBA_8888) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Buffers geometry setting failed");
        return;
    }

    int framesToSkip = getSkippedFramesCount(info, startPosition);
    struct ANativeWindow_Buffer buffer = {.bits =NULL};
    void *oldBufferBits;

    struct pollfd eventPollFd = {.fd=info->eventFd, .events = POLL_IN};

    POLL_TYPE eftd_ctr;
    int pollResult;
    while (1) {
        pollResult = poll(&eventPollFd, 1, 0);
        if (pollResult == 0)
            break;
        else if (pollResult > 0) {
            if (read(eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
                throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Read on flushing failed");
                return;
            }
        }
        else {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Poll on flushing failed");
            return;
        }
    }

    if (ANativeWindow_lock(window, &buffer, NULL) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Window lock failed");
        return;
    }
    const size_t bufferSize = buffer.stride * buffer.height * sizeof(argb);

    info->stride = buffer.stride;
    if (info->surfaceBackupPtr) {
        memcpy(buffer.bits, info->surfaceBackupPtr, bufferSize);
    }
    else {
        while (framesToSkip-- > 0) {
            DDGifSlurp(info, true);
            getBitmap(buffer.bits, info);
        }
    }
    ANativeWindow_unlockAndPost(window);

    ARect rect;
    while (1) {
        time_t renderingStartTime = getRealTime();
        DDGifSlurp(info, true);

        rect.left = info->gifFilePtr->Image.Left;
        rect.right = info->gifFilePtr->Image.Left + info->gifFilePtr->Image.Width;
        rect.top = info->gifFilePtr->Image.Top;
        rect.bottom = info->gifFilePtr->Image.Top + info->gifFilePtr->Image.Height;
        oldBufferBits = buffer.bits;
        if (ANativeWindow_lock(window, &buffer, &rect) != 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Window lock failed");
            break;
        }
        if (info->currentIndex > 0) {
            if (rect.left == 0 && rect.right == buffer.width && info->gifFilePtr->Image.Height < info->gifFilePtr->SHeight) {
                memcpy(buffer.bits, oldBufferBits, buffer.stride * rect.top * sizeof(argb));
                size_t offset = buffer.stride * rect.bottom * sizeof(argb);
                memcpy(buffer.bits + offset, oldBufferBits + offset, bufferSize - offset);
            }
            else
                memcpy(buffer.bits, oldBufferBits, bufferSize);
        }
        const uint_fast16_t frameDuration = getBitmap(buffer.bits, info);
        ANativeWindow_unlockAndPost(window);

        time_t invalidationDelayMillis = calculateInvalidationDelay(info, renderingStartTime, frameDuration);

//        if (info->lastFrameRemainder > 0) { //TODO switch
//            invalidationDelayMillis = info->lastFrameRemainder;
//            info->lastFrameRemainder = 0;
//        }
        pollResult = poll(&eventPollFd, 1, (int) invalidationDelayMillis);
        if (pollResult < 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Poll failed");
            break;
        }
        else if (pollResult > 0) {
            if (info->surfaceBackupPtr == NULL) {
                info->surfaceBackupPtr = malloc(bufferSize);
                if (info->surfaceBackupPtr == NULL) {
                    throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
                    break;
                }
            }
            memcpy(info->surfaceBackupPtr, buffer.bits, bufferSize);
            if (read(eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
                throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Eventfd read failed");
            }
            break;
        }
    }
    ANativeWindow_release(window);
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_postUnbindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info || info->eventFd == -1) {
        return 0;
    }
    POLL_TYPE eftd_ctr;
    if (write(info->eventFd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Eventfd write failed");
    }
    return 0;
//    info->lastFrameRemainder = info->nextStartTime - getRealTime();
//    if (info->lastFrameRemainder < 0) //TODO switch
//        info->lastFrameRemainder = 0;
//    return getCurrentPosition(info);
}

static int getSkippedFramesCount(GifInfo *info, jint desiredPos) {
    if (info->gifFilePtr->ImageCount == 1)
        return 0;

    unsigned long sum = 0;
    int i;
    for (i = 0; i < info->gifFilePtr->ImageCount; i++) {
        unsigned long newSum = sum + info->infos[i].DelayTime;
        if (newSum >= desiredPos)
            break;
        sum = newSum;
    }

//    time_t lastFrameRemainder = desiredPos - sum; TODO switch
//    if (i == info->gifFilePtr->ImageCount - 1 && lastFrameRemainder > info->infos[i].DelayTime)
//        lastFrameRemainder = info->infos[i].DelayTime;
//
//    info->lastFrameRemainder = lastFrameRemainder;
//
//    if (info->speedFactor == 1.0)
//        info->nextStartTime = getRealTime() + lastFrameRemainder;
//    else
//        info->nextStartTime = getRealTime() + (time_t) (lastFrameRemainder * info->speedFactor);
    return i;
}
