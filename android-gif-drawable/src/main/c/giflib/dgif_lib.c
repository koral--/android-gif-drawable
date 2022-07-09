/******************************************************************************

dgif_lib.c - GIF decoding

The functions here and in egif_lib.c are partitioned carefully so that
if you only require one of read and write capability, only one of these
two modules will be linked.  Preserve this property!

*****************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#ifdef _WIN32
#include <io.h>
#endif /* _WIN32 */

#include "gif_lib.h"
#include "gif_lib_private.h"
#include "../gif.h"

/* compose unsigned little endian value */
#define UNSIGNED_LITTLE_ENDIAN(lo, hi)    ((lo) | ((hi) << 8))

static int DGifGetWord(GifFileType *GifFile, GifWord *Word);

static int DGifSetupDecompress(GifFileType *GifFile);

static int DGifDecompressLine(GifFileType *GifFile, GifPixelType *Line,
                              uint_fast32_t LineLen);

static int DGifGetPrefixChar(GifPrefixType *Prefix, int Code, int ClearCode);

static int DGifDecompressInput(GifFileType *GifFile, int *Code);

static int DGifBufferedInput(GifFileType *GifFile, GifByteType *Buf,
                             GifByteType *NextByte);

/******************************************************************************
GifFileType constructor with user supplied input function (TVT)
******************************************************************************/
GifFileType *
DGifOpen(void *userData, InputFunc readFunc, int *Error) {
    char Buf[GIF_STAMP_LEN + 1];
    GifFileType *GifFile;
    GifFilePrivateType *Private;

    GifFile = (GifFileType *) calloc(1, sizeof(GifFileType));
    if (GifFile == NULL) {
        if (Error != NULL)
            *Error = D_GIF_ERR_NOT_ENOUGH_MEM;
        return NULL;
    }

    //memset(GifFile, '\0', sizeof(GifFileType));

    /* Belt and suspenders, in case the null pointer isn't zero */
    GifFile->SavedImages = NULL;
    GifFile->SColorMap = NULL;

    Private = (GifFilePrivateType *) calloc(1, sizeof(GifFilePrivateType));
    if (!Private) {
        if (Error != NULL)
            *Error = D_GIF_ERR_NOT_ENOUGH_MEM;
        free((char *) GifFile);
        return NULL;
    }

    GifFile->Private = (void *) Private;
//    Private->FileHandle = 0;
//    Private->File = NULL;
//    Private->FileState = FILE_STATE_READ;

    Private->Read = readFunc;    /* TVT */
    GifFile->UserData = userData;    /* TVT */

    /* Lets see if this is a GIF file: */
    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, (unsigned char *) Buf, sizeof("GIFVER") - 1) !=
        GIF_STAMP_LEN) {
        if (Error != NULL)
            *Error = D_GIF_ERR_READ_FAILED;
        free((char *) Private);
        free((char *) GifFile);
        return NULL;
    }

    /* Check for GIF prefix at start of file */
    Buf[GIF_STAMP_LEN] = '\0';
    if (strncmp(GIF_STAMP, Buf, GIF_VERSION_POS) != 0) {
        if (Error != NULL)
            *Error = D_GIF_ERR_NOT_GIF_FILE;
        free((char *) Private);
        free((char *) GifFile);
        return NULL;
    }

    if (DGifGetScreenDesc(GifFile) == GIF_ERROR) {
        free((char *) Private);
        free((char *) GifFile);
        if (Error != NULL)
            *Error = D_GIF_ERR_NO_SCRN_DSCR;
        return NULL;
    }

    GifFile->Error = 0;
    *Error = 0;
    /* What version of GIF? */
    //Private->gif89 = (Buf[GIF_VERSION_POS] == '9');

    return GifFile;
}

/******************************************************************************
This routine should be called before any other DGif calls. Note that
this routine is called automatically from DGif file open routines.
******************************************************************************/
int
DGifGetScreenDesc(GifFileType *GifFile) {
//    bool SortFlag;
    GifByteType Buf[3];
//    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

//    if (!IS_READABLE(Private)) {
//        /* This file was NOT open for reading: */
//        GifFile->Error = D_GIF_ERR_NOT_READABLE;
//        return GIF_ERROR;
//    }

    /* Put the screen descriptor into the file: */
    if (DGifGetWord(GifFile, &GifFile->SWidth) == GIF_ERROR ||
        DGifGetWord(GifFile, &GifFile->SHeight) == GIF_ERROR)
        return GIF_ERROR;

    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, Buf, 3) != 3) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;
        GifFreeMapObject(GifFile->SColorMap);
        GifFile->SColorMap = NULL;
        return GIF_ERROR;
    }
