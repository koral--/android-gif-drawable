#include "gif.h"
#include <GLES2/gl2.h>

typedef struct {
	struct pollfd eventPollFd;
	void *frameBuffer;
	pthread_mutex_t renderMutex;
	pthread_t slurpThread;
} TexImageDescriptor;

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_glTexImage2D(JNIEnv *__unused unused, jclass __unused handleClass, jlong gifInfo, jint target, jint level) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->frameBufferDescriptor == NULL) {
		return;
	}
	const GLsizei width = (const GLsizei) info->gifFilePtr->SWidth;
	const GLsizei height = (const GLsizei) info->gifFilePtr->SHeight;
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
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
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
	void *const pixels = descriptor->frameBuffer;
	pthread_mutex_lock(&descriptor->renderMutex);
	glTexSubImage2D((GLenum) target, level, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
	pthread_mutex_unlock(&descriptor->renderMutex);
}

static void *slurp(void *pVoidInfo) {
	GifInfo *info = pVoidInfo;
	JNIEnv *env = getEnv();
	while (true) {
		long renderStartTime = getRealTime();
		DDGifSlurp(info, true, false);
		TexImageDescriptor *texImageDescriptor = info->frameBufferDescriptor;
		pthread_mutex_lock(&texImageDescriptor->renderMutex);
		if (info->currentIndex == 0) {
			prepareCanvas(texImageDescriptor->frameBuffer, info);
		}
		const uint_fast32_t frameDuration = getBitmap(texImageDescriptor->frameBuffer, info);
		pthread_mutex_unlock(&texImageDescriptor->renderMutex);

		const long long invalidationDelayMillis = calculateInvalidationDelay(info, renderStartTime, frameDuration);
		const int pollResult = TEMP_FAILURE_RETRY(poll(&texImageDescriptor->eventPollFd, 1, (int) invalidationDelayMillis));
		if (pollResult < 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not poll on eventfd ");
			break;
		} else if (pollResult > 0) {
			break;
		}
	}
	DetachCurrentThread();
	return NULL;
}

static void stopDecoderThread(JNIEnv *env, TexImageDescriptor *texImageDescriptor) {
	if (texImageDescriptor->eventPollFd.fd == -1) {
		return;
	}
	if (close(texImageDescriptor->eventPollFd.fd) != 0 && errno != EINTR) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd close failed ");
	}
	errno = pthread_join(texImageDescriptor->slurpThread, NULL);
	THROW_ON_NONZERO_RESULT(errno, "Slurp thread join failed ");

	texImageDescriptor->eventPollFd.fd = -1;
}

static void releaseTexImageDescriptor(GifInfo *info, JNIEnv *env) {
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
	stopDecoderThread(env, descriptor);
	info->frameBufferDescriptor = NULL;
	free(descriptor->frameBuffer);
	errno = pthread_mutex_destroy(&descriptor->renderMutex);
	THROW_ON_NONZERO_RESULT(errno, "Render mutex destroy failed ");
	free(descriptor);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_initTexImageDescriptor(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	TexImageDescriptor *descriptor = malloc(sizeof(TexImageDescriptor));
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
	info->stride = (uint32_t) width;
	info->frameBufferDescriptor = descriptor;
	errno = pthread_mutex_init(&descriptor->renderMutex, NULL);
	THROW_ON_NONZERO_RESULT(errno, "Render mutex initialization failed ");
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_startDecoderThread(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	TexImageDescriptor *texImageDescriptor = info->frameBufferDescriptor;
	if (texImageDescriptor->eventPollFd.fd != -1) {
		return;
	}

	texImageDescriptor->eventPollFd.events = POLL_IN;
	texImageDescriptor->eventPollFd.fd = eventfd(0, 0);
	if (texImageDescriptor->eventPollFd.fd == -1) {
		free(texImageDescriptor);
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd creation failed ");
		return;
	}
	info->frameBufferDescriptor = texImageDescriptor;
	info->destructor = releaseTexImageDescriptor;

	errno = pthread_create(&texImageDescriptor->slurpThread, NULL, slurp, info);
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
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
	seek(info, (uint_fast32_t) desiredIndex, descriptor->frameBuffer);
}

