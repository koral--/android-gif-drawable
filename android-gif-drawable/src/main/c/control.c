#include "gif.h"

bool reset(GifInfo *info) {
	if (info->rewindFunction(info) != 0) {
		return false;
	}
	info->nextStartTime = 0;
	info->currentLoop = 0;
	info->currentIndex = 0;
	info->lastFrameRemainder = -1;
	return true;
}

__unused JNIEXPORT jboolean JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_reset(JNIEnv *__unused  env, jclass  __unused class, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info != NULL && reset(info)) {
		return JNI_TRUE;
	}
	return JNI_FALSE;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_setSpeedFactor(JNIEnv __unused *env, jclass __unused handleClass,
                                                       jlong gifInfo, jfloat factor) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	info->speedFactor = factor;
}

static uint_fast32_t seekBitmap(GifInfo *info, JNIEnv *env, jint desiredIndex, jobject jbitmap) {
	void *pixels;
	if (lockPixels(env, jbitmap, info, &pixels) != 0) {
		return 0;
	}
	uint_fast32_t duration = seek(info, (uint_fast32_t) desiredIndex, pixels);
	unlockPixels(env, jbitmap);
	return duration;
}

uint_fast32_t seek(GifInfo *info, uint_fast32_t desiredIndex, void *pixels) {
	GifFileType *const gifFilePtr = info->gifFilePtr;
	if (desiredIndex < info->currentIndex || info->currentIndex == 0) {
		if (!reset(info)) {
			gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
			return 0;
		}
		prepareCanvas(pixels, info);
	}
	if (desiredIndex >= gifFilePtr->ImageCount) {
		desiredIndex = gifFilePtr->ImageCount - 1;
	}

	uint_fast32_t i;
	for (i = desiredIndex; i > info->currentIndex; i--) {
		const GifImageDesc imageDesc = info->gifFilePtr->SavedImages[i].ImageDesc;
		if (gifFilePtr->SWidth == imageDesc.Width && gifFilePtr->SHeight == imageDesc.Height) {
			const GraphicsControlBlock controlBlock = info->controlBlock[i];
			if (controlBlock.TransparentColor == NO_TRANSPARENT_COLOR) {
				break;
			} else if (controlBlock.DisposalMode == DISPOSE_BACKGROUND) {
				break;
			}
		}
	}

	if (i > 0) {
		while (info->currentIndex < i - 1) {
			DDGifSlurp(info, false, true);
			++info->currentIndex;
		}
	}

	do {
		DDGifSlurp(info, true, false);
		drawNextBitmap(pixels, info);
	} while (info->currentIndex++ < desiredIndex);
	--info->currentIndex;
	return getFrameDuration(info);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToTime(JNIEnv *env, jclass __unused handleClass,
                                                   jlong gifInfo, jint desiredPos, jobject jbitmap) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}

	unsigned long sum = 0;
	unsigned int desiredIndex;
	for (desiredIndex = 0; desiredIndex < info->gifFilePtr->ImageCount - 1; desiredIndex++) {
		unsigned long newSum = sum + info->controlBlock[desiredIndex].DelayTime;
		if (newSum > (unsigned long) desiredPos)
			break;
		sum = newSum;
	}

	if (info->lastFrameRemainder != -1) {
		info->lastFrameRemainder = desiredPos - sum;
		if (desiredIndex == info->gifFilePtr->ImageCount - 1 &&
		    info->lastFrameRemainder > (long long) info->controlBlock[desiredIndex].DelayTime)
			info->lastFrameRemainder = info->controlBlock[desiredIndex].DelayTime;
	}
	seekBitmap(info, env, desiredIndex, jbitmap);

	info->nextStartTime = getRealTime() + (long) (info->lastFrameRemainder / info->speedFactor);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToFrame(JNIEnv *env, jclass __unused handleClass,
                                                    jlong gifInfo, jint desiredIndex, jobject jbitmap) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}

	uint_fast32_t lastFrameDuration = seekBitmap(info, env, desiredIndex, jbitmap);

	info->nextStartTime = getRealTime() + (long) (lastFrameDuration / info->speedFactor);
	if (info->lastFrameRemainder != -1)
		info->lastFrameRemainder = 0;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_saveRemainder(JNIEnv *__unused  env, jclass __unused handleClass,
                                                      jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->lastFrameRemainder != -1 || info->currentIndex == info->gifFilePtr->ImageCount ||
	    info->gifFilePtr->ImageCount == 1)
		return;
	info->lastFrameRemainder = info->nextStartTime - getRealTime();
	if (info->lastFrameRemainder < 0)
		info->lastFrameRemainder = 0;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_restoreRemainder(JNIEnv *__unused env,
                                                         jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->lastFrameRemainder == -1 || info->gifFilePtr->ImageCount == 1 ||
	    (info->loopCount > 0 && info->currentLoop == info->loopCount))
		return -1;
	info->nextStartTime = getRealTime() + info->lastFrameRemainder;
	const long long remainder = info->lastFrameRemainder;
	info->lastFrameRemainder = -1;
	return remainder;
}
