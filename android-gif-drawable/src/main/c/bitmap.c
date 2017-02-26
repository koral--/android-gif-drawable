#include "gif.h"
#include <android/bitmap.h>
#include "bitmap.h"

int lockPixels(JNIEnv *env, jobject jbitmap, Animation *animation, void **pixels) {
	AndroidBitmapInfo bitmapInfo;
	if (AndroidBitmap_getInfo(env, jbitmap, &bitmapInfo) == ANDROID_BITMAP_RESULT_SUCCESS)
		animation->stride = bitmapInfo.width;
	else {
		throwException(env, RUNTIME_EXCEPTION_BARE, "Could not get bitmap info");
		return -2;
	}

	const int lockPixelsResult = AndroidBitmap_lockPixels(env, jbitmap, pixels);
	if (lockPixelsResult == ANDROID_BITMAP_RESULT_SUCCESS) {
		return 0;
	}

	char *message;
	switch (lockPixelsResult) {
		case ANDROID_BITMAP_RESULT_ALLOCATION_FAILED:
#ifdef DEBUG
			LOGE("bitmap lock allocation failed");
#endif
			return -1; //#122 workaround
		case ANDROID_BITMAP_RESULT_BAD_PARAMETER:
			message = "Lock pixels error, bad parameter";
			break;
		case ANDROID_BITMAP_RESULT_JNI_EXCEPTION:
			message = "Lock pixels error, JNI exception";
			break;
		default:
			message = "Lock pixels error";
	}
	throwException(env, RUNTIME_EXCEPTION_BARE, message);
	return -2;
}

void unlockPixels(JNIEnv *env, jobject jbitmap) {
	const int unlockPixelsResult = AndroidBitmap_unlockPixels(env, jbitmap);
	if (unlockPixelsResult == ANDROID_BITMAP_RESULT_SUCCESS) {
		return;
	}
	char *message;
	switch (unlockPixelsResult) {
		case ANDROID_BITMAP_RESULT_BAD_PARAMETER:
			message = "Unlock pixels error, bad parameter";
			break;
		case ANDROID_BITMAP_RESULT_JNI_EXCEPTION:
			message = "Unlock pixels error, JNI exception";
			break;
		default:
			message = "Unlock pixels error";
	}
	throwException(env, RUNTIME_EXCEPTION_BARE, message);
}

uint_fast32_t renderGifBitmap(Animation *animation, void *pixels, uint_fast32_t frameIndex) {
	GifInfo *info = animation->data;
	DDGifSlurp(info, true, false);
	if (frameIndex == 0) {
		prepareCanvas(pixels, info);
	}
	return getBitmap(pixels, info);
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_renderFrame(JNIEnv *env, jclass __unused handleClass, jlong animationPtr, jobject jbitmap) {
	Animation *animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return -1;
	}

	long renderStartTime = getRealTime();
	void *pixels;
	if (lockPixels(env, jbitmap, animation, &pixels) != 0) {
		return 0;
	}

	const uint_fast32_t frameDuration = animation->functions->RenderBitmap(animation, pixels, animation->currentFrameIndex);
	unlockPixels(env, jbitmap);
	return calculateInvalidationDelay(animation, renderStartTime, frameDuration);
}
