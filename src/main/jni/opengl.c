#include "gif.h"
#include <GLES2/gl2.h>

typedef struct {
	struct pollfd eventPollFd;
	void *frameBuffer;
	pthread_mutex_t renderMutex;
	pthread_t slurpThread;
	//TODO bool cacheAnimation;
	GLuint glFramebufferName;
	GLuint *glTextureNames;
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
	while (true) {
		long renderStartTime = getRealTime();
        //TODO only advance frame index if cacheAnimation is enabled and frame is cached
		DDGifSlurp(info, true, false);
		TexImageDescriptor *descriptor = info->frameBufferDescriptor;
		pthread_mutex_lock(&descriptor->renderMutex);
		if (info->currentIndex == 0) {
			prepareCanvas(descriptor->frameBuffer, info);
		}
		const uint_fast32_t frameDuration = getBitmap(descriptor->frameBuffer, info, false);
		pthread_mutex_unlock(&descriptor->renderMutex);

		const long long invalidationDelayMillis = calculateInvalidationDelay(info, renderStartTime, frameDuration);
		int pollResult = poll(&descriptor->eventPollFd, 1, (int) invalidationDelayMillis);
		eventfd_t eventValue;
		if (pollResult < 0) {
			throwException(getEnv(), RUNTIME_EXCEPTION_ERRNO, "Could not poll on eventfd ");
			break;
		} else if (pollResult > 0) {
			const int readResult = TEMP_FAILURE_RETRY(eventfd_read(descriptor->eventPollFd.fd, &eventValue));
			if (readResult != 0) {
				throwException(getEnv(), RUNTIME_EXCEPTION_ERRNO, "Could not read from eventfd ");
			}
			break;
		}
	}
	DetachCurrentThread();
	return NULL;
}

static void stopDecoderThread(JNIEnv *env, TexImageDescriptor *descriptor) {
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
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
	info->frameBufferDescriptor = NULL;
	stopDecoderThread(env, descriptor);
	free(descriptor->frameBuffer);
	free(descriptor->glTextureNames);
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
	const GifWord width = info->gifFilePtr->SWidth;
	const GifWord height = info->gifFilePtr->SHeight;
	descriptor->frameBuffer = malloc(width * height * sizeof(argb));
	if (!descriptor->frameBuffer) {
		free(descriptor);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return;
	}
	descriptor->glTextureNames = calloc(info->gifFilePtr->ImageCount, sizeof(GLuint));
	if (descriptor->glTextureNames == NULL) {
		free(descriptor->frameBuffer);
		free(descriptor);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return;
	}
	descriptor->glFramebufferName = 0;
	info->stride = (int32_t) width;
	descriptor->eventPollFd.fd = -1;
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
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
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
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
	seek(info, (uint_fast32_t) desiredIndex, descriptor->frameBuffer);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_renderFrameGL(JNIEnv *__unused env, jclass __unused handleClass, jlong gifInfo, jint rawTarget, jint level) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	TexImageDescriptor *descriptor = info->frameBufferDescriptor;
	pthread_mutex_lock(&descriptor->renderMutex);
	const GLenum target = (const GLenum) rawTarget;
	GLuint *currentTextureName = descriptor->glTextureNames + info->currentIndex;
	if (*currentTextureName != 0) {
		glBindTexture(target, *currentTextureName);
	} else {
		GLuint *framebufferName = &descriptor->glFramebufferName;
		if (*framebufferName == 0) {
			glGenFramebuffers(1, framebufferName); //TODO delete
		}
		glGenTextures(1, currentTextureName);//TODO delete
		const GLsizei width = (const GLsizei) info->gifFilePtr->SWidth;
		const GLsizei height = (const GLsizei) info->gifFilePtr->SHeight;

		glBindTexture(target, *currentTextureName);
		glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(target, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, descriptor->frameBuffer);
		glBindFramebuffer(GL_FRAMEBUFFER, *framebufferName);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, target, *currentTextureName, level);
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}
	pthread_mutex_unlock(&descriptor->renderMutex);
}
