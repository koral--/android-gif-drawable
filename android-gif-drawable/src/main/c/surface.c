#include "gif.h"
#include <android/native_window_jni.h>

typedef struct {
	struct pollfd eventPollFd;
	void *frameBuffer;
	uint8_t slurpHelper;
	pthread_mutex_t slurpMutex;
	pthread_cond_t slurpCond;
	uint8_t renderHelper;
	pthread_mutex_t renderMutex;
	pthread_cond_t renderCond;
	pthread_t slurpThread;
} SurfaceDescriptor;

static void *slurp(void *pVoidInfo) {
	GifInfo *info = pVoidInfo;
	SurfaceDescriptor *descriptor = info->frameBufferDescriptor;
	while (1) {
		pthread_mutex_lock(&descriptor->slurpMutex);
		while (descriptor->slurpHelper == 0) {
			pthread_cond_wait(&descriptor->slurpCond, &descriptor->slurpMutex);
		}

		if (descriptor->slurpHelper == 2) {
			pthread_mutex_unlock(&descriptor->slurpMutex);
			DetachCurrentThread();
			return NULL;
		}
		descriptor->slurpHelper = 0;
		pthread_mutex_unlock(&descriptor->slurpMutex);
		DDGifSlurp(info, true, false);
		pthread_mutex_lock(&descriptor->renderMutex);
		descriptor->renderHelper = 1;
		pthread_cond_signal(&descriptor->renderCond);
		pthread_mutex_unlock(&descriptor->renderMutex);
	}
}

static void releaseSurfaceDescriptor(GifInfo *info, JNIEnv *env) {
	SurfaceDescriptor *descriptor = info->frameBufferDescriptor;
	info->frameBufferDescriptor = NULL;
	free(descriptor->frameBuffer);
	if (close(descriptor->eventPollFd.fd) != 0 && errno != EINTR) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd close failed ");
	}
	errno = pthread_mutex_destroy(&descriptor->slurpMutex);
	THROW_ON_NONZERO_RESULT(errno, "Slurp mutex destroy failed ");
	errno = pthread_mutex_destroy(&descriptor->renderMutex);
	THROW_ON_NONZERO_RESULT(errno, "Render mutex destroy failed ");
	errno = pthread_cond_destroy(&descriptor->slurpCond);
	THROW_ON_NONZERO_RESULT(errno, "Slurp cond destroy failed ");
	errno = pthread_cond_destroy(&descriptor->renderCond);
	THROW_ON_NONZERO_RESULT(errno, "Render cond  destroy failed ");
	free(descriptor);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_bindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo,
                                                    jobject jsurface, jlongArray savedState) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	SurfaceDescriptor *descriptor = info->frameBufferDescriptor;
	if (descriptor == NULL) {
		descriptor = malloc(sizeof(SurfaceDescriptor));
		if (descriptor == NULL) {
			throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
			return;
		}
		descriptor->eventPollFd.events = POLL_IN;
		descriptor->eventPollFd.fd = eventfd(0, 0);
		if (descriptor->eventPollFd.fd == -1) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd creation failed ");
			free(descriptor);
			return;
		}
		errno = pthread_cond_init(&descriptor->slurpCond, NULL);
		THROW_ON_NONZERO_RESULT(errno, "Slurp condition variable initialization failed ");
		errno = pthread_cond_init(&descriptor->renderCond, NULL);
		THROW_ON_NONZERO_RESULT(errno, "Render condition variable initialization failed ");
		errno = pthread_mutex_init(&descriptor->slurpMutex, NULL);
		THROW_ON_NONZERO_RESULT(errno, "Slurp mutex initialization failed ");
		errno = pthread_mutex_init(&descriptor->renderMutex, NULL);
		THROW_ON_NONZERO_RESULT(errno, "Render mutex initialization failed ");
		descriptor->frameBuffer = NULL;
		info->frameBufferDescriptor = descriptor;
		info->destructor = releaseSurfaceDescriptor;
	}

	eventfd_t eventValue;
	int pollResult;

	while (true) {
		pollResult = TEMP_FAILURE_RETRY(poll(&descriptor->eventPollFd, 1, 0));
		if (pollResult == 0)
			break;
		else if (pollResult > 0) {
			const int readResult = TEMP_FAILURE_RETRY(eventfd_read(descriptor->eventPollFd.fd, &eventValue));
			if (readResult != 0) {
				throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not read from eventfd ");
				return;
			}
		} else {
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
		LOGE("Full window lock failed %d", errno);
#endif
		ANativeWindow_release(window);
		return;
	}
	const size_t bufferSize = buffer.stride * buffer.height * sizeof(argb);

	info->stride = (uint32_t) buffer.stride;
	long long invalidationDelayMillis;
	if (descriptor->frameBuffer) {
		memcpy(buffer.bits, descriptor->frameBuffer, bufferSize);
		invalidationDelayMillis = 0;
		descriptor->renderHelper = 1;
		descriptor->slurpHelper = 0;
	} else {
		if (savedState != NULL) {
			invalidationDelayMillis = restoreSavedState(info, env, savedState, buffer.bits);
			if (invalidationDelayMillis < 0)
				invalidationDelayMillis = 0;
		} else
			invalidationDelayMillis = 0;
		descriptor->renderHelper = 0;
		descriptor->slurpHelper = 1;
	}

	info->lastFrameRemainder = -1;
	ANativeWindow_unlockAndPost(window);

	if (info->loopCount != 0 && info->currentLoop == info->loopCount) {
		ANativeWindow_release(window);
		pollResult = TEMP_FAILURE_RETRY(poll(&descriptor->eventPollFd, 1, -1));
		if (pollResult < 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Animation end poll failed ");
		}
		return;
	}

	errno = pthread_create(&descriptor->slurpThread, NULL, slurp, info);
	if (errno != 0) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Slurp thread creation failed ");
		ANativeWindow_release(window);
		return;
	}

	while (true) {
		pollResult = TEMP_FAILURE_RETRY(poll(&descriptor->eventPollFd, 1, (int) invalidationDelayMillis));
		long renderingStartTime = getRealTime();

		if (pollResult < 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Display loop poll failed ");
			break;
		} else if (pollResult > 0) {
			if (descriptor->frameBuffer == NULL) {
				descriptor->frameBuffer = malloc(bufferSize);
				if (descriptor->frameBuffer == NULL) {
					throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
					break;
				}
			}
			memcpy(descriptor->frameBuffer, buffer.bits, bufferSize);
			break;
		}
		oldBufferBits = buffer.bits;

		const GifImageDesc imageDesc = gifFilePtr->SavedImages[info->currentIndex].ImageDesc;
		struct ARect dirtyRect = {
				.left = imageDesc.Left,
				.top = imageDesc.Top,
				.right = imageDesc.Left + imageDesc.Width,
				.bottom = imageDesc.Top + imageDesc.Height
		};

		struct ARect *dirtyRectPtr = (info->currentIndex == 0) ? NULL : &dirtyRect;

		if (ANativeWindow_lock(window, &buffer, dirtyRectPtr) != 0) {
#ifdef DEBUG
			LOGE("Partial window lock failed %d", errno);
#endif
			break;
		}

		if (info->currentIndex == 0)
			prepareCanvas(buffer.bits, info);
		else
			memcpy(buffer.bits, oldBufferBits, bufferSize);

		pthread_mutex_lock(&descriptor->renderMutex);
		while (descriptor->renderHelper == 0) {
			pthread_cond_wait(&descriptor->renderCond, &descriptor->renderMutex);
		}
		descriptor->renderHelper = 0;
		pthread_mutex_unlock(&descriptor->renderMutex);

		const uint_fast32_t frameDuration = getBitmap(buffer.bits, info);

		pthread_mutex_lock(&descriptor->slurpMutex);
		descriptor->slurpHelper = 1;
		pthread_cond_signal(&descriptor->slurpCond);
		pthread_mutex_unlock(&descriptor->slurpMutex);

		ANativeWindow_unlockAndPost(window);

		invalidationDelayMillis = calculateInvalidationDelay(info, renderingStartTime, frameDuration);

		if (info->lastFrameRemainder >= 0) {
			invalidationDelayMillis = info->lastFrameRemainder;
			info->lastFrameRemainder = -1;
		}
	}

	ANativeWindow_release(window);
	pthread_mutex_lock(&descriptor->slurpMutex);
	descriptor->slurpHelper = 2;
	pthread_cond_signal(&descriptor->slurpCond);
	pthread_mutex_unlock(&descriptor->slurpMutex);
	errno = pthread_join(descriptor->slurpThread, NULL);
	THROW_ON_NONZERO_RESULT(errno, "Slurp thread join failed");
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_postUnbindSurface(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->frameBufferDescriptor == NULL) {
		return;
	}
	SurfaceDescriptor const *descriptor = info->frameBufferDescriptor;
	const int writeResult = TEMP_FAILURE_RETRY(eventfd_write(descriptor->eventPollFd.fd, 1));
	if (writeResult != 0 && errno != EBADF) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not write to eventfd ");
	}
}
