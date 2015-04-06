#include "gif.h"
#ifdef __arm__
    extern void arm_memset32(uint32_t* dst, uint32_t value, int count);
    extern void memset32_neon(uint32_t* dst, uint32_t value, int count);
    #define MEMSET_ARGB(dst, value, count) memset32_neon(dst, value, (int) count)
#else
    #define MEMSET_ARGB(dst, value, count) memset(dst, value, count * sizeof(argb))
#endif
static void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap) {
    unsigned char *src = info->rasterBits;
    argb *dst = GET_ADDR(bm, info->stride, frame->ImageDesc.Left, frame->ImageDesc.Top);
    uint_fast16_t copyWidth = frame->ImageDesc.Width;
    if (frame->ImageDesc.Left + copyWidth > info->gifFilePtr->SWidth) {
        copyWidth = info->gifFilePtr->SWidth - frame->ImageDesc.Left;
    }

    uint_fast16_t copyHeight = frame->ImageDesc.Height;
    if (frame->ImageDesc.Top + copyHeight > info->gifFilePtr->SHeight) {
        copyHeight = info->gifFilePtr->SHeight - frame->ImageDesc.Top;
    }

    uint_fast16_t x;
    const int_fast16_t transpIndex = info->infos[info->currentIndex].TransparentColor;
    if (transpIndex == NO_TRANSPARENT_COLOR) { //TODO check previous ?
        for (; copyHeight > 0; copyHeight--) {
            MEMSET_ARGB((uint32_t *) dst, UINT32_MAX, copyWidth);
            for (x = copyWidth; x > 0; x--, src++, dst++)
                dst->rgb = cmap->Colors[*src];
            dst += info->stride - copyWidth;
        }
    }
    else {
        for (; copyHeight > 0; copyHeight--) {
            for (x = copyWidth; x > 0; x--, src++, dst++) {
                if (*src != transpIndex) {
                    dst->rgb = cmap->Colors[*src];
                    dst->alpha = 0xFF;
                }
            }
            dst += info->stride - copyWidth;
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
    uint_fast8_t curDisposal = info->infos[idx - 1].DisposalMode;
    bool nextTrans = info->infos[idx].TransparentColor != NO_TRANSPARENT_COLOR;
    unsigned char nextDisposal = info->infos[idx].DisposalMode;

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
                MEMSET_ARGB(dst, 0, copyWidth);
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
    DDGifSlurp(fGIF, info, true);
#ifdef DEBUG
    LOGE("slurpTime %ld %d", getRealTime() - start, info->currentIndex);
#endif
    if (info->currentIndex == 0) {
        if (fGIF->SColorMap && info->infos[0].TransparentColor == NO_TRANSPARENT_COLOR) {
            argb bgColArgb;
            bgColArgb.rgb= fGIF->SColorMap->Colors[fGIF->SBackGroundColor];
            bgColArgb.alpha=0xFF;
            MEMSET_ARGB((uint32_t *)bm, *(uint32_t*)&bgColArgb, info->stride * fGIF->SHeight);
        }
        else
        {
            MEMSET_ARGB((uint32_t *) bm, 0, info->stride * fGIF->SHeight);
        }
    }
    else {
        disposeFrameIfNeeded(bm, info, info->currentIndex);
        if (info->currentIndex >= fGIF->ImageCount - 1) { //TODO move increment here?
            if (info->loopCount > 0)
                info->currentLoop++;
            info->rewindFunction(info);
        }
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