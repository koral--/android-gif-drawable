#include "gif.h"
#include <sys/eventfd.h>

typedef uint64_t POLL_TYPE;
#define POLL_TYPE_SIZE sizeof(POLL_TYPE)

#define THROW_ON_NONZERO_RESULT(fun, message) if (fun !=0) throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, message)
#define THROW_AND_BREAK_ON_NONZERO_RESULT(fun, message) if (fun !=0) {throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, message); break;}
#define RETURN_ERRNO_ON_NONZERO_RESULT(fun) if (fun !=0) return (void *) errno;

static void *slurp(void *pVoidInfo) {
    GifInfo *info = pVoidInfo;
    while (1) {
        RETURN_ERRNO_ON_NONZERO_RESULT(pthread_mutex_lock(&info->surfaceDescriptor->slurpMutex));
        while (info->surfaceDescriptor->slurpHelper == 0)
            RETURN_ERRNO_ON_NONZERO_RESULT(
                    pthread_cond_wait(&info->surfaceDescriptor->slurpCond, &info->surfaceDescriptor->slurpMutex));
        if (info->surfaceDescriptor->slurpHelper == 2) {
            RETURN_ERRNO_ON_NONZERO_RESULT(pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex));
            return NULL;
        }
        info->surfaceDescriptor->slurpHelper = 0;
        RETURN_ERRNO_ON_NONZERO_RESULT(pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex));
        DDGifSlurp(info, true);
        RETURN_ERRNO_ON_NONZERO_RESULT(pthread_mutex_lock(&info->surfaceDescriptor->renderMutex));
        info->surfaceDescriptor->renderHelper = 1;
        RETURN_ERRNO_ON_NONZERO_RESULT(pthread_cond_signal(&info->surfaceDescriptor->renderCond));
        RETURN_ERRNO_ON_NONZERO_RESULT(pthread_mutex_unlock(&info->surfaceDescriptor->renderMutex));
    }
    return NULL;
}

static inline bool initSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *env) {
    surfaceDescriptor->eventPollFd.events = POLL_IN;
    surfaceDescriptor->eventPollFd.fd = eventfd(0, 0);
    if (surfaceDescriptor->eventPollFd.fd == -1) {
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Could not create eventfd");
        return false;
    }
    const pthread_cond_t condInitializer = PTHREAD_COND_INITIALIZER;
    surfaceDescriptor->slurpCond = condInitializer;
    surfaceDescriptor->renderCond = condInitializer;
    const pthread_mutex_t mutexInitializer = PTHREAD_MUTEX_INITIALIZER;
    surfaceDescriptor->slurpMutex = mutexInitializer;
    surfaceDescriptor->renderMutex = mutexInitializer;

    surfaceDescriptor->surfaceBackupPtr = NULL;
    return true;
}

void releaseSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *env) {
    if (surfaceDescriptor == NULL)
        return;
    free(surfaceDescriptor->surfaceBackupPtr);
    surfaceDescriptor->surfaceBackupPtr = NULL;
    THROW_ON_NONZERO_RESULT(close(surfaceDescriptor->eventPollFd.fd), "eventfd close failed");
    THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->slurpMutex), "slurp mutex destroy failed");
    THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->renderMutex), "render mutex destroy failed");
    THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->slurpCond), "slurp cond destroy failed");
    THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->renderCond), "render cond  destroy failed");
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass,
                                                    jlong gifInfo, jobject jsurface, jlongArray savedState,
                                                    jboolean isOpaque, jboolean wasOpaque) {

    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info->surfaceDescriptor == NULL) {
        info->surfaceDescriptor = malloc(sizeof(SurfaceDescriptor));
        if (!initSurfaceDescriptor(info->surfaceDescriptor, env)) {
            free(info->surfaceDescriptor);
            info->surfaceDescriptor = NULL;
            return;
        }
    }
    info->surfaceDescriptor->renderHelper = 0;
    info->surfaceDescriptor->slurpHelper = 0;

    const int32_t windowFormat = isOpaque == JNI_TRUE ? WINDOW_FORMAT_RGBX_8888 : WINDOW_FORMAT_RGBA_8888;
    info->isOpaque = isOpaque;

    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    if (ANativeWindow_setBuffersGeometry(window, (int32_t) info->gifFilePtr->SWidth,
                                         (int32_t) info->gifFilePtr->SHeight,
                                         windowFormat) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Buffers geometry setting failed");
        return;
    }

    pthread_t thread;
    if (pthread_create(&thread, NULL, slurp, info) != 0) {
        ANativeWindow_release(window);
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "pthread_create failed");
    }

    struct ANativeWindow_Buffer buffer = {.bits =NULL};
    void *oldBufferBits;
    POLL_TYPE eftd_ctr;
    int pollResult;

    while (1) {
        pollResult = poll(&info->surfaceDescriptor->eventPollFd, 1, 0);
        if (pollResult == 0)
            break;
        else if (pollResult > 0) {
            if (read(info->surfaceDescriptor->eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
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
    else if (info->surfaceDescriptor->surfaceBackupPtr) {
        memcpy(buffer.bits, info->surfaceDescriptor->surfaceBackupPtr, bufferSize);
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

        THROW_AND_BREAK_ON_NONZERO_RESULT(pthread_mutex_lock(&info->surfaceDescriptor->slurpMutex),
                                          "slurp mutex_lock on render start failed")
        info->surfaceDescriptor->slurpHelper = 1;
        THROW_AND_BREAK_ON_NONZERO_RESULT(pthread_cond_signal(&info->surfaceDescriptor->slurpCond),
                                          "slurp cond_signal on render start failed")
        THROW_AND_BREAK_ON_NONZERO_RESULT(pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex),
                                          "slurp mutex_unlock on render start failed")

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

        if (pthread_mutex_lock(&info->surfaceDescriptor->renderMutex) != 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "render mutex_lock after cpy failed");
            ANativeWindow_unlockAndPost(window);
            break;
        }
        while (info->surfaceDescriptor->renderHelper == 0) {
            if (pthread_cond_wait(&info->surfaceDescriptor->renderCond, &info->surfaceDescriptor->renderMutex) != 0) {
                throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "render cond_wait after cpy failed");
                ANativeWindow_unlockAndPost(window);
                break;
            }
        }
        info->surfaceDescriptor->renderHelper = 0;
        if (pthread_mutex_unlock(&info->surfaceDescriptor->renderMutex) != 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "render mutex_unlock after cpy failed");
            ANativeWindow_unlockAndPost(window);
            break;
        }

        const uint_fast32_t frameDuration = getBitmap(buffer.bits, info);

        ANativeWindow_unlockAndPost(window);

        time_t invalidationDelayMillis = calculateInvalidationDelay(info, renderingStartTime, frameDuration);

        if (info->lastFrameRemainder >= 0) {
            invalidationDelayMillis = info->lastFrameRemainder;
            info->lastFrameRemainder = -1;
        }

        pollResult = poll(&info->surfaceDescriptor->eventPollFd, 1, (int) invalidationDelayMillis);
        if (pollResult < 0) {
            throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Poll failed");
            break;
        }
        else if (pollResult > 0) {
            if (info->surfaceDescriptor->surfaceBackupPtr == NULL) {
                info->surfaceDescriptor->surfaceBackupPtr = malloc(bufferSize);
                if (info->surfaceDescriptor->surfaceBackupPtr == NULL) {
                    throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
                    break;
                }
            }
            memcpy(info->surfaceDescriptor->surfaceBackupPtr, buffer.bits, bufferSize);
            if (read(info->surfaceDescriptor->eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
                throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Eventfd read failed");
            }
            break;
        }
    }

    ANativeWindow_release(window);
    THROW_ON_NONZERO_RESULT(pthread_mutex_lock(&info->surfaceDescriptor->slurpMutex), "mutex_lock on exit failed");
    info->surfaceDescriptor->slurpHelper = 2;
    THROW_ON_NONZERO_RESULT(pthread_cond_signal(&info->surfaceDescriptor->slurpCond), "cond_signal on exit failed");
    THROW_ON_NONZERO_RESULT(pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex), "mutex_unlock on exit failed");
    void *slurpResult = NULL;
    THROW_ON_NONZERO_RESULT(pthread_join(thread, &slurpResult), "join failed");
    if (slurpResult != NULL) {
        errno = (int) slurpResult;
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Slurp thread finished with error");
    }
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_postUnbindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (!info || info->surfaceDescriptor == NULL) {
        return;
    }
    POLL_TYPE eftd_ctr;
    if (write(info->surfaceDescriptor->eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
        throwException(env, ILLEGAL_STATE_EXCEPTION_ERRNO, "Eventfd write failed");
    }
}