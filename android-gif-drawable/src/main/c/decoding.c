#include "gif.h"
#define COMMENT_LENGTH_MAX 2048

static bool updateGCB(GifInfo *info, uint_fast32_t *lastAllocatedGCBIndex) {
	if (*lastAllocatedGCBIndex < info->gifFilePtr->ImageCount) {
		GraphicsControlBlock *tmpInfos = reallocarray(info->controlBlock, info->gifFilePtr->ImageCount + 1, sizeof(GraphicsControlBlock));
		if (tmpInfos == NULL) {
			info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
			return false;
		}
		*lastAllocatedGCBIndex = info->gifFilePtr->ImageCount;
		info->controlBlock = tmpInfos;
		setGCBDefaults(&info->controlBlock[info->gifFilePtr->ImageCount]);
	}
	return true;
}

void DDGifSlurp(GifInfo *info, bool decode, bool exitAfterFrame) {
	GifRecordType RecordType;
	GifByteType *ExtData;
	int ExtFunction;
	GifFileType *gifFilePtr;
	gifFilePtr = info->gifFilePtr;
	uint_fast32_t lastAllocatedGCBIndex = 0;
	do {
		if (DGifGetRecordType(gifFilePtr, &RecordType) == GIF_ERROR && gifFilePtr->Error != D_GIF_ERR_WRONG_RECORD) {
			break;
		}
		bool isInitialPass = !decode && !exitAfterFrame;
		if (RecordType == IMAGE_DESC_RECORD_TYPE) {
			if (DGifGetImageDesc(gifFilePtr, isInitialPass, info->originalWidth,
								 info->originalHeight) == GIF_ERROR) {
				break;
			}

			if (isInitialPass) {
				if (!updateGCB(info, &lastAllocatedGCBIndex)) {
					break;
				}
			}

			if (decode) {
				const uint_fast32_t newRasterSize = gifFilePtr->Image.Width * gifFilePtr->Image.Height;
				if (newRasterSize == 0) {
					free(info->rasterBits);
					info->rasterBits = NULL;
					info->rasterSize = newRasterSize;
					break;
				}
				const int_fast32_t widthOverflow = gifFilePtr->Image.Width - info->originalWidth;
				const int_fast32_t heightOverflow = gifFilePtr->Image.Height - info->originalHeight;
				if (newRasterSize > info->rasterSize || widthOverflow > 0 || heightOverflow > 0) {
						void *tmpRasterBits = reallocarray(info->rasterBits, newRasterSize, sizeof(GifPixelType));
					if (tmpRasterBits == NULL) {
						gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
						break;
					}
					info->rasterBits = tmpRasterBits;
					info->rasterSize = newRasterSize;
				}
				if (gifFilePtr->Image.Interlace) {
					uint_fast16_t i, j;
					/*
					 * The way an interlaced image should be read -
					 * offsets and jumps...
					 */
					uint_fast8_t InterlacedOffset[] = {0, 4, 2, 1};
					uint_fast8_t InterlacedJumps[] = {8, 8, 4, 2};
					/* Need to perform 4 passes on the image */
					for (i = 0; i < 4; i++)
							for (j = InterlacedOffset[i]; j < gifFilePtr->Image.Height; j += InterlacedJumps[i]) {
								if (DGifGetLine(gifFilePtr, info->rasterBits + j * gifFilePtr->Image.Width, gifFilePtr->Image.Width) == GIF_ERROR)
								break;
						}
				} else {
						if (DGifGetLine(gifFilePtr, info->rasterBits, gifFilePtr->Image.Width * gifFilePtr->Image.Height) == GIF_ERROR) {
						break;
					}
				}

				if (info->sampleSize > 1) {
					unsigned char *dst = info->rasterBits;
					unsigned char *src = info->rasterBits;
					unsigned char *const srcEndImage = info->rasterBits + gifFilePtr->Image.Width * gifFilePtr->Image.Height;
					do {
						unsigned char *srcNextLineStart = src + gifFilePtr->Image.Width * info->sampleSize;
						unsigned char *const srcEndLine = src + gifFilePtr->Image.Width;
						unsigned char *dstEndLine = dst + gifFilePtr->Image.Width / info->sampleSize;
						do {
							*dst = *src;
							dst++;
							src += info->sampleSize;
						} while (src < srcEndLine);
						dst = dstEndLine;
						src = srcNextLineStart;
					} while (src < srcEndImage);
				}
				return;
			} else {
				do {
					if (DGifGetCodeNext(gifFilePtr, &ExtData) == GIF_ERROR) {
						break;
					}
				} while (ExtData != NULL);
				if (exitAfterFrame) {
					return;
				}
			}
		} else if (RecordType == EXTENSION_RECORD_TYPE) {
			if (DGifGetExtension(gifFilePtr, &ExtFunction, &ExtData) == GIF_ERROR) {
				break;
			}
			if (isInitialPass) {
				updateGCB(info, &lastAllocatedGCBIndex);
				if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR) {
					break;
				}
			}
			while (ExtData != NULL) {
				if (DGifGetExtensionNext(gifFilePtr, &ExtData) == GIF_ERROR) {
					break;
				}
				if (isInitialPass && readExtensions(ExtFunction, ExtData, info) == GIF_ERROR) {
					break;
				}
			}
		}
	} while (RecordType != TERMINATE_RECORD_TYPE);

	info->rewindFunction(info);
}

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info) {
	if (ExtData == NULL) {
		return GIF_OK;
	}
	if (ExtFunction == GRAPHICS_EXT_FUNC_CODE) {
		GraphicsControlBlock *GCB = &info->controlBlock[info->gifFilePtr->ImageCount];
		if (DGifExtensionToGCB(ExtData[0], ExtData + 1, GCB) == GIF_ERROR) {
			return GIF_ERROR;
		}

		GCB->DelayTime = GCB->DelayTime > 1 ? GCB->DelayTime * 10 : DEFAULT_FRAME_DURATION_MS;
	} else if (ExtFunction == COMMENT_EXT_FUNC_CODE) {
		if (getComment(ExtData, info) == GIF_ERROR) {
			info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
			return GIF_ERROR;
		}
	} else if (ExtFunction == APPLICATION_EXT_FUNC_CODE) {
		char const *string = (char const *) (ExtData + 1);
		if (strncmp("NETSCAPE2.0", string, ExtData[0]) == 0
			|| strncmp("ANIMEXTS1.0", string, ExtData[0]) == 0) {
			if (DGifGetExtensionNext(info->gifFilePtr, &ExtData) == GIF_ERROR) {
				return GIF_ERROR;
			}
			if (ExtData && ExtData[0] == 3 && ExtData[1] == 1) {
				uint_fast16_t loopCount = (uint_fast16_t) (ExtData[2] + (ExtData[3] << 8));
				if (loopCount) {
					loopCount++;
				}
				info->loopCount = loopCount;
			}
		}
	}
	return GIF_OK;
}

static int getComment(GifByteType *Bytes, GifInfo *info) {
	unsigned int length = (unsigned int) Bytes[0];
	size_t offset = info->comment != NULL ? strlen(info->comment) : 0;
	unsigned int newLength = length + offset + 1;
	if (newLength > COMMENT_LENGTH_MAX) {
		return GIF_OK;
	}
	char *ret = reallocarray(info->comment, newLength, sizeof(char));
	if (ret != NULL) {
		memcpy(ret + offset, &Bytes[1], length);
		ret[length + offset] = 0;
		info->comment = ret;
		return GIF_OK;
	}
	info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
	return GIF_ERROR;
}
