#include "gif.h"

static void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap) {
    const uint_fast16_t width = info->gifFilePtr->SWidth;
    const uint_fast16_t height = info->gifFilePtr->SHeight;
    unsigned char *src = info->rasterBits;
    argb *dst = GET_ADDR(bm, info->stride, frame->ImageDesc.Left, frame->ImageDesc.Top);
    uint_fast16_t copyWidth = frame->ImageDesc.Width;
    if (frame->ImageDesc.Left + copyWidth > width) {
        copyWidth = width - frame->ImageDesc.Left;
    }

    uint_fast16_t copyHeight = frame->ImageDesc.Height;
    if (frame->ImageDesc.Top + copyHeight > height) {
        copyHeight = height - frame->ImageDesc.Top;
    }

    uint_fast16_t x;
    for (; copyHeight > 0; copyHeight--) {
        for (x = copyWidth; x > 0; x--, src++, dst++) {
            if (*src != info->infos[info->currentIndex].transpIndex) {
                dst->rgb = cmap->Colors[*src];
                dst->alpha = 0xFF;
            }
        }
        dst += info->stride - copyWidth;
    }
}

static void drawFrame(argb *bm, GifInfo *info, SavedImage *frame) {
    ColorMapObject *cmap = info->gifFilePtr->SColorMap;

    if (frame->ImageDesc.ColorMap != NULL) {
        // use local color table
        cmap = frame->ImageDesc.ColorMap;
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
            uint_fast16_t copyWidth = cur->ImageDesc.Width;
            if (cur->ImageDesc.Left + copyWidth > fGif->SWidth) {
                copyWidth = fGif->SWidth - cur->ImageDesc.Left;
            }

            uint_fast16_t copyHeight = cur->ImageDesc.Height;
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

#ifdef DEBUG
    time_t start = getRealTime();
#endif

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
        if (info->gifFilePtr->SColorMap && info->infos[0].transpIndex == NO_TRANSPARENT_COLOR) {
            const GifColorType bgColor = info->gifFilePtr->SColorMap->Colors[fGIF->SBackGroundColor];
            memset(bm, INT_MAX, info->stride * fGIF->SHeight * sizeof(argb));

            argb *dst = bm;
            uint_fast16_t x, y;
            for (y = 0; y < fGIF->SHeight; y++) {
                for (x = 0; x < fGIF->SWidth; x++, dst++)
                    dst->rgb = bgColor;
                dst += info->stride - fGIF->SWidth;
            }
        }
        else
            memset(bm, 0, info->stride * fGIF->SHeight * sizeof(argb));
    }
    else {
        disposeFrameIfNeeded(bm, info, info->currentIndex);
    }
    drawFrame(bm, info, &fGIF->SavedImages[info->currentIndex]);
#ifdef DEBUG
    LOGE("renderTime %ld", getRealTime() - start);
#endif
}

ColorMapObject *genDefColorMap(void) {
    ColorMapObject *cmap = GifMakeMapObject(8, NULL);
    if (cmap != NULL) {
        uint_fast16_t iColor;
        for (iColor = 0; iColor < 256; iColor++) {
            cmap->Colors[iColor].Red = (GifByteType) iColor;
            cmap->Colors[iColor].Green = (GifByteType) iColor;
            cmap->Colors[iColor].Blue = (GifByteType) iColor;
        }
    }
    return cmap;
}