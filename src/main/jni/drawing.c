#include "gif.h"

static void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap) {
    const GifWord width = info->gifFilePtr->SWidth;
    const GifWord height = info->gifFilePtr->SHeight;
    const unsigned char *src = info->rasterBits;
    argb *dst = GET_ADDR(bm, info->stride, frame->ImageDesc.Left, frame->ImageDesc.Top);
    GifWord copyWidth = frame->ImageDesc.Width;
    if (frame->ImageDesc.Left + copyWidth > width) {
        copyWidth = width - frame->ImageDesc.Left;
    }

    GifWord copyHeight = frame->ImageDesc.Height;
    if (frame->ImageDesc.Top + copyHeight > height) {
        copyHeight = height - frame->ImageDesc.Top;
    }

    int x;
    int colorIndex;
    argb *copyDst;
    for (; copyHeight > 0; copyHeight--) {
        copyDst = dst;
        for (x = copyWidth; x > 0; x--, src++, copyDst++) {
            if (*src != info->infos[info->currentIndex].transpIndex) {
                colorIndex = (*src >= cmap->ColorCount) ? 0 : *src;
                GifColorType *col = &cmap->Colors[colorIndex];
                copyDst->alpha = 0xFF;
                copyDst->red = col->Red;
                copyDst->green = col->Green;
                copyDst->blue = col->Blue;
            }
        }
        dst += info->stride;
    }
}

static void drawFrame(argb *bm, GifInfo *info, SavedImage *frame) {
    ColorMapObject *cmap = info->gifFilePtr->SColorMap;

    if (frame->ImageDesc.ColorMap != NULL) {
        // use local color table
        cmap = frame->ImageDesc.ColorMap;
        if (cmap->ColorCount != (1 << cmap->BitsPerPixel))
            cmap = defaultCmap;
    }
    else if (cmap == NULL)
        cmap = defaultCmap;

    blitNormal(bm, info, frame, cmap);
}

// return true if area of 'target' is completely covers area of 'covered'
static bool checkIfCover(const SavedImage *target, const SavedImage *covered) {
    if (target->ImageDesc.Left <= covered->ImageDesc.Left
        && covered->ImageDesc.Left + covered->ImageDesc.Width
           <= target->ImageDesc.Left + target->ImageDesc.Width
        && target->ImageDesc.Top <= covered->ImageDesc.Top
        && covered->ImageDesc.Top + covered->ImageDesc.Height
           <= target->ImageDesc.Top + target->ImageDesc.Height) {
        return true;
    }
    return false;
}

static inline void disposeFrameIfNeeded(argb *bm, GifInfo *info, int idx) {
    GifFileType *fGif = info->gifFilePtr;
    SavedImage *cur = &fGif->SavedImages[idx - 1];
    SavedImage *next = &fGif->SavedImages[idx];
    // We can skip disposal process if next frame is not transparent
    // and completely covers current area
    unsigned char curDisposal = info->infos[idx - 1].disposalMethod;
    bool nextTrans = info->infos[idx].transpIndex != NO_TRANSPARENT_COLOR;
    unsigned char nextDisposal = info->infos[idx].disposalMethod;

    if ((curDisposal == DISPOSE_PREVIOUS || nextDisposal == DISPOSE_PREVIOUS) && info->backupPtr == NULL) {
        info->backupPtr = malloc(info->stride * fGif->SHeight * sizeof(argb));
        if (!info->backupPtr) {
            JNIEnv *env = getEnv();
            if (!env) {
                abort();
            }
            throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
            info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
            return;
        }
    }
    argb *backup = info->backupPtr;
    if (nextTrans || !checkIfCover(next, cur)) {
        if (curDisposal == DISPOSE_BACKGROUND) {// restore to background (under this image) color
            uint32_t *dst = (uint32_t *) GET_ADDR(bm, info->stride, cur->ImageDesc.Left, cur->ImageDesc.Top);
            int copyWidth = cur->ImageDesc.Width;
            if (cur->ImageDesc.Left + copyWidth > fGif->SWidth) {
                copyWidth = fGif->SWidth - cur->ImageDesc.Left;
            }

            int copyHeight = cur->ImageDesc.Height;
            if (cur->ImageDesc.Top + copyHeight > fGif->SHeight) {
                copyHeight = fGif->SHeight - cur->ImageDesc.Top;
            }
            for (; copyHeight > 0; copyHeight--) {
                memset(dst, 0, copyWidth * sizeof(argb));
                dst += info->stride;
            }
        }
        else if (curDisposal == DISPOSE_PREVIOUS && nextDisposal == DISPOSE_PREVIOUS) {// restore to previous
            argb *tmp = bm;
            bm = backup;
            backup = tmp;
        }
    }

    // Save current image if next frame's disposal method == DISPOSE_PREVIOUS
    if (nextDisposal == DISPOSE_PREVIOUS)
        memcpy(backup, bm, info->stride * fGif->SHeight * sizeof(argb));
}

void getBitmap(argb *bm, GifInfo *info) {
    GifFileType *fGIF = info->gifFilePtr;
    if (fGIF->Error == D_GIF_ERR_REWIND_FAILED)
        return;

    if (DDGifSlurp(fGIF, info, true) == GIF_ERROR) {
#ifdef DEBUG
        LOGE("slurp error %d", fGIF->Error);
#endif
        if (!reset(info)) //#80 - reset an animation if broken frame is encountered
            fGIF->Error = D_GIF_ERR_REWIND_FAILED;
        return;
    }
    if (info->currentIndex == 0) {
        if (info->gifFilePtr->SColorMap && info->infos[0].disposalMethod == DISPOSAL_UNSPECIFIED) {
            const GifColorType bgColor = info->gifFilePtr->SColorMap->Colors[fGIF->SBackGroundColor];
            argb *dst = bm;
            int x, y;
            for (y = 0; y < fGIF->SHeight; y++) {
                argb *copyDst = dst;
                for (x = 0; x < fGIF->SWidth; x++, copyDst++) {
                    copyDst->alpha = 0xFF;
                    copyDst->red = bgColor.Red;
                    copyDst->green = bgColor.Green;
                    copyDst->blue = bgColor.Blue;
                }
                dst += info->stride;
            }
        }
        else
            memset(bm, 0, info->stride * fGIF->SHeight * sizeof(argb));
    }
    else {
        disposeFrameIfNeeded(bm, info, info->currentIndex);
    }
    drawFrame(bm, info, &fGIF->SavedImages[info->currentIndex]);
}

ColorMapObject *genDefColorMap(void) {
    ColorMapObject *cmap = GifMakeMapObject(256, NULL);
    if (cmap != NULL) {
        int iColor;
        for (iColor = 0; iColor < 256; iColor++) {
            cmap->Colors[iColor].Red = (GifByteType) iColor;
            cmap->Colors[iColor].Green = (GifByteType) iColor;
            cmap->Colors[iColor].Blue = (GifByteType) iColor;
        }
    }
    return cmap;
}