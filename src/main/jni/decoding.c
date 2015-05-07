#include "gif.h"

void DDGifSlurp(GifInfo *info, bool shouldDecode) {
    GifRecordType RecordType;
    GifByteType *ExtData;
    int codeSize;
    int ExtFunction;
    do {
        if (DGifGetRecordType(info->gifFilePtr, &RecordType) == GIF_ERROR)
            return;
        switch (RecordType) {
            case IMAGE_DESC_RECORD_TYPE:

                if (DGifGetImageDesc(info->gifFilePtr, !shouldDecode) == GIF_ERROR)
                    return;

                if (info->gifFilePtr->Image.Left + info->gifFilePtr->Image.Width > info->gifFilePtr->SWidth) {
                    info->gifFilePtr->Image.Width = info->gifFilePtr->SWidth - info->gifFilePtr->Image.Left;
                }

                if (info->gifFilePtr->Image.Top + info->gifFilePtr->Image.Height > info->gifFilePtr->SHeight) {
                    info->gifFilePtr->Image.Height = info->gifFilePtr->SHeight - info->gifFilePtr->Image.Top;
                }

                if (shouldDecode) {
                    if (info->gifFilePtr->Image.Interlace) {
                        uint_fast16_t i, j;
                        /*
                         * The way an interlaced image should be read -
                         * offsets and jumps...
                         */
                        uint_fast8_t InterlacedOffset[] = {0, 4, 2, 1};
                        uint_fast8_t InterlacedJumps[] = {8, 8, 4, 2};
                        /* Need to perform 4 passes on the image */
                        for (i = 0; i < 4; i++)
                            for (j = InterlacedOffset[i]; j < info->gifFilePtr->Image.Height; j += InterlacedJumps[i]) {
                                if (DGifGetLine(info->gifFilePtr, info->rasterBits + j * info->gifFilePtr->Image.Width,
                                                info->gifFilePtr->Image.Width) == GIF_ERROR)
                                    return;
                            }
                    }
                    else {
                        if (DGifGetLine(
                                info->gifFilePtr, info->rasterBits, info->gifFilePtr->Image.Width * info->gifFilePtr->Image.Height) == GIF_ERROR)
                            return;
                    }
                    return;
                }
                else {
                    if (DGifGetCode(info->gifFilePtr, &codeSize, &ExtData) == GIF_ERROR)
                        return;
                    while (ExtData != NULL) {
                        if (DGifGetCodeNext(info->gifFilePtr, &ExtData) == GIF_ERROR)
                            return;
                    }
                }
                break;

            case EXTENSION_RECORD_TYPE:
                if (DGifGetExtension(info->gifFilePtr, &ExtFunction, &ExtData) == GIF_ERROR)
                    return;
                if (!shouldDecode) {
                    GraphicsControlBlock *tmpInfos = realloc(info->controlBlock,
                                                             (info->gifFilePtr->ImageCount + 1) * sizeof(GraphicsControlBlock));
                    if (tmpInfos == NULL) {
                        info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
                        return;
                    }
                    info->controlBlock = tmpInfos;
                    if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR)
                        return;
                }
                while (ExtData != NULL) {
                    if (DGifGetExtensionNext(info->gifFilePtr, &ExtData,
                                             &ExtFunction) == GIF_ERROR)
                        return;
                    if (!shouldDecode) {
                        if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR)
                            return;
                    }
                }
                break;

            case TERMINATE_RECORD_TYPE:
                break;

            default: /* Should be trapped by DGifGetRecordType */
                break;
        }
    } while (RecordType != TERMINATE_RECORD_TYPE);

    info->rewindFunction(info);
}

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info) {
    if (ExtData == NULL)
        return GIF_OK;
    if (ExtFunction == GRAPHICS_EXT_FUNC_CODE) {
        GraphicsControlBlock *GCB = &info->controlBlock[info->gifFilePtr->ImageCount];
        if (DGifExtensionToGCB(ExtData[0], ExtData + 1, GCB) == GIF_ERROR)
            return GIF_ERROR;

        GCB->DelayTime = GCB->DelayTime > 1 ? GCB->DelayTime * 10 : 100;
    }
    else if (ExtFunction == COMMENT_EXT_FUNC_CODE) {
        if (getComment(ExtData, info) == GIF_ERROR) {
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
            if (ExtData[0] == 3 && ExtData[1] == 1) {
                info->loopCount = (uint_fast16_t) (ExtData[2] + (ExtData[3] << 8));
            }
        }
    }
    return GIF_OK;
}

static int getComment(GifByteType *Bytes, GifInfo *info) {
    unsigned int len = (unsigned int) Bytes[0];
    size_t offset = info->comment != NULL ? strlen(info->comment) : 0;
    char *ret = realloc(info->comment, (len + offset + 1) * sizeof(char));
    if (ret != NULL) {
        memcpy(ret + offset, &Bytes[1], len);
        ret[len + offset] = 0;
        info->comment = ret;
        return GIF_OK;
    }
    info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
    return GIF_ERROR;
}
