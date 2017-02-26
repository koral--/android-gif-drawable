#include "gif.h"
#include "bitmap.h"

__unused JNIEXPORT jstring JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getComment(JNIEnv *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return NULL;
	}
	char *comment = animation->functions->GetComment(animation);
	return (*env)->NewStringUTF(env, comment);
}

__unused JNIEXPORT jboolean JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_isAnimationCompleted(JNIEnv __unused *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return JNI_FALSE;
	}
	if (animation->loopCount != 0 && animation->currentLoopIndex == animation->loopCount) {
		return JNI_TRUE;
	}
	return JNI_FALSE;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getLoopCount(JNIEnv __unused *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return NULL;
	}
	return (jint) animation->loopCount;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_setLoopCount(JNIEnv __unused *env, jclass __unused handleClass, jlong animationPtr, jchar loopCount) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return;
	}
	animation->loopCount = loopCount;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getDuration(JNIEnv *__unused  env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return NULL;
	}
	uint_fast32_t i;
	uint_fast32_t sum = 0;
	for (i = 0; i < animation->numberOfFrames; i++) {
		sum += animation->functions->GetDuration(animation, i);
	}
	return (jint) sum;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getSourceLength(JNIEnv __unused *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return -1;
	}
	return animation->sourceLength;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getCurrentPosition(JNIEnv *__unused env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	if (animation->numberOfFrames == 1) {
		return 0;
	}

	uint_fast32_t i;
	uint32_t sum = 0;
	for (i = 0; i < animation->currentFrameIndex; i++) {
		sum += animation->functions->GetDuration(animation, i);
	}

	long long remainder;
	if (animation->lastFrameRemainder == -1) {
		remainder = animation->nextStartTime - getRealTime();
		if (remainder < 0) { //in case of if frame hasn't been rendered until nextStartTime passed
			remainder = 0;
		}
	} else {
		remainder = animation->lastFrameRemainder;
	}
	return (jint) (sum - remainder); //2^31-1[ms]>596[h] so jint is enough
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getMetadataByteCount(JNIEnv *__unused  env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}

	return (jlong) animation->functions->GetMetadataByteCount(animation);
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getAllocationByteCount(JNIEnv *__unused  env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	return (jlong) animation->functions->GetAllocationByteCount(animation);
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getNativeErrorCode(JNIEnv *__unused  env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	return animation->functions->GetErrorCode(animation);
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getCurrentLoop(JNIEnv __unused *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	return (jint) animation->currentLoopIndex;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getCurrentFrameIndex(JNIEnv __unused *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return -1;
	}
	return (jint) animation->currentFrameIndex;
}

__unused JNIEXPORT jlongArray JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getSavedState(JNIEnv *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return NULL;
	}

	const jlongArray state = (*env)->NewLongArray(env, 4);
	if (state == NULL) {
		throwException(env, RUNTIME_EXCEPTION_BARE, "Could not create state array");
		return NULL;
	}
	jlong nativeState[4] = {(jlong) animation->currentFrameIndex, (jlong) animation->currentLoopIndex, animation->lastFrameRemainder};
	memcpy(nativeState + 3, &animation->speedFactor, sizeof(animation->speedFactor));
	(*env)->SetLongArrayRegion(env, state, 0, 4, nativeState);
	return state;
}

long long restoreSavedState(Animation *animation, JNIEnv *env, jlongArray state, void *pixels) {
	if (animation->numberOfFrames == 1) {
		return -1;
	}

	jlong nativeState[4];
	(*env)->GetLongArrayRegion(env, state, 0, 4, nativeState);

	const uint_fast32_t savedIndex = (uint_fast32_t) nativeState[0];
	const uint_fast8_t savedLoop = (uint_fast8_t) nativeState[1];

	if (savedIndex >= animation->numberOfFrames || animation->currentLoopIndex > animation->loopCount)
		return -1;

	if (savedIndex < animation->currentFrameIndex && !reset(info)) {
		info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
		return -1;
	}

	uint_fast32_t lastFrameDuration = info->controlBlock[info->currentIndex].DelayTime;
	if (info->currentIndex < savedIndex) {
		if (info->currentIndex == 0)
			prepareCanvas(pixels, info);
		while (info->currentIndex < savedIndex) {
			DDGifSlurp(info, true, false);
			lastFrameDuration = getBitmap(pixels, animation);
		}
	}

	animation->currentLoopIndex = savedLoop;
	animation->lastFrameRemainder = nativeState[2];
	memcpy(&animation->speedFactor, nativeState + 3, sizeof(animation->speedFactor));

	if (animation->lastFrameRemainder == -1) {
		long long duration = (long long) (lastFrameDuration * animation->speedFactor);
		animation->nextStartTime = getRealTime() + duration;
		return duration;
	}
	return -1;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_restoreSavedState(JNIEnv *env, jclass __unused handleClass, jlong animationPtr, jlongArray state, jobject jbitmap) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return NULL;
	}
	void *pixels;
	if (lockPixels(env, jbitmap, animation, &pixels) != 0) {
		return -1;
	}
	const long long invalidationDelay = restoreSavedState(animation, env, state, pixels);
	unlockPixels(env, jbitmap);
	return (jint) invalidationDelay;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getFrameDuration(__unused JNIEnv *env, jclass __unused handleClass, jlong animationPtr, jint index) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	return (jint) animation->functions->GetDuration(animation, (uint_fast32_t) index);
}

__unused JNIEXPORT jboolean JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_isOpaque(__unused JNIEnv *env, jclass __unused handleClass, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return JNI_FALSE;
	}
	if (animation->isOpaque) {
		return JNI_TRUE;
	}
	return JNI_FALSE;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getWidth(__unused JNIEnv *env, jclass __unused class, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	return (jint) animation->canvasWidth;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getHeight(__unused JNIEnv *env, jclass __unused class, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	return (jint) animation->canvasHeight;
}

__unused JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_getNumberOfFrames(__unused JNIEnv *env, jclass __unused class, jlong animationPtr) {
	Animation *const animation = (Animation *) (intptr_t) animationPtr;
	if (animation == NULL) {
		return 0;
	}
	return (jint) animation->numberOfFrames;
}