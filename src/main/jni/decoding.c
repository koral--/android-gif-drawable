#include "gif.h"

int DDGifSlurp(GifFileType *GifFile, GifInfo *info, bool shouldDecode) {
    GifRecordType RecordType;
    GifByteType *ExtData;
    int codeSize;
    int ExtFunction;
    do {
        if (DGifGetRecordType(GifFile, &RecordType) == GIF_ERROR)
            return GIF_ERROR;
        switch (RecordType) {
            case IMAGE_DESC_RECORD_TYPE:

                if (DGifGetImageDesc(GifFile, !shouldDecode) == GIF_ERROR)
                    return GIF_ERROR;
                const uint_fast16_t width = GifFile->Image.Width, height = GifFile->Image.Height;
                const int ImageSize = width * height;

                if ((width < 1) || (height < 1) || ImageSize > (SIZE_MAX / sizeof(GifPixelType))) {
                    GifFile->Error = D_GIF_ERR_INVALID_IMG_DIMS;
                    return GIF_ERROR;
                }
                if (width > GifFile->SWidth || height > GifFile->SHeight) {
                    GifFile->Error = D_GIF_ERR_IMG_NOT_CONFINED;
                    return GIF_ERROR;
                }
                if (shouldDecode) {
                    if (GifFile->Image.Interlace) {
                        uint_fast16_t i, j;
                        /*
                         * The way an interlaced image should be read -
                         * offsets and jumps...
                         */
                        uint_fast8_t InterlacedOffset[] = {0, 4, 2, 1};
                        uint_fast8_t InterlacedJumps[] = {8, 8, 4, 2};
                        /* Need to perform 4 passes on the image */
                        for (i = 0; i < 4; i++)
                            for (j = InterlacedOffset[i]; j < GifFile->Image.Height;
                                 j += InterlacedJumps[i]) {
                                if (DGifGetLine(GifFile,
                                                info->rasterBits + j * GifFile->Image.Width,
                                                GifFile->Image.Width) == GIF_ERROR)
                                    return GIF_ERROR;
                            }
                    }
                    else {
                        if (DGifGetLine(GifFile, info->rasterBits, ImageSize) == GIF_ERROR)
                            return GIF_ERROR;
                    }
                    if (info->currentIndex >= GifFile->ImageCount - 1) {
                        if (info->loopCount > 0)
                            info->currentLoop++;
                        if (info->rewindFunction(info) != 0) {
                            info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
                            return GIF_ERROR;
                        }
                    }
                    return GIF_OK;
                }
                else {
                    if (DGifGetCode(GifFile, &codeSize, &ExtData) == GIF_ERROR)
                        return GIF_ERROR;
                    while (ExtData != NULL) {
                        if (DGifGetCodeNext(GifFile, &ExtData) == GIF_ERROR)
                            return GIF_ERROR;
                    }
                }
                break;

            case EXTENSION_RECORD_TYPE:
                if (DGifGetExtension(GifFile, &ExtFunction, &ExtData) == GIF_ERROR)
                    return GIF_ERROR;
                if (!shouldDecode) {
                    FrameInfo *tmpInfos = realloc(info->infos,
                                                  (GifFile->ImageCount + 1) * sizeof(FrameInfo));
                    if (tmpInfos == NULL)
                        return GIF_ERROR;
                    info->infos = tmpInfos;
                    if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR)
                        return GIF_ERROR;
                }
                while (ExtData != NULL) {
                    if (DGifGetExtensionNext(GifFile, &ExtData,
                                             &ExtFunction) == GIF_ERROR)
                        return GIF_ERROR;
                    if (!shouldDecode) {
                        if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR)
                            return GIF_ERROR;
                    }
                }
                break;

            case TERMINATE_RECORD_TYPE:
                break;

            default: /* Should be trapped by DGifGetRecordType */
                break;
        }
    } while (RecordType != TERMINATE_RECORD_TYPE);

    if (shouldDecode) {
        if (info->rewindFunction(info) != 0) {
            info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
            return GIF_ERROR;
        }
    }
    return GIF_OK;
}

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info) {
    if (ExtData == NULL)
        return GIF_OK;
    if (ExtFunction == GRAPHICS_EXT_FUNC_CODE) {
        GraphicsControlBlock GCB;
        if (DGifExtensionToGCB(ExtData[0], ExtData + 1, &GCB) == GIF_ERROR)
            return GIF_ERROR;

        FrameInfo *fi = &info->infos[info->gifFilePtr->ImageCount];
        fi->disposalMethod = (unsigned char) GCB.DisposalMode;
        fi->duration = (uint_fast16_t) (GCB.DelayTime > 1 ? GCB.DelayTime * 10 : 100);
        fi->transpIndex = (uint_fast16_t) GCB.TransparentColor;
    }
    else if (ExtFunction == COMMENT_EXT_FUNC_CODE) {
        if (getComment(ExtData, &info->comment) == GIF_ERROR) {
            info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
            return GIF_ERROR;
        }
    }
    else if (ExtFunction == APPLICATION_EXT_FUNC_CODE) {
        char const *string = (char const *) (ExtData + 1);
        if (strncmp("NETSCAPE2.0", string, ExtData[0]) == 0
            || strncmp("ANIMEXTS1.0", string, ExtData[0]) == 0) {
            if (DGifGetExtensionNext(info->gifFilePtr, &ExtData,
                                     &ExtFunction) == GIF_ERROR)
                return GIF_ERROR;
            if (ExtData[0] == 3
                && ExtData[1] == 1) {
                info->loopCount = (uint8_t) (ExtData[2]
                                             + (ExtData[3] << 8));
            }
        }
    }
    return GIF_OK;
}

static int getComment(GifByteType *Bytes, char **cmt) {
    unsigned int len = (unsigned int) Bytes[0];
    size_t offset = *cmt != NULL ? strlen(*cmt) : 0;
    char *ret = realloc(*cmt, (len + offset + 1) * sizeof(char));
    if (ret != NULL) {
        memcpy(ret + offset, &Bytes[1], len);
        ret[len + offset] = 0;
        *cmt = ret;
        return GIF_OK;
    }
    return GIF_ERROR;
}
