#include "gif.h"
#include <sys/eventfd.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

typedef uint64_t POLL_TYPE;
#define POLL_TYPE_SIZE sizeof(POLL_TYPE)

#define THROW_AND_BREAK_ON_NONZERO_RESULT(fun, message) if (fun !=0) {throwException(env, RUNTIME_EXCEPTION_ERRNO, message); break;}

static void *slurp(void *pVoidInfo) {
    GifInfo *info = pVoidInfo;
    while (1) {
        pthread_mutex_lock(&info->surfaceDescriptor->slurpMutex);
        while (info->surfaceDescriptor->slurpHelper == 0)
            pthread_cond_wait(&info->surfaceDescriptor->slurpCond, &info->surfaceDescriptor->slurpMutex);

        if (info->surfaceDescriptor->slurpHelper == 2) {
            pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex);
            break;
        }
        info->surfaceDescriptor->slurpHelper = 0;
        pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex);
        DDGifSlurp(info, true);
        pthread_mutex_lock(&info->surfaceDescriptor->renderMutex);
        info->surfaceDescriptor->renderHelper = 1;
        pthread_cond_signal(&info->surfaceDescriptor->renderCond);
        pthread_mutex_unlock(&info->surfaceDescriptor->renderMutex);
    }

    DetachCurrentThread();
    return NULL;
}

static inline bool initSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *env) {
    surfaceDescriptor->eventPollFd.events = POLL_IN;
    surfaceDescriptor->eventPollFd.fd = eventfd(0, 0);
    if (surfaceDescriptor->eventPollFd.fd == -1) {
        throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not create eventfd ");
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

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo,
                                                    jobject jsurface, jlongArray savedState, jboolean isOpaque) {

    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info->surfaceDescriptor == NULL) {
        info->surfaceDescriptor = malloc(sizeof(SurfaceDescriptor));
        if (!initSurfaceDescriptor(info->surfaceDescriptor, env)) {
            free(info->surfaceDescriptor);
            info->surfaceDescriptor = NULL;
            return;
        }
    }

    POLL_TYPE eftd_ctr;
    int pollResult;

    while (1) {
        pollResult = poll(&info->surfaceDescriptor->eventPollFd, 1, 0);
        if (pollResult == 0)
            break;
        else if (pollResult > 0) {
            if (read(info->surfaceDescriptor->eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
                throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not read from eventfd ");
                return;
            }
        }
        else {
            throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not poll on eventfd ");
            return;
        }
    }

    const int32_t windowFormat = isOpaque == JNI_TRUE ? WINDOW_FORMAT_RGBX_8888 : WINDOW_FORMAT_RGBA_8888;
    info->isOpaque = isOpaque;

    struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    if (ANativeWindow_setBuffersGeometry(window, (int32_t) info->gifFilePtr->SWidth,
                                         (int32_t) info->gifFilePtr->SHeight,
                                         windowFormat) != 0) {
        ANativeWindow_release(window);
        throwException(env, RUNTIME_EXCEPTION_ERRNO, "Buffers geometry setting failed ");
        return;
    }

    struct ANativeWindow_Buffer buffer = {.bits =NULL};
    void *oldBufferBits;

    if (ANativeWindow_lock(window, &buffer, NULL) != 0) {
        ANativeWindow_release(window);
        throwException(env, RUNTIME_EXCEPTION_ERRNO, "Window lock failed ");
        return;
    }
    const size_t bufferSize = buffer.stride * buffer.height * sizeof(argb);

    info->stride = buffer.stride;
    long invalidationDelayMillis;
    if (info->surfaceDescriptor->surfaceBackupPtr) {
        memcpy(buffer.bits, info->surfaceDescriptor->surfaceBackupPtr, bufferSize);
        invalidationDelayMillis = 0;
        info->surfaceDescriptor->renderHelper = 1;
        info->surfaceDescriptor->slurpHelper = 0;
    }
    else {
        if (savedState != NULL){
            invalidationDelayMillis = restoreSavedState(info, env, savedState, buffer.bits);
            if (invalidationDelayMillis <0)
                invalidationDelayMillis =0;
        }
        else
            invalidationDelayMillis = 0;
        info->surfaceDescriptor->renderHelper = 0;
        info->surfaceDescriptor->slurpHelper = 1;
    }

    info->lastFrameRemainder = -1;
    ANativeWindow_unlockAndPost(window);

    if (info->loopCount != 0 && info->currentLoop == info->loopCount) {
        ANativeWindow_release(window);
        pollResult = poll(&info->surfaceDescriptor->eventPollFd, 1, -1);
        if (pollResult < 0) {
            throwException(env, RUNTIME_EXCEPTION_ERRNO, "Animation end poll failed ");
        }
        return;
    }

    pthread_t thread;
    if (pthread_create(&thread, NULL, slurp, info) != 0) {
        ANativeWindow_release(window);
        throwException(env, RUNTIME_EXCEPTION_ERRNO, "Slurp thread creation failed ");
        return;
    }

    while (1) {
        pollResult = poll(&info->surfaceDescriptor->eventPollFd, 1, (int) invalidationDelayMillis);
        long renderingStartTime = getRealTime();

        if (pollResult < 0) {
            throwException(env, RUNTIME_EXCEPTION_ERRNO, "Display loop poll failed ");
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
            break;
        }
        oldBufferBits = buffer.bits;
        THROW_AND_BREAK_ON_NONZERO_RESULT(ANativeWindow_lock(window, &buffer, NULL), "Window lock failed ");

        if (info->currentIndex == 0)
            prepareCanvas(buffer.bits, info);
        else
            memcpy(buffer.bits, oldBufferBits, bufferSize);

        pthread_mutex_lock(&info->surfaceDescriptor->renderMutex);
        while (info->surfaceDescriptor->renderHelper == 0) {
            pthread_cond_wait(&info->surfaceDescriptor->renderCond, &info->surfaceDescriptor->renderMutex);
        }
        info->surfaceDescriptor->renderHelper = 0;
        pthread_mutex_unlock(&info->surfaceDescriptor->renderMutex);

        const uint_fast32_t frameDuration = getBitmap(buffer.bits, info);

        pthread_mutex_lock(&info->surfaceDescriptor->slurpMutex);
        info->surfaceDescriptor->slurpHelper = 1;
        pthread_cond_signal(&info->surfaceDescriptor->slurpCond);
        pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex);

        ANativeWindow_unlockAndPost(window);

        invalidationDelayMillis = calculateInvalidationDelay(info, renderingStartTime, frameDuration);

        if (info->lastFrameRemainder >= 0) {
            invalidationDelayMillis = info->lastFrameRemainder;
            info->lastFrameRemainder = -1;
        }
    }

    ANativeWindow_release(window);
    pthread_mutex_lock(&info->surfaceDescriptor->slurpMutex);
    info->surfaceDescriptor->slurpHelper = 2;
    pthread_cond_signal(&info->surfaceDescriptor->slurpCond);
    pthread_mutex_unlock(&info->surfaceDescriptor->slurpMutex);
    THROW_ON_NONZERO_RESULT(pthread_join(thread, NULL), "Slurp thread join failed ");
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_postUnbindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
    GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
    if (info == NULL || info->surfaceDescriptor == NULL) {
        return;
    }
    POLL_TYPE eftd_ctr;
    if (write(info->surfaceDescriptor->eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE) != POLL_TYPE_SIZE) {
        throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not write to eventfd ");
    }
}