//    GifFile->SColorResolution = (((Buf[0] & 0x70) + 1) >> 4) + 1;
//    SortFlag = (Buf[0] & 0x08) != 0;
    uint_fast8_t BitsPerPixel = (uint_fast8_t) ((Buf[0] & 0x07) + 1);
    GifFile->SBackGroundColor = Buf[1];
//    GifFile->AspectByte = Buf[2];
    if (Buf[0] & 0x80) {    /* Do we have global color map? */
        uint_fast16_t i;

        GifFile->SColorMap = GifMakeMapObject(BitsPerPixel, NULL);
        if (GifFile->SColorMap == NULL) {
            GifFile->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
            return GIF_ERROR;
        }

        /* Get the global color map: */
//	GifFile->SColorMap->SortFlag = SortFlag;
        for (i = 0; i < GifFile->SColorMap->ColorCount; i++) {
            if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, Buf, 3) != 3) {
                GifFreeMapObject(GifFile->SColorMap);
                GifFile->SColorMap = NULL;
                GifFile->Error = D_GIF_ERR_READ_FAILED;
                return GIF_ERROR;
            }
            GifFile->SColorMap->Colors[i].Red = Buf[0];
            GifFile->SColorMap->Colors[i].Green = Buf[1];
            GifFile->SColorMap->Colors[i].Blue = Buf[2];
        }
    } else {
        GifFile->SColorMap = NULL;
    }

    return GIF_OK;
}

/******************************************************************************
This routine should be called before any attempt to read an image.
******************************************************************************/
int
DGifGetRecordType(GifFileType *GifFile, GifRecordType *Type) {
    GifByteType Buf;
//    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

//    if (!IS_READABLE(Private)) {
//        /* This file was NOT open for reading: */
//        GifFile->Error = D_GIF_ERR_NOT_READABLE;
//        return GIF_ERROR;
//    }

    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &Buf, 1) != 1) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;
        return GIF_ERROR;
    }

    switch (Buf) {
        case DESCRIPTOR_INTRODUCER:
            *Type = IMAGE_DESC_RECORD_TYPE;
            break;
        case EXTENSION_INTRODUCER:
            *Type = EXTENSION_RECORD_TYPE;
            break;
        case TERMINATOR_INTRODUCER:
            *Type = TERMINATE_RECORD_TYPE;
            break;
        default:
            *Type = UNDEFINED_RECORD_TYPE;
            GifFile->Error = D_GIF_ERR_WRONG_RECORD;
            return GIF_ERROR;
    }

    return GIF_OK;
}

