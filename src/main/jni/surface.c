#include "gif.h"
#include <sys/eventfd.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

static void *slurp(void *pVoidInfo) {
	GifInfo *info = pVoidInfo;
	SurfaceDescriptor *surfaceDescriptor = info->frameBufferDescriptor;
	while (1) {
		pthread_mutex_lock(&surfaceDescriptor->slurpMutex);
		while (surfaceDescriptor->slurpHelper == 0)
			pthread_cond_wait(&surfaceDescriptor->slurpCond, &surfaceDescriptor->slurpMutex);

		if (surfaceDescriptor->slurpHelper == 2) {
			pthread_mutex_unlock(&surfaceDescriptor->slurpMutex);
			DetachCurrentThread();
			return NULL;
		}
		surfaceDescriptor->slurpHelper = 0;
		pthread_mutex_unlock(&surfaceDescriptor->slurpMutex);
		DDGifSlurp(info, true);
		pthread_mutex_lock(&surfaceDescriptor->renderMutex);
		surfaceDescriptor->renderHelper = 1;
		pthread_cond_signal(&surfaceDescriptor->renderCond);
		pthread_mutex_unlock(&surfaceDescriptor->renderMutex);
	}
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo,
                                                    jobject jsurface, jlongArray savedState, jboolean isOpaque) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	SurfaceDescriptor *surfaceDescriptor = info->frameBufferDescriptor;
	if (surfaceDescriptor == NULL) {
		surfaceDescriptor = malloc(sizeof(SurfaceDescriptor));
		if (surfaceDescriptor == NULL) {
			throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
			return;
		}
		surfaceDescriptor->eventPollFd.events = POLL_IN;
		surfaceDescriptor->eventPollFd.fd = eventfd(0, 0);
		if (surfaceDescriptor->eventPollFd.fd == -1) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not create eventfd ");
			free(surfaceDescriptor);
			return;
		}
		const pthread_cond_t condInitializer = PTHREAD_COND_INITIALIZER;
		surfaceDescriptor->slurpCond = condInitializer;
		surfaceDescriptor->renderCond = condInitializer;
		const pthread_mutex_t mutexInitializer = PTHREAD_MUTEX_INITIALIZER;
		surfaceDescriptor->slurpMutex = mutexInitializer;
		surfaceDescriptor->renderMutex = mutexInitializer;
		surfaceDescriptor->surfaceBackupPtr = NULL;

		info->frameBufferDescriptor = surfaceDescriptor;
	}

	POLL_TYPE eftd_ctr;
	int pollResult;

	while (1) {
		pollResult = TEMP_FAILURE_RETRY(poll(&surfaceDescriptor->eventPollFd, 1, 0));
		if (pollResult == 0)
			break;
		else if (pollResult > 0) {
			ssize_t bytesRead = TEMP_FAILURE_RETRY(
					read(surfaceDescriptor->eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE));
			if (bytesRead != POLL_TYPE_SIZE) {
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
#ifdef DEBUG
		LOGE("Window lock failed %d", errno);
#endif
		ANativeWindow_release(window);
		return;
	}
	const size_t bufferSize = buffer.stride * buffer.height * sizeof(argb);

	info->stride = buffer.stride;
	long invalidationDelayMillis;
	if (surfaceDescriptor->surfaceBackupPtr) {
		memcpy(buffer.bits, surfaceDescriptor->surfaceBackupPtr, bufferSize);
		invalidationDelayMillis = 0;
		surfaceDescriptor->renderHelper = 1;
		surfaceDescriptor->slurpHelper = 0;
	}
	else {
		if (savedState != NULL) {
			invalidationDelayMillis = restoreSavedState(info, env, savedState, buffer.bits);
			if (invalidationDelayMillis < 0)
				invalidationDelayMillis = 0;
		}
		else
			invalidationDelayMillis = 0;
		surfaceDescriptor->renderHelper = 0;
		surfaceDescriptor->slurpHelper = 1;
	}

	info->lastFrameRemainder = -1;
	ANativeWindow_unlockAndPost(window);

	if (info->loopCount != 0 && info->currentLoop == info->loopCount) {
		ANativeWindow_release(window);
		pollResult = TEMP_FAILURE_RETRY(poll(&surfaceDescriptor->eventPollFd, 1, -1));
		if (pollResult < 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Animation end poll failed ");
		}
		return;
	}

	pthread_t thread;
	errno = pthread_create(&thread, NULL, slurp, info);
	if (errno != 0) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Slurp thread creation failed ");
		ANativeWindow_release(window);
		return;
	}

	while (1) {
		pollResult = TEMP_FAILURE_RETRY(poll(&surfaceDescriptor->eventPollFd, 1, (int) invalidationDelayMillis));
		long renderingStartTime = getRealTime();

		if (pollResult < 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Display loop poll failed ");
			break;
		}
		else if (pollResult > 0) {
			if (surfaceDescriptor->surfaceBackupPtr == NULL) {
				surfaceDescriptor->surfaceBackupPtr = malloc(bufferSize);
				if (surfaceDescriptor->surfaceBackupPtr == NULL) {
					throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
					break;
				}
			}
			memcpy(surfaceDescriptor->surfaceBackupPtr, buffer.bits, bufferSize);
			break;
		}
		oldBufferBits = buffer.bits;
		if (ANativeWindow_lock(window, &buffer, NULL) != 0) {
#ifdef DEBUG
			LOGE("Window lock failed %d", errno);
#endif
			break;
		}

		if (info->currentIndex == 0)
			prepareCanvas(buffer.bits, info);
		else
			memcpy(buffer.bits, oldBufferBits, bufferSize);

		pthread_mutex_lock(&surfaceDescriptor->renderMutex);
		while (surfaceDescriptor->renderHelper == 0) {
			pthread_cond_wait(&surfaceDescriptor->renderCond, &surfaceDescriptor->renderMutex);
		}
		surfaceDescriptor->renderHelper = 0;
		pthread_mutex_unlock(&surfaceDescriptor->renderMutex);

		const uint_fast32_t frameDuration = getBitmap(buffer.bits, info);

		pthread_mutex_lock(&surfaceDescriptor->slurpMutex);
		surfaceDescriptor->slurpHelper = 1;
		pthread_cond_signal(&surfaceDescriptor->slurpCond);
		pthread_mutex_unlock(&surfaceDescriptor->slurpMutex);

		ANativeWindow_unlockAndPost(window);

		invalidationDelayMillis = calculateInvalidationDelay(info, renderingStartTime, frameDuration);

		if (info->lastFrameRemainder >= 0) {
			invalidationDelayMillis = info->lastFrameRemainder;
			info->lastFrameRemainder = -1;
		}
	}

	ANativeWindow_release(window);
	pthread_mutex_lock(&surfaceDescriptor->slurpMutex);
	surfaceDescriptor->slurpHelper = 2;
	pthread_cond_signal(&surfaceDescriptor->slurpCond);
	pthread_mutex_unlock(&surfaceDescriptor->slurpMutex);
	errno = pthread_join(thread, NULL);
	if (errno != 0) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Slurp thread join failed ");
	}
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_postUnbindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->frameBufferDescriptor == NULL) {
		return;
	}
	SurfaceDescriptor const *surfaceDescriptor = info->frameBufferDescriptor;
	POLL_TYPE eftd_ctr;
	ssize_t bytesWritten = TEMP_FAILURE_RETRY(write(surfaceDescriptor->eventPollFd.fd, &eftd_ctr, POLL_TYPE_SIZE));
	if (bytesWritten != POLL_TYPE_SIZE && errno != EBADF) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not write to eventfd ");
	}
}
