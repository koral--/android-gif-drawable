#include "gif.h"
#include <GLES2/gl2.h>

typedef struct {
	struct pollfd eventPollFd;
	void *frameBuffer;
	pthread_t slurpThread;
} TexImageDescriptor;

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_glTexImage2D(JNIEnv *__unused unused, jclass __unused handleClass,
                                                     jlong gifInfo) {
	GifInfo *info = (GifInfo *) gifInfo;
	if (info == NULL || info->frameBufferDescriptor == NULL) {
		return;
	}
	const TexImageDescriptor *texImageDescriptor = info->frameBufferDescriptor;
	const GLsizei width = (const GLsizei) info->gifFilePtr->SWidth;
	const GLsizei height = (const GLsizei) info->gifFilePtr->SHeight;
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE,
	             texImageDescriptor->frameBuffer);
}

static void *slurp(void *pVoidInfo) {
	GifInfo *info = pVoidInfo;
	while (1) {
		long renderStartTime = getRealTime();
		DDGifSlurp(info, true);
		TexImageDescriptor *texImageDescriptor = info->frameBufferDescriptor;
		if (info->currentIndex == 0)
			prepareCanvas(texImageDescriptor->frameBuffer, info);
		const uint_fast32_t frameDuration = getBitmap((argb *) texImageDescriptor->frameBuffer, info);

		const long invalidationDelayMillis = calculateInvalidationDelay(info, renderStartTime, frameDuration);
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

static void releaseTexImageDescriptor(GifInfo *info, JNIEnv *env) {
	TexImageDescriptor *texImageDescriptor = info->frameBufferDescriptor;
	if (texImageDescriptor == NULL) {
		return;
	}
	if (texImageDescriptor->eventPollFd.fd != -1) {
		const int writeResult = TEMP_FAILURE_RETRY(eventfd_write(texImageDescriptor->eventPollFd.fd, 1));
		if (writeResult != 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not write to eventfd ");
		}
		errno = pthread_join(texImageDescriptor->slurpThread, NULL);
		if (errno != 0) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Slurp thread join failed ");
		}
		if (close(texImageDescriptor->eventPollFd.fd) != 0 && errno != EINTR) {
			throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd close failed ");
		}
		texImageDescriptor->eventPollFd.fd = -1;
	}
	free(texImageDescriptor->frameBuffer);
	free(texImageDescriptor);
	info->frameBufferDescriptor = NULL;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_startDecoderThread(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) gifInfo;
	if (info == NULL) {
		return;
	}
	TexImageDescriptor *texImageDescriptor = info->frameBufferDescriptor;
	if (texImageDescriptor != NULL) {
		return;
	}
	texImageDescriptor = malloc(sizeof(TexImageDescriptor));
	if (!texImageDescriptor) {
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return;
	}
	texImageDescriptor->frameBuffer = malloc(info->gifFilePtr->SWidth * info->gifFilePtr->SHeight * sizeof(argb));
	if (!texImageDescriptor->frameBuffer) {
		free(texImageDescriptor);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return;
	}
	texImageDescriptor->eventPollFd.events = POLL_IN;
	texImageDescriptor->eventPollFd.fd = eventfd(0, 0);
	if (texImageDescriptor->eventPollFd.fd == -1) {
		free(texImageDescriptor);
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd creation failed ");
		return;
	}
	info->destructor = releaseTexImageDescriptor;
	info->frameBufferDescriptor = texImageDescriptor;

	info->stride = (int32_t) info->gifFilePtr->SWidth;
	if (pthread_create(&texImageDescriptor->slurpThread, NULL, slurp, info) != 0) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Slurp thread creation failed ");
	}
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_stopDecoderThread(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	releaseTexImageDescriptor(info, env);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_renderGLFrame(JNIEnv *env, jclass __unused handleClass, jlong gifInfo,
                                                      jint desiredIndex) {
	GifInfo *info = (GifInfo *) gifInfo;
	if (info == NULL) {
		return;
	}
	TexImageDescriptor *texImageDescriptor = info->frameBufferDescriptor;
	if (texImageDescriptor == NULL) {
		texImageDescriptor = malloc(sizeof(TexImageDescriptor));
		if (!texImageDescriptor) {
			throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
			return;
		}
		texImageDescriptor->frameBuffer = malloc(info->gifFilePtr->SWidth * info->gifFilePtr->SHeight * sizeof(argb));
		if (!texImageDescriptor->frameBuffer) {
			free(texImageDescriptor);
			throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
			return;
		}
		info->frameBufferDescriptor = texImageDescriptor;
		info->stride = (int32_t) info->gifFilePtr->SWidth;
		texImageDescriptor->eventPollFd.fd = -1;
	}

	seek(info, desiredIndex, texImageDescriptor->frameBuffer);

	const GLsizei width = (const GLsizei) info->gifFilePtr->SWidth;
	const GLsizei height = (const GLsizei) info->gifFilePtr->SHeight;
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, texImageDescriptor->frameBuffer);
}