/******************************************************************************
This routine should be called before any attempt to read an image.
Note it is assumed the Image desc. header has been read.
******************************************************************************/
int
DGifGetImageDesc(GifFileType *GifFile, bool changeImageCount,
                 GifWord originalWidth, GifWord originalHeight) {
    GifByteType Buf[3];
    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

//    if (!IS_READABLE(Private)) {
//        /* This file was NOT open for reading: */
//        GifFile->Error = D_GIF_ERR_NOT_READABLE;
//        return GIF_ERROR;
//    }

    if (DGifGetWord(GifFile, &GifFile->Image.Left) == GIF_ERROR ||
        DGifGetWord(GifFile, &GifFile->Image.Top) == GIF_ERROR ||
        DGifGetWord(GifFile, &GifFile->Image.Width) == GIF_ERROR ||
        DGifGetWord(GifFile, &GifFile->Image.Height) == GIF_ERROR)
        return GIF_ERROR;
    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, Buf, 1) != 1) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;
        GifFreeMapObject(GifFile->Image.ColorMap);
        GifFile->Image.ColorMap = NULL;
        return GIF_ERROR;
    }
    // Error out if any part of the image is outside the logical screen
    if (GifFile->Image.Left + GifFile->Image.Width > originalWidth ||
        GifFile->Image.Top + GifFile->Image.Height > originalHeight) {
        GifFile->Error = D_GIF_ERR_IMG_NOT_CONFINED;
        return GIF_ERROR;
    }
    uint_fast8_t BitsPerPixel = (uint_fast8_t) ((Buf[0] & 0x07) + 1);
    GifFile->Image.Interlace = (Buf[0] & 0x40) ? true : false;

    /* Setup the colormap */
    if (GifFile->Image.ColorMap) {
        GifFreeMapObject(GifFile->Image.ColorMap); //TODO reuse old one?
        GifFile->Image.ColorMap = NULL;
    }
    /* Does this image have local color map? */
    if (Buf[0] & 0x80) {
        GifFile->Image.ColorMap = GifMakeMapObject(BitsPerPixel, NULL);
        if (GifFile->Image.ColorMap == NULL) {
            GifFile->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
            return GIF_ERROR;
        }

        unsigned int i;
        /* Get the image local color map: */
        for (i = 0; i < GifFile->Image.ColorMap->ColorCount; i++) {
            if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, Buf, 3) != 3) {
                GifFreeMapObject(GifFile->Image.ColorMap);
                GifFile->Error = D_GIF_ERR_READ_FAILED;
                GifFile->Image.ColorMap = NULL;
                return GIF_ERROR;
            }
            GifFile->Image.ColorMap->Colors[i].Red = Buf[0];
            GifFile->Image.ColorMap->Colors[i].Green = Buf[1];
            GifFile->Image.ColorMap->Colors[i].Blue = Buf[2];
        }
    }

    /* Reset decompress algorithm parameters. */
    if (DGifSetupDecompress(GifFile) == GIF_ERROR) {
        // skip frame
        return GIF_ERROR;
    }

    if (changeImageCount) {
        SavedImage *new_saved_images = (SavedImage *) reallocarray(GifFile->SavedImages, GifFile->ImageCount + 1, sizeof(SavedImage));
        if (new_saved_images == NULL) {
            GifFile->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
            return GIF_ERROR;
        }
        GifFile->SavedImages = new_saved_images;

        SavedImage *sp = &GifFile->SavedImages[GifFile->ImageCount];
        memcpy(&sp->ImageDesc, &GifFile->Image, sizeof(GifImageDesc));
        if (GifFile->Image.ColorMap != NULL) {
            sp->ImageDesc.ColorMap = GifMakeMapObject(
                    GifFile->Image.ColorMap->BitsPerPixel,
                    GifFile->Image.ColorMap->Colors);
            if (sp->ImageDesc.ColorMap == NULL) {
                GifFile->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
                return GIF_ERROR;
            }
        }
//        sp->RasterBits = (unsigned char *) NULL;
//        sp->ExtensionBlockCount = 0;
//        sp->ExtensionBlocks = (ExtensionBlock *) NULL;
        GifFile->ImageCount++;
    }
    Private->PixelCount = GifFile->Image.Width * GifFile->Image.Height;

    return GIF_OK;
}

/******************************************************************************
Get one full scanned line (Line) of length LineLen from GIF file.
******************************************************************************/
int
DGifGetLine(GifFileType *GifFile, GifPixelType *Line, uint_fast32_t LineLen) {
    GifByteType *Dummy;
    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

//    if (!IS_READABLE(Private)) {
//        /* This file was NOT open for reading: */
//        GifFile->Error = D_GIF_ERR_NOT_READABLE;
//        return GIF_ERROR;
//    }

    if (!LineLen)
        LineLen = GifFile->Image.Width;

    if ((Private->PixelCount -= LineLen) > 0xffff0000UL) {
        GifFile->Error = D_GIF_ERR_DATA_TOO_BIG;
        return GIF_ERROR;
    }

    if (DGifDecompressLine(GifFile, Line, LineLen) == GIF_OK) {
        if (Private->PixelCount == 0) {
            /* We probably won't be called any more, so let's clean up
             * everything before we return: need to flush out all the
             * rest of image until an empty block (size 0)
             * detected. We use GetCodeNext.
         */
            do
                if (DGifGetCodeNext(GifFile, &Dummy) == GIF_ERROR)
                    return GIF_ERROR;
            while (Dummy != NULL);
        }
        return GIF_OK;
    } else
        return GIF_ERROR;
}

