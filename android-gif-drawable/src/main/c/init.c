#include "gif.h"

GifInfo *createGifInfo(GifSourceDescriptor *descriptor, JNIEnv *env) {
	if (descriptor->startPos < 0) {
		descriptor->Error = D_GIF_ERR_NOT_READABLE;
	}
	if (descriptor->Error != 0 || descriptor->GifFileIn == NULL) {
		bool readErrno = descriptor->rewindFunc == fileRewind && (descriptor->Error == D_GIF_ERR_NOT_READABLE || descriptor->Error == D_GIF_ERR_READ_FAILED);
		throwGifIOException(descriptor->Error, env, readErrno);
		DGifCloseFile(descriptor->GifFileIn);
		return NULL;
	}

	GifInfo *info = malloc(sizeof(GifInfo));
	if (info == NULL) {
		DGifCloseFile(descriptor->GifFileIn);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL;
	}
	info->controlBlock = malloc(sizeof(GraphicsControlBlock));
	if (info->controlBlock == NULL) {
		DGifCloseFile(descriptor->GifFileIn);
		free(info);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL;
	}
	setGCBDefaults(info->controlBlock);
	info->destructor = NULL;
	info->gifFilePtr = descriptor->GifFileIn;
	info->startPos = descriptor->startPos;
	info->currentIndex = 0;
	info->nextStartTime = 0;
	info->lastFrameRemainder = -1;
	info->comment = NULL;
	info->loopCount = 1;
	info->currentLoop = 0;
	info->speedFactor = 1.0f;
	info->sourceLength = descriptor->sourceLength;

	info->backupPtr = NULL;
	info->rewindFunction = descriptor->rewindFunc;
	info->frameBufferDescriptor = NULL;
	info->isOpaque = false;
	info->sampleSize = 1;

	info->rasterBits = NULL;
	info->rasterSize = 0;
	info->originalHeight = info->gifFilePtr->SHeight;
	info->originalWidth = info->gifFilePtr->SWidth;
	DDGifSlurp(info, false, false);
	info->rasterBits = NULL;
	info->rasterSize = 0;

	if (descriptor->GifFileIn->SWidth < 1 || descriptor->GifFileIn->SHeight < 1) {
		cleanUp(info);
		throwGifIOException(D_GIF_ERR_INVALID_SCR_DIMS, env, false);
		return NULL;
	}
	if (descriptor->GifFileIn->Error == D_GIF_ERR_NOT_ENOUGH_MEM) {
		cleanUp(info);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL;
	}
#if defined(STRICT_FORMAT_89A)
	descriptor->Error = descriptor->GifFileIn->Error;
#endif

	if (descriptor->GifFileIn->ImageCount == 0) {
		descriptor->Error = D_GIF_ERR_NO_FRAMES;
	} else if (descriptor->GifFileIn->Error == D_GIF_ERR_REWIND_FAILED) {
		descriptor->Error = D_GIF_ERR_REWIND_FAILED;
	}
	if (descriptor->Error != 0) {
		cleanUp(info);
		throwGifIOException(descriptor->Error, env, false);
		return NULL;
	}
	return info;
}

void setGCBDefaults(GraphicsControlBlock *gcb) {
	gcb->DelayTime = DEFAULT_FRAME_DURATION_MS;
	gcb->TransparentColor = NO_TRANSPARENT_COLOR;
	gcb->DisposalMode = DISPOSAL_UNSPECIFIED;
}

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_setOptions(__unused JNIEnv *env, jclass __unused class, jlong gifInfo, jchar sampleSize, jboolean isOpaque) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	info->isOpaque = isOpaque == JNI_TRUE;
	info->sampleSize = (uint_fast16_t) sampleSize;
	info->gifFilePtr->SHeight /= info->sampleSize;
	info->gifFilePtr->SWidth /= info->sampleSize;
	if (info->gifFilePtr->SHeight == 0) {
		info->gifFilePtr->SHeight = 1;
	}
	if (info->gifFilePtr->SWidth == 0) {
		info->gifFilePtr->SWidth = 1;
	}

	SavedImage *sp;
	uint_fast32_t i;
	for (i = 0; i < info->gifFilePtr->ImageCount; i++) {
		sp = &info->gifFilePtr->SavedImages[i];
		sp->ImageDesc.Width /= info->sampleSize;
		sp->ImageDesc.Height /= info->sampleSize;
		sp->ImageDesc.Left /= info->sampleSize;
		sp->ImageDesc.Top /= info->sampleSize;
	}
}
