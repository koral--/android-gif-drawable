#include "gif.h"

bool reset(GifInfo *info) {
	if (info->rewindFunction(info) != 0)
		return false;
	info->nextStartTime = 0;
	info->currentLoop = 0;
	info->currentIndex = 0;
	info->lastFrameRemainder = -1;
	return true;
}

__unused JNIEXPORT jboolean JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_reset(JNIEnv *__unused  env, jclass  __unused class,
                                              jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL)
		return JNI_FALSE;

	if (reset(info))
		return JNI_TRUE;
	return JNI_FALSE;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_setSpeedFactor(JNIEnv __unused *env, jclass __unused handleClass,
                                                       jlong gifInfo, jfloat factor) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL)
		return;
	info->speedFactor = factor;
}

static uint_fast32_t seek(GifInfo *info, JNIEnv *env, jint desiredIndex, jobject jbitmap) {

	void *pixels;
	if (lockPixels(env, jbitmap, info, &pixels) != 0) {
		return 0;
	}
	if (info->currentIndex == 0)
		prepareCanvas(pixels, info);
	do {
		DDGifSlurp(info, true);
		drawNextBitmap((argb *) pixels, info);
	} while (info->currentIndex++ < desiredIndex);
	unlockPixels(env, jbitmap);
	--info->currentIndex;
	return getFrameDuration(info);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToTime(JNIEnv *env, jclass __unused handleClass,
                                                   jlong gifInfo, jint desiredPos, jobject jbitmap) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL)
		return;
	if (info->gifFilePtr->ImageCount == 1)
		return;

	unsigned long sum = 0;
	int desiredIndex;
	for (desiredIndex = 0; desiredIndex < info->gifFilePtr->ImageCount - 1; desiredIndex++) {
		unsigned long newSum = sum + info->controlBlock[desiredIndex].DelayTime;
		if (newSum > desiredPos)
			break;
		sum = newSum;
	}

	if (desiredIndex < info->currentIndex && !reset(info)) {
		info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
		return;
	}

	if (info->lastFrameRemainder != -1) {
		info->lastFrameRemainder = desiredPos - sum;
		if (desiredIndex == info->gifFilePtr->ImageCount - 1 &&
		    info->lastFrameRemainder > info->controlBlock[desiredIndex].DelayTime)
			info->lastFrameRemainder = info->controlBlock[desiredIndex].DelayTime;
	}
	seek(info, env, desiredIndex, jbitmap);

	info->nextStartTime = getRealTime() + (long) (info->lastFrameRemainder / info->speedFactor);
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_seekToFrame(JNIEnv *env, jclass __unused handleClass,
                                                    jlong gifInfo, jint desiredIndex, jobject jbitmap) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL || info->gifFilePtr->ImageCount == 1)
		return;

	if (desiredIndex < info->currentIndex && !reset(info)) {
		info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
		return;
	}

	if (desiredIndex >= info->gifFilePtr->ImageCount)
		desiredIndex = (jint) (info->gifFilePtr->ImageCount - 1);

	uint_fast32_t lastFrameDuration = seek(info, env, desiredIndex, jbitmap);

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
	const long remainder = info->lastFrameRemainder;
	info->lastFrameRemainder = -1;
	return remainder;
}