/******************************************************************************
Get an extension block (see GIF manual) from GIF file. This routine only
returns the first data block, and DGifGetExtensionNext should be called
after this one until NULL extension is returned.
The Extension should NOT be freed by the user (not dynamically allocated).
Note it is assumed the Extension description header has been read.
******************************************************************************/
int
DGifGetExtension(GifFileType *GifFile, int *ExtCode, GifByteType **Extension) {
    GifByteType Buf;
//    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

//    if (!IS_READABLE(Private)) {
//        /* This file was NOT open for reading: */
//        GifFile->Error = D_GIF_ERR_NOT_READABLE;
//        return GIF_ERROR;
//    }

    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &Buf, 1) != 1) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;
        return GIF_ERROR;
    }
    *ExtCode = Buf;

    return DGifGetExtensionNext(GifFile, Extension);
}

/******************************************************************************
Get a following extension block (see GIF manual) from GIF file. This
routine should be called until NULL Extension is returned.
The Extension should NOT be freed by the user (not dynamically allocated).
******************************************************************************/
int
DGifGetExtensionNext(GifFileType *GifFile, GifByteType **Extension) {
    GifByteType Buf;
    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &Buf, 1) != 1) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;
        return GIF_ERROR;
    }
    if (Buf > 0) {
        *Extension = Private->Buf;    /* Use private unused buffer. */
        (*Extension)[0] = Buf;  /* Pascal strings notation (pos. 0 is len.). */
        /* coverity[tainted_data] */
        if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &((*Extension)[1]), Buf) != Buf) {
            GifFile->Error = D_GIF_ERR_READ_FAILED;
            return GIF_ERROR;
        }
    } else
        *Extension = NULL;

    return GIF_OK;
}

/******************************************************************************
Extract a Graphics Control Block from raw extension data
******************************************************************************/

int DGifExtensionToGCB(const size_t GifExtensionLength,
                       const GifByteType *GifExtension,
                       GraphicsControlBlock *GCB) {
    if (GifExtensionLength != 4) {
        return GIF_ERROR;
    }

    GCB->DisposalMode = (uint_fast8_t) ((GifExtension[0] >> 2) & 0x07);
//    GCB->UserInputFlag = (GifExtension[0] & 0x02) != 0;
    GCB->DelayTime = UNSIGNED_LITTLE_ENDIAN(GifExtension[1], GifExtension[2]);
    if (GifExtension[0] & 0x01)
        GCB->TransparentColor = (int) GifExtension[3];
    else
        GCB->TransparentColor = NO_TRANSPARENT_COLOR;

    return GIF_OK;
}

/******************************************************************************
This routine should be called last, to close the GIF file.
******************************************************************************/
int
DGifCloseFile(GifFileType *GifFile) {
//    GifFilePrivateType *Private;

    if (GifFile == NULL || GifFile->Private == NULL)
        return GIF_ERROR;

    if (GifFile->Image.ColorMap) {
        GifFreeMapObject(GifFile->Image.ColorMap);
        GifFile->Image.ColorMap = NULL;
    }

    if (GifFile->SColorMap) {
        GifFreeMapObject(GifFile->SColorMap);
        GifFile->SColorMap = NULL;
    }

    if (GifFile->SavedImages) {
        GifFreeSavedImages(GifFile);
        GifFile->SavedImages = NULL;
    }

//    GifFreeExtensions(&GifFile->ExtensionBlockCount, &GifFile->ExtensionBlocks);

//    Private = (GifFilePrivateType *) GifFile->Private;

//    if (!IS_READABLE(Private)) {
//        /* This file was NOT open for reading: */
//        GifFile->Error = D_GIF_ERR_NOT_READABLE;
//        return GIF_ERROR;
//    }

//    if (Private->File && (fclose(Private->File) != 0)) {
//        GifFile->Error = D_GIF_ERR_CLOSE_FAILED;
//        return GIF_ERROR;
//    }

    free((char *) GifFile->Private);

    /*
     * Without the #ifndef, we get spurious warnings because Coverity mistakenly
     * thinks the GIF structure is freed on an error return.
     */
#ifndef __COVERITY__
    free(GifFile);
#endif /* __COVERITY__ */

    return GIF_OK;
}

/******************************************************************************
Get 2 bytes (word) from the given file:
******************************************************************************/
static int
DGifGetWord(GifFileType *GifFile, GifWord *Word) {
    unsigned char c[2];

    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, c, 2) != 2) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;
        return GIF_ERROR;
    }

    *Word = (GifWord) UNSIGNED_LITTLE_ENDIAN(c[0], c[1]);
    return GIF_OK;
}

/******************************************************************************
Continue to get the image code in compressed form. This routine should be
called until NULL block is returned.
The block should NOT be freed by the user (not dynamically allocated).
******************************************************************************/
int
DGifGetCodeNext(GifFileType *GifFile, GifByteType **CodeBlock) {
    GifByteType Buf;
    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

    /* coverity[tainted_data_argument] */
    if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &Buf, 1) != 1) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;
        return GIF_ERROR;
    }

    /* coverity[lower_bounds] */
    if (Buf > 0) {
        *CodeBlock = Private->Buf;    /* Use private unused buffer. */
        (*CodeBlock)[0] = Buf;  /* Pascal strings notation (pos. 0 is len.). */
        /* coverity[tainted_data] */
        if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &((*CodeBlock)[1]), Buf) != Buf) {
            GifFile->Error = D_GIF_ERR_READ_FAILED;
            return GIF_ERROR;
        }
    } else {
        *CodeBlock = NULL;
        Private->Buf[0] = 0;    /* Make sure the buffer is empty! */
        Private->PixelCount = 0;    /* And local info. indicate image read. */
    }

    return GIF_OK;
}

