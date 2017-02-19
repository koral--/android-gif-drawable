#pragma once

#include "animation/animation.h"
#include <jni.h>

int lockPixels(JNIEnv *env, jobject jbitmap, Animation *animation, void **pixels);

void unlockPixels(JNIEnv *env, jobject jbitmap);

uint_fast32_t renderGifBitmap(Animation *animation, void *pixels, uint_fast32_t frameIndex);