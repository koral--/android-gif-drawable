#include "metadata.h"

char *getGifComment(Animation *animation) {
	const GifInfo *info = animation->data;
	return info->comment;
}

uint_fast32_t getGifDuration(Animation *animation, uint_fast32_t frameIndex) {
	const GifInfo *info = animation->data;
	return info->controlBlock[frameIndex].DelayTime;
}

size_t getGifMetadataByteCount(Animation *animation) {
	size_t size = sizeof(GifInfo) + sizeof(GifFileType);
	size += animation->numberOfFrames * (sizeof(GraphicsControlBlock) + sizeof(SavedImage));
	char *const comment = getGifComment(animation);
	if (comment != NULL) {
		size += strlen(comment);
	}
	return size;
}

size_t getGifAllocationByteCount(Animation *animation) {
	GifInfo *info = animation->data;
	size_t size = info->rasterSize;
	if (size == 0) {
		uint_fast32_t rasterSize = 0;
		uint_fast32_t i;
		for (i = 0; i < info->gifFilePtr->ImageCount; i++) {
			GifImageDesc imageDesc = info->gifFilePtr->SavedImages[i].ImageDesc;
			int_fast32_t widthOverflow = imageDesc.Width - info->originalWidth;
			int_fast32_t heightOverflow = imageDesc.Height - info->originalHeight;
			uint_fast32_t newRasterSize = imageDesc.Width * imageDesc.Height;
			if (newRasterSize > rasterSize || widthOverflow > 0 || heightOverflow > 0) {
				rasterSize = newRasterSize;
			}
		}
		size = rasterSize;
	}
	size *= sizeof(GifPixelType);

	bool isBackupBitmapUsed = info->backupPtr != NULL;
	if (!isBackupBitmapUsed) {
		uint_fast32_t i;
		for (i = 1; i < info->gifFilePtr->ImageCount; i++) {
			if (info->controlBlock[i].DisposalMode == DISPOSE_PREVIOUS) {
				isBackupBitmapUsed = true;
				break;
			}
		}
	}

	if (isBackupBitmapUsed) {
		int32_t stride = animation->stride > 0 ? animation->stride : (int32_t) info->gifFilePtr->SWidth;
		size += stride * info->gifFilePtr->SHeight * sizeof(argb);
	}

	return size;
}

int getGifErrorCode(Animation *animation) {
	GifInfo *info = animation->data;
	return info->gifFilePtr->Error;
}