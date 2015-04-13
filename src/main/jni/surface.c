#include "gif.h"
#include <sys/eventfd.h>
#include <poll.h>

typedef uint64_t POLL_TYPE;
#define POLL_TYPE_SIZE sizeof(POLL_TYPE)

static void *slurp(void *pVoidInfo) {
    GifInfo *info = pVoidInfo;
    while(1){ //TODO exit from loop
    pthread_mutex_lock(info->slurpMutex);
    while (info->slurpHelper == 0)
        pthread_cond_wait(info->slurpCond, info->slurpMutex);
    info->slurpHelper = 0;
    pthread_mutex_unlock(info->slurpMutex);
    DDGifSlurp(info, true);
    pthread_mutex_lock(info->renderMutex);
    info->renderHelper = 1;
    pthread_cond_signal(info->renderCond);
    pthread_mutex_unlock(info->renderMutex);
    }
    return NULL;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass,
                                                    jlong gifInfo, jobject jsurface, jlongArray savedState,
                                                    jboolean isOpaque, jboolean wasOpaque) {

    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;

    if (info->eventFd == -1) {
        info->eventFd = eventfd(0, 0);
        if (info->eventFd == -1) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Could not create eventfd");
            return;
        }
    }

    info->slurpHelper = 0; //TODO handle init results, release resources
    info->slurpCond = malloc(sizeof(pthread_cond_t));
    pthread_cond_init(info->slurpCond, NULL);
    info->slurpMutex = malloc(sizeof(pthread_mutex_t));
    pthread_mutex_init(info->slurpMutex, NULL);
    info->renderHelper = 0;
    info->renderCond = malloc(sizeof(pthread_cond_t));
    pthread_cond_init(info->renderCond, NULL);
    info->renderMutex = malloc(sizeof(pthread_mutex_t));
    pthread_mutex_init(info->renderMutex, NULL);

    pthread_t thread;
    pthread_create(&thread, NULL, slurp, info);

    info->isOpaque = isOpaque;
    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    const int32_t windowFormat = isOpaque == JNI_TRUE ? WINDOW_FORMAT_RGBX_8888 : WINDOW_FORMAT_RGBA_8888;
    if (ANativeWindow_setBuffersGeometry(window, (int32_t) info->gifFilePtr->SWidth,
                                         (int32_t) info->gifFilePtr->SHeight,
                                         windowFormat) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Buffers geometry setting failed");
        return;
    }

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
    if (isOpaque == JNI_FALSE && wasOpaque == JNI_TRUE) {
        if (!reset(info)) {
            ANativeWindow_release(window);
            return;
        }
    }
    else if (info->surfaceBackupPtr) {
        memcpy(buffer.bits, info->surfaceBackupPtr, bufferSize);
        info->lastFrameRemainder = -1;
    }
    else {
        if (savedState != NULL)
            info->lastFrameRemainder = restoreSavedState(info, env, savedState, buffer.bits);
        else
            info->lastFrameRemainder = -1;
    }
    ANativeWindow_unlockAndPost(window);

    ARect rect;
    while (1) {
        time_t renderingStartTime = getRealTime();
        pthread_mutex_lock(info->slurpMutex);
        info->slurpHelper = 1;
        pthread_cond_broadcast(info->slurpCond);
        pthread_mutex_unlock(info->slurpMutex);

        rect.left = (int32_t) info->gifFilePtr->Image.Left;
        rect.right = (int32_t) (info->gifFilePtr->Image.Left + info->gifFilePtr->Image.Width);
        rect.top = (int32_t) info->gifFilePtr->Image.Top;
        rect.bottom = (int32_t) (info->gifFilePtr->Image.Top + info->gifFilePtr->Image.Height);
        oldBufferBits = buffer.bits;
        if (ANativeWindow_lock(window, &buffer, &rect) != 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Window lock failed");
            break;
        }
        if (info->currentIndex > 0) {
            memcpy(buffer.bits, oldBufferBits, bufferSize);
        }
        pthread_mutex_lock(info->renderMutex);
        while (info->renderHelper == 0)
            pthread_cond_wait(info->renderCond, info->renderMutex);
        info->renderHelper = 0;
        pthread_mutex_unlock(info->renderMutex);

        const uint_fast32_t frameDuration = getBitmap(buffer.bits, info);

        ANativeWindow_unlockAndPost(window);

        time_t invalidationDelayMillis = calculateInvalidationDelay(info, renderingStartTime, frameDuration);

        if (info->lastFrameRemainder >= 0) {
            invalidationDelayMillis = info->lastFrameRemainder;
            info->lastFrameRemainder = -1;
        }

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

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_postUnbindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info || info->eventFd == -1) {
        return;
    }
    POLL_TYPE eftd_ctr;
    if (write(info->eventFd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Eventfd write failed");
    }
}