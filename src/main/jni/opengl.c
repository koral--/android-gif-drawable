#include "gif.h"
#include <GLES2/gl2.h>

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_glTexImage2D(JNIEnv *__unused unused, jclass __unused handleClass, jlong gifInfo, jint target, jint level) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->frameBufferDescriptor == NULL) {
		return;
	}
	const GLsizei width = (const GLsizei) info->gifFilePtr->SWidth;
	const GLsizei height = (const GLsizei) info->gifFilePtr->SHeight;
	FrameBufferDescriptor *descriptor = info->frameBufferDescriptor;
	void *const pixels = descriptor->frameBuffer;
	pthread_mutex_lock(&descriptor->renderMutex);
	glTexImage2D((GLenum) target, level, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
	pthread_mutex_unlock(&descriptor->renderMutex);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_glTexSubImage2D(JNIEnv *__unused env, jclass __unused handleClass, jlong gifInfo, jint target, jint level) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->frameBufferDescriptor == NULL) {
		return;
	}
	const GLsizei width = (const GLsizei) info->gifFilePtr->SWidth;
	const GLsizei height = (const GLsizei) info->gifFilePtr->SHeight;
	FrameBufferDescriptor *descriptor = info->frameBufferDescriptor;
	void *const pixels = descriptor->frameBuffer;
	pthread_mutex_lock(&descriptor->renderMutex);
	glTexSubImage2D((GLenum) target, level, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
	pthread_mutex_unlock(&descriptor->renderMutex);
}

static void *slurp(void *pVoidInfo) {
	GifInfo *info = pVoidInfo;
	while (true) {
		long renderStartTime = getRealTime();
		DDGifSlurp(info, true, false);
		FrameBufferDescriptor *texImageDescriptor = info->frameBufferDescriptor;
		pthread_mutex_lock(&texImageDescriptor->renderMutex);
		if (info->currentIndex == 0) {
			prepareCanvas(texImageDescriptor->frameBuffer, info);
		}
		const uint_fast32_t frameDuration = getBitmap(texImageDescriptor->frameBuffer, info, true);
		pthread_mutex_unlock(&texImageDescriptor->renderMutex);

		const long long invalidationDelayMillis = calculateInvalidationDelay(info, renderStartTime, frameDuration);
		int pollResult = poll(&texImageDescriptor->eventPollFd, 1, (int) invalidationDelayMillis);
		eventfd_t eventValue;
		if (pollResult < 0) {
			throwException(getEnv(), RUNTIME_EXCEPTION_ERRNO, "Could not poll on eventfd ");
			break;
		} else if (pollResult > 0) {
			const int readResult = TEMP_FAILURE_RETRY(eventfd_read(texImageDescriptor->eventPollFd.fd, &eventValue));
			if (readResult != 0) {
				throwException(getEnv(), RUNTIME_EXCEPTION_ERRNO, "Could not read from eventfd ");
			}
			break;
		}
	}
	DetachCurrentThread();
	return NULL;
}

static void stopDecoderThread(JNIEnv *env, FrameBufferDescriptor *descriptor) {
	if (descriptor->eventPollFd.fd != -1) {
		const int writeResult = TEMP_FAILURE_RETRY(eventfd_write(descriptor->eventPollFd.fd, 1));
		if (writeResult != 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not write to eventfd ");
		}
		errno = pthread_join(descriptor->slurpThread, NULL);
		THROW_ON_NONZERO_RESULT(errno, "Slurp thread join failed ");

		if (close(descriptor->eventPollFd.fd) != 0 && errno != EINTR) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd close failed ");
		}
		descriptor->eventPollFd.fd = -1;
	}
}

static void releaseTexImageDescriptor(GifInfo *info, JNIEnv *env) {
	FrameBufferDescriptor *descriptor = info->frameBufferDescriptor;
	stopDecoderThread(env, descriptor);
	releaseFrameBufferDescriptor(info, env);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_initTexImageDescriptor(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	FrameBufferDescriptor *descriptor = malloc(sizeof(FrameBufferDescriptor));
	if (!descriptor) {
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return;
	}
	descriptor->eventPollFd.fd = -1;
	const GifWord width = info->gifFilePtr->SWidth;
	const GifWord height = info->gifFilePtr->SHeight;
	descriptor->frameBuffer = malloc(width * height * sizeof(argb));
	if (!descriptor->frameBuffer) {
		free(descriptor);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return;
	}
	info->stride = (int32_t) width;
	info->frameBufferDescriptor = descriptor;
	errno = pthread_mutex_init(&descriptor->renderMutex, NULL);
	THROW_ON_NONZERO_RESULT(errno, "Render mutex initialization failed ");
	errno = pthread_mutex_init(&descriptor->slurpMutex, NULL);
	THROW_ON_NONZERO_RESULT(errno, "Slurp mutex initialization failed ");
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_startDecoderThread(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	FrameBufferDescriptor *descriptor = info->frameBufferDescriptor;
	if (descriptor->eventPollFd.fd != -1) {
		return;
	}

	descriptor->eventPollFd.events = POLL_IN;
	descriptor->eventPollFd.fd = eventfd(0, 0);
	if (descriptor->eventPollFd.fd == -1) {
		free(descriptor);
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd creation failed ");
		return;
	}
	info->frameBufferDescriptor = descriptor;
	info->destructor = releaseTexImageDescriptor;

	errno = pthread_create(&descriptor->slurpThread, NULL, slurp, info);
	THROW_ON_NONZERO_RESULT(errno, "Slurp thread creation failed ");
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_stopDecoderThread(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->frameBufferDescriptor == NULL) {
		return;
	}
	stopDecoderThread(env, info->frameBufferDescriptor);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToFrameGL(__unused JNIEnv *env, jclass __unused handleClass, jlong gifInfo, jint desiredIndex) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	FrameBufferDescriptor *descriptor = info->frameBufferDescriptor;
	seek(info, (uint_fast32_t) desiredIndex, descriptor->frameBuffer);
}