/******************************************************************************
Setup the LZ decompression for this image:
******************************************************************************/
static int
DGifSetupDecompress(GifFileType *GifFile) {
    int i, BitsPerPixel;
    GifByteType CodeSize;
    GifPrefixType *Prefix;
    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

    ((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &CodeSize, 1);    /* Read Code size from file. */
    BitsPerPixel = CodeSize;

    /* this can only happen on a severely malformed GIF */
    if (BitsPerPixel > 8) {
        GifFile->Error = D_GIF_ERR_READ_FAILED;    /* somewhat bogus error code */
        return GIF_ERROR;    /* Failed to read Code size. */
    }

    Private->Buf[0] = 0;    /* Input Buffer empty. */
    Private->BitsPerPixel = BitsPerPixel;
    Private->ClearCode = (1 << BitsPerPixel);
    Private->EOFCode = Private->ClearCode + 1;
    Private->RunningCode = Private->EOFCode + 1;
    Private->RunningBits = BitsPerPixel + 1;    /* Number of bits per code. */
    Private->MaxCode1 = 1 << Private->RunningBits;    /* Max. code + 1. */
    Private->StackPtr = 0;    /* No pixels on the pixel stack. */
    Private->LastCode = NO_SUCH_CODE;
    Private->CrntShiftState = 0;    /* No information in CrntShiftDWord. */
    Private->CrntShiftDWord = 0;

    Prefix = Private->Prefix;
    for (i = 0; i <= LZ_MAX_CODE; i++)
        Prefix[i] = NO_SUCH_CODE;

    return GIF_OK;
}

/******************************************************************************
The LZ decompression routine:
This version decompress the given GIF file into Line of length LineLen.
This routine can be called few times (one per scan line, for example), in
order the complete the whole image.
******************************************************************************/
static int
DGifDecompressLine(GifFileType *GifFile, GifPixelType *Line, uint_fast32_t LineLen) {
    uint_fast32_t i = 0, j;
    int CrntCode, EOFCode, ClearCode, CrntPrefix, LastCode, StackPtr;
    GifByteType *Stack, *Suffix;
    GifPrefixType *Prefix;
    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

    StackPtr = Private->StackPtr;
    Prefix = Private->Prefix;
    Suffix = Private->Suffix;
    Stack = Private->Stack;
    EOFCode = Private->EOFCode;
    ClearCode = Private->ClearCode;
    LastCode = Private->LastCode;

    if (StackPtr > LZ_MAX_CODE) {
        return GIF_ERROR;
    }

    if (StackPtr != 0) {
        /* Let pop the stack off before continueing to read the GIF file: */
        while (StackPtr != 0 && i < LineLen)
            Line[i++] = Stack[--StackPtr];
    }

    while (i < LineLen) {    /* Decode LineLen items. */
        if (DGifDecompressInput(GifFile, &CrntCode) == GIF_ERROR)
            return GIF_ERROR;

        if (CrntCode == EOFCode) {
            /* Note however that usually we will not be here as we will stop
             * decoding as soon as we got all the pixel, or EOF code will
             * not be read at all, and DGifGetLine/Pixel clean everything.  */
            GifFile->Error = D_GIF_ERR_EOF_TOO_SOON;
            return GIF_ERROR;
        } else if (CrntCode == ClearCode) {
            /* We need to start over again: */
            for (j = 0; j <= LZ_MAX_CODE; j++)
                Prefix[j] = NO_SUCH_CODE;
            Private->RunningCode = Private->EOFCode + 1;
            Private->RunningBits = Private->BitsPerPixel + 1;
            Private->MaxCode1 = 1 << Private->RunningBits;
            LastCode = Private->LastCode = NO_SUCH_CODE;
        } else {
            /* Its regular code - if in pixel range simply add it to output
             * stream, otherwise trace to codes linked list until the prefix
             * is in pixel range: */
            if (CrntCode < ClearCode) {
                /* This is simple - its pixel scalar, so add it to output: */
                Line[i++] = CrntCode;
            } else {
                /* Its a code to needed to be traced: trace the linked list
                 * until the prefix is a pixel, while pushing the suffix
                 * pixels on our stack. If we done, pop the stack in reverse
                 * (thats what stack is good for!) order to output.  */
                if (Prefix[CrntCode] == NO_SUCH_CODE) {
                    /* Only allowed if CrntCode is exactly the running code:
                     * In that case CrntCode = XXXCode, CrntCode or the
                     * prefix code is last code and the suffix char is
                     * exactly the prefix of last code! */
                    if (CrntCode == Private->RunningCode - 2) {
                        CrntPrefix = LastCode;
                        Suffix[Private->RunningCode - 2] =
                        Stack[StackPtr++] = DGifGetPrefixChar(Prefix,
                                                              LastCode,
                                                              ClearCode);
                    } else {
                        GifFile->Error = D_GIF_ERR_IMAGE_DEFECT;
                        return GIF_ERROR;
                    }
                } else
                    CrntPrefix = CrntCode;

                /* Now (if image is O.K.) we should not get a NO_SUCH_CODE
                 * during the trace. As we might loop forever, in case of
                 * defective image, we use StackPtr as loop counter and stop
                 * before overflowing Stack[]. */
                while (StackPtr < LZ_MAX_CODE &&
                       CrntPrefix > ClearCode && CrntPrefix <= LZ_MAX_CODE) {
                    Stack[StackPtr++] = Suffix[CrntPrefix];
                    CrntPrefix = Prefix[CrntPrefix];
                }
                if (StackPtr >= LZ_MAX_CODE || CrntPrefix > LZ_MAX_CODE) {
                    GifFile->Error = D_GIF_ERR_IMAGE_DEFECT;
                    return GIF_ERROR;
                }
                /* Push the last character on stack: */
                Stack[StackPtr++] = CrntPrefix;

                /* Now lets pop all the stack into output: */
                while (StackPtr != 0 && i < LineLen)
                    Line[i++] = Stack[--StackPtr];
            }
            if (LastCode != NO_SUCH_CODE && Private->RunningCode - 2 < (LZ_MAX_CODE + 1) && Prefix[Private->RunningCode - 2] == NO_SUCH_CODE)
                Prefix[Private->RunningCode - 2] = LastCode;

            if (CrntCode == Private->RunningCode - 2) {
                /* Only allowed if CrntCode is exactly the running code:
                 * In that case CrntCode = XXXCode, CrntCode or the
                 * prefix code is last code and the suffix char is
                 * exactly the prefix of last code! */
                Suffix[Private->RunningCode - 2] =
                        DGifGetPrefixChar(Prefix, LastCode, ClearCode);
            } else {
                Suffix[Private->RunningCode - 2] =
                        DGifGetPrefixChar(Prefix, CrntCode, ClearCode);
            }
        }
        LastCode = CrntCode;
    }

    Private->LastCode = LastCode;
    Private->StackPtr = StackPtr;

    return GIF_OK;
}

/******************************************************************************
Routine to trace the Prefixes linked list until we get a prefix which is
not code, but a pixel value (less than ClearCode). Returns that pixel value.
If image is defective, we might loop here forever, so we limit the loops to
the maximum possible if image O.k. - LZ_MAX_CODE times.
******************************************************************************/
static int
DGifGetPrefixChar(GifPrefixType *Prefix, int Code, int ClearCode) {
    int i = 0;

    while (Code > ClearCode && i++ <= LZ_MAX_CODE) {
        if (Code > LZ_MAX_CODE) {
            return NO_SUCH_CODE;
        }
        Code = Prefix[Code];
    }
    return Code;
}

/******************************************************************************
The LZ decompression input routine:
This routine is responsable for the decompression of the bit stream from
8 bits (bytes) packets, into the real codes.
Returns GIF_OK if read successfully.
******************************************************************************/
static int
DGifDecompressInput(GifFileType *GifFile, int *Code) {
    static const unsigned short CodeMasks[] = {
            0x0000, 0x0001, 0x0003, 0x0007,
            0x000f, 0x001f, 0x003f, 0x007f,
            0x00ff, 0x01ff, 0x03ff, 0x07ff,
            0x0fff
    };

    GifFilePrivateType *Private = (GifFilePrivateType *) GifFile->Private;

    GifByteType NextByte;

    /* The image can't contain more than LZ_BITS per code. */
    if (Private->RunningBits > LZ_BITS) {
        GifFile->Error = D_GIF_ERR_IMAGE_DEFECT;
        return GIF_ERROR;
    }

    while (Private->CrntShiftState < Private->RunningBits) {
        /* Needs to get more bytes from input stream for next code: */
        if (DGifBufferedInput(GifFile, Private->Buf, &NextByte) == GIF_ERROR) {
            return GIF_ERROR;
        }
        Private->CrntShiftDWord |=
                ((unsigned long) NextByte) << Private->CrntShiftState;
        Private->CrntShiftState += 8;
    }
    *Code = Private->CrntShiftDWord & CodeMasks[Private->RunningBits];

    Private->CrntShiftDWord >>= Private->RunningBits;
    Private->CrntShiftState -= Private->RunningBits;

    /* If code cannot fit into RunningBits bits, must raise its size. Note
     * however that codes above 4095 are used for special signaling.
     * If we're using LZ_BITS bits already and we're at the max code, just
     * keep using the table as it is, don't increment Private->RunningCode.
     */
    if (Private->RunningCode < LZ_MAX_CODE + 2 &&
        ++Private->RunningCode > Private->MaxCode1 &&
        Private->RunningBits < LZ_BITS) {
        Private->MaxCode1 <<= 1;
        Private->RunningBits++;
    }
    return GIF_OK;
}

/******************************************************************************
This routines read one GIF data block at a time and buffers it internally
so that the decompression routine could access it.
The routine returns the next byte from its internal buffer (or read next
block in if buffer empty) and returns GIF_OK if succesful.
******************************************************************************/
static int
DGifBufferedInput(GifFileType *GifFile, GifByteType *Buf, GifByteType *NextByte) {
    if (Buf[0] == 0) {
        /* Needs to read the next buffer - this one is empty: */
        if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, Buf, 1) != 1) {
            GifFile->Error = D_GIF_ERR_READ_FAILED;
            return GIF_ERROR;
        }
        /* There shouldn't be any empty data blocks here as the LZW spec
         * says the LZW termination code should come first.  Therefore we
         * shouldn't be inside this routine at that point.
         */
        if (Buf[0] == 0) {
            GifFile->Error = D_GIF_ERR_IMAGE_DEFECT;
            return GIF_ERROR;
        }
        if (((GifFilePrivateType *) GifFile->Private)->Read(GifFile, &Buf[1], Buf[0]) != Buf[0]) {
            GifFile->Error = D_GIF_ERR_READ_FAILED;
            return GIF_ERROR;
        }
        *NextByte = Buf[1];
        Buf[1] = 2;    /* We use now the second place as last char read! */
        Buf[0]--;
    } else {
        *NextByte = Buf[Buf[1]++];
        Buf[0]--;
    }

    return GIF_OK;
}
