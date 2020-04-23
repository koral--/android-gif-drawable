#include "gif.h"

#if defined (__arm__)
extern void arm_memset32(uint32_t* dst, uint32_t value, int count);
#define MEMSET_ARGB(dst, value, count) arm_memset32(dst, value, (int) count)
#else
#define MEMSET_ARGB(dst, value, count) memset(dst, value, (count) * sizeof(argb))
#endif

static inline void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap) {
	unsigned char *src = info->rasterBits;
	if (src == NULL) {
		return;
	}
	argb *dst = GET_ADDR(bm, info->stride, frame->ImageDesc.Left, frame->ImageDesc.Top);

	uint_fast16_t x, y = frame->ImageDesc.Height;
	const int_fast16_t transpIndex = info->controlBlock[info->currentIndex].TransparentColor;
	const GifWord frameWidth = frame->ImageDesc.Width;
	const GifWord padding = info->stride - frameWidth;
	if (info->rasterSize < frame->ImageDesc.Height * frame->ImageDesc.Width) {
		return;
	}
	if (info->isOpaque) {
		if (transpIndex == NO_TRANSPARENT_COLOR) {
			for (; y > 0; y--) {
				for (x = frameWidth; x > 0; x--, src++, dst++) {
					dst->rgb = cmap->Colors[*src];
				}
				dst += padding;
			}
		} else {
			for (; y > 0; y--) {
				for (x = frameWidth; x > 0; x--, src++, dst++) {
					if (*src != transpIndex) {
						dst->rgb = cmap->Colors[*src];
					}
				}
				dst += padding;
			}
		}
	} else {
		if (transpIndex == NO_TRANSPARENT_COLOR) {
			for (; y > 0; y--) {
				MEMSET_ARGB((uint32_t *) dst, UINT_MAX, frameWidth);
				for (x = frameWidth; x > 0; x--, src++, dst++) {
					dst->rgb = cmap->Colors[*src];
				}
				dst += padding;
			}
		} else {
			for (; y > 0; y--) {
				for (x = frameWidth; x > 0; x--, src++, dst++) {
					if (*src != transpIndex) {
						dst->rgb = cmap->Colors[*src];
						dst->alpha = 0xFF;
					}
				}
				dst += padding;
			}
		}
	}
}

static void drawFrame(argb *bm, GifInfo *info, SavedImage *frame) {
	ColorMapObject *cmap;
	if (frame->ImageDesc.ColorMap != NULL)
		cmap = frame->ImageDesc.ColorMap;// use local color table
	else if (info->gifFilePtr->SColorMap != NULL)
		cmap = info->gifFilePtr->SColorMap;
	else
		cmap = getDefColorMap();

	blitNormal(bm, info, frame, cmap);
}

// return true if area of 'target' is completely covers area of 'covered'
static bool checkIfCover(const SavedImage *target, const SavedImage *covered) {
	return target->ImageDesc.Left <= covered->ImageDesc.Left
	       && covered->ImageDesc.Left + covered->ImageDesc.Width <= target->ImageDesc.Left + target->ImageDesc.Width
	       && target->ImageDesc.Top <= covered->ImageDesc.Top
	       && covered->ImageDesc.Top + covered->ImageDesc.Height
	          <= target->ImageDesc.Top + target->ImageDesc.Height;
}

static inline void disposeFrameIfNeeded(argb *bm, GifInfo *info) {
	GifFileType *fGif = info->gifFilePtr;
	SavedImage *cur = &fGif->SavedImages[info->currentIndex - 1];
	SavedImage *next = &fGif->SavedImages[info->currentIndex];
	// We can skip disposal process if next frame is not transparent
	// and completely covers current area
	uint_fast8_t curDisposal = info->controlBlock[info->currentIndex - 1].DisposalMode;
	bool nextTrans = info->controlBlock[info->currentIndex].TransparentColor != NO_TRANSPARENT_COLOR;
	uint_fast8_t nextDisposal = info->controlBlock[info->currentIndex].DisposalMode;

	if ((curDisposal == DISPOSE_PREVIOUS || nextDisposal == DISPOSE_PREVIOUS) && info->backupPtr == NULL) {
		info->backupPtr = calloc(info->stride * fGif->SHeight, sizeof(argb));
		if (!info->backupPtr) {
			info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM; //TODO throw OOME
			return;
		}
	}

	if (nextTrans || !checkIfCover(next, cur)) {
		if (curDisposal == DISPOSE_BACKGROUND || (info->currentIndex == 1 && curDisposal == DISPOSE_PREVIOUS)) {// restore to background (under this image) color
			uint32_t *dst = (uint32_t *) GET_ADDR(bm, info->stride, cur->ImageDesc.Left, cur->ImageDesc.Top);
			uint_fast16_t copyHeight = cur->ImageDesc.Height;
			for (; copyHeight > 0; copyHeight--) {
				MEMSET_ARGB(dst, 0, cur->ImageDesc.Width);
				dst += info->stride;
			}
		} else if (curDisposal == DISPOSE_PREVIOUS) {// restore to previous
			memcpy(bm, info->backupPtr, info->stride * fGif->SHeight * sizeof(argb));
		}
	}

	// Save current image if next frame's disposal method == DISPOSE_PREVIOUS
	if (nextDisposal == DISPOSE_PREVIOUS) {
		memcpy(info->backupPtr, bm, info->stride * fGif->SHeight * sizeof(argb));
	}
}

void prepareCanvas(const argb *bm, GifInfo *info) {
	GifFileType *const gifFilePtr = info->gifFilePtr;
	if (gifFilePtr->SColorMap && info->controlBlock->TransparentColor == NO_TRANSPARENT_COLOR) {
		const GifColorType backgroundRGB = gifFilePtr->SColorMap->Colors[gifFilePtr->SBackGroundColor];
		argb *pixel;
		for (pixel = (argb *) bm; pixel < bm + (info->stride * info->gifFilePtr->SHeight); pixel++) {
			pixel->alpha = 0xFF;
			pixel->rgb = backgroundRGB;
		}
	} else {
		MEMSET_ARGB((uint32_t *) bm, 0, info->stride * gifFilePtr->SHeight);
	}
}

void drawNextBitmap(argb *bm, GifInfo *info) {
	if (info->currentIndex > 0) {
		disposeFrameIfNeeded(bm, info);
	}
	drawFrame(bm, info, info->gifFilePtr->SavedImages + info->currentIndex);
}

uint_fast32_t getFrameDuration(GifInfo *info) {
	uint_fast32_t frameDuration = info->controlBlock[info->currentIndex].DelayTime;
	if (++info->currentIndex >= info->gifFilePtr->ImageCount) {
		if (info->loopCount == 0 || info->currentLoop + 1 < info->loopCount) {
			if (info->rewindFunction(info) != 0)
				return 0;
			else if (info->loopCount > 0)
				info->currentLoop++;
			info->currentIndex = 0;
		} else {
			info->currentLoop++;
			--info->currentIndex;
			frameDuration = 0;
		}
	}
	return frameDuration;
}

uint_fast32_t getBitmap(argb *bm, GifInfo *info) {
	drawNextBitmap(bm, info);
	return getFrameDuration(info);
}
