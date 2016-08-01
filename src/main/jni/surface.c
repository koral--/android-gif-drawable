#include "gif.h"
#include <android/native_window_jni.h>

typedef struct {
	struct pollfd eventPollFd;
	void *surfaceBackupPtr;
	uint8_t slurpHelper;
	pthread_mutex_t slurpMutex;
	pthread_cond_t slurpCond;
	uint8_t renderHelper;
	pthread_mutex_t renderMutex;
	pthread_cond_t renderCond;
} SurfaceDescriptor;

static void *slurp(void *pVoidInfo) {
	GifInfo *info = pVoidInfo;
	SurfaceDescriptor *surfaceDescriptor = info->frameBufferDescriptor;
	while (1) {
		pthread_mutex_lock(&surfaceDescriptor->slurpMutex);
		while (surfaceDescriptor->slurpHelper == 0) {
			pthread_cond_wait(&surfaceDescriptor->slurpCond, &surfaceDescriptor->slurpMutex);
		}

		if (surfaceDescriptor->slurpHelper == 2) {
			pthread_mutex_unlock(&surfaceDescriptor->slurpMutex);
			DetachCurrentThread();
			return NULL;
		}
		surfaceDescriptor->slurpHelper = 0;
		pthread_mutex_unlock(&surfaceDescriptor->slurpMutex);
		DDGifSlurp(info, true, false);
		pthread_mutex_lock(&surfaceDescriptor->renderMutex);
		surfaceDescriptor->renderHelper = 1;
		pthread_cond_signal(&surfaceDescriptor->renderCond);
		pthread_mutex_unlock(&surfaceDescriptor->renderMutex);
	}
}

static void releaseSurfaceDescriptor(GifInfo *info, JNIEnv *env) {
	SurfaceDescriptor *surfaceDescriptor = info->frameBufferDescriptor;
	if (surfaceDescriptor == NULL)
		return;

	free(surfaceDescriptor->surfaceBackupPtr);
	surfaceDescriptor->surfaceBackupPtr = NULL;
	if (close(surfaceDescriptor->eventPollFd.fd) != 0 && errno != EINTR) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd close failed ");
	}
	THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->slurpMutex), "Slurp mutex destroy failed ");
	THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->renderMutex), "Render mutex destroy failed ");
	THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->slurpCond), "Slurp cond destroy failed ");
	THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->renderCond), "Render cond  destroy failed ");
	free(surfaceDescriptor);
	info->frameBufferDescriptor = NULL;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo,
                                                    jobject jsurface, jlongArray savedState) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	SurfaceDescriptor *surfaceDescriptor = info->frameBufferDescriptor;
	if (surfaceDescriptor == NULL) {
		info->destructor = releaseSurfaceDescriptor;
		surfaceDescriptor = malloc(sizeof(SurfaceDescriptor));
		if (surfaceDescriptor == NULL) {
			throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
			return;
		}
		surfaceDescriptor->eventPollFd.events = POLL_IN;
		surfaceDescriptor->eventPollFd.fd = eventfd(0, 0);
		if (surfaceDescriptor->eventPollFd.fd == -1) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd creation failed ");
			free(surfaceDescriptor);
			return;
		}
		THROW_ON_NONZERO_RESULT(pthread_cond_init(&surfaceDescriptor->slurpCond, NULL), "Slurp condition variable initialization failed ");
		THROW_ON_NONZERO_RESULT(pthread_cond_init(&surfaceDescriptor->renderCond, NULL), "Render condition variable initialization failed ");
		THROW_ON_NONZERO_RESULT(pthread_mutex_init(&surfaceDescriptor->slurpMutex, NULL), "Slurp mutex initialization failed ");
		THROW_ON_NONZERO_RESULT(pthread_mutex_init(&surfaceDescriptor->renderMutex, NULL), "Render mutex initialization failed ");
		surfaceDescriptor->surfaceBackupPtr = NULL;

		info->frameBufferDescriptor = surfaceDescriptor;
	}

	eventfd_t eventValue;
	int pollResult;

	while (1) {
		pollResult = TEMP_FAILURE_RETRY(poll(&surfaceDescriptor->eventPollFd, 1, 0));
		if (pollResult == 0)
			break;
		else if (pollResult > 0) {
			const int readResult = TEMP_FAILURE_RETRY(eventfd_read(surfaceDescriptor->eventPollFd.fd, &eventValue));
			if (readResult != 0) {
				throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not read from eventfd ");
				return;
			}
		}
		else {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not poll on eventfd ");
			return;
		}
	}

	const int32_t windowFormat = info->isOpaque ? WINDOW_FORMAT_RGBX_8888 : WINDOW_FORMAT_RGBA_8888;
	struct ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
	GifFileType *const gifFilePtr = info->gifFilePtr;
	if (ANativeWindow_setBuffersGeometry(window, (int32_t) gifFilePtr->SWidth, (int32_t) gifFilePtr->SHeight, windowFormat) != 0) {
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
	long long invalidationDelayMillis;
	if (surfaceDescriptor->surfaceBackupPtr) {
		memcpy(buffer.bits, surfaceDescriptor->surfaceBackupPtr, bufferSize);
		invalidationDelayMillis = 0;
		surfaceDescriptor->renderHelper = 1;
		surfaceDescriptor->slurpHelper = 0;
	} else {
		if (savedState != NULL) {
			invalidationDelayMillis = restoreSavedState(info, env, savedState, buffer.bits);
			if (invalidationDelayMillis < 0)
				invalidationDelayMillis = 0;
		} else
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
		} else if (pollResult > 0) {
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

		struct ARect *dirtyRectPtr;
		if (info->currentIndex == 0) {
			dirtyRectPtr = NULL;
		} else {
			const GifImageDesc imageDesc = gifFilePtr->SavedImages[info->currentIndex].ImageDesc;
			struct ARect dirtyRect = {
					.left = imageDesc.Left,
					.top = imageDesc.Top,
					.right = imageDesc.Left + imageDesc.Width,
					.bottom = imageDesc.Top + imageDesc.Height
			};
			dirtyRectPtr = &dirtyRect;
		}
		if (ANativeWindow_lock(window, &buffer, dirtyRectPtr) != 0) {
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

		const uint_fast32_t frameDuration = getBitmap(buffer.bits, info, false);

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
	const int writeResult = TEMP_FAILURE_RETRY(eventfd_write(surfaceDescriptor->eventPollFd.fd, 1));
	if (writeResult != 0 && errno != EBADF) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not write to eventfd ");
	}
}
