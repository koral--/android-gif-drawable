/*****************************************************************************

 GIF construction tools

****************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "gif_lib.h"

#define MAX(x, y)    (((x) > (y)) ? (x) : (y))

/******************************************************************************
 Miscellaneous utility functions                          
******************************************************************************/

/* return smallest bitfield size n will fit in */
int
GifBitSize(int n)
{
    register int i;

    for (i = 1; i <= 8; i++)
        if ((1 << i) >= n)
            break;
    return (i);
}

/******************************************************************************
  Color map object functions                              
******************************************************************************/

/*
 * Allocate a color map of given size; initialize with contents of
 * ColorMap if that pointer is non-NULL.
 */
ColorMapObject *
GifMakeMapObject(int ColorCount, const GifColorType *ColorMap)
{
    ColorMapObject *Object;

    /*** FIXME: Our ColorCount has to be a power of two.  Is it necessary to
     * make the user know that or should we automatically round up instead? */
    if (ColorCount != (1 << GifBitSize(ColorCount))) {
        return ((ColorMapObject *) NULL);
    }
    
    Object = (ColorMapObject *)malloc(sizeof(ColorMapObject));
    if (Object == (ColorMapObject *) NULL) {
        return ((ColorMapObject *) NULL);
    }

    Object->Colors = (GifColorType *)calloc(ColorCount, sizeof(GifColorType));
    if (Object->Colors == (GifColorType *) NULL) {
	free(Object);
        return ((ColorMapObject *) NULL);
    }

    Object->ColorCount = ColorCount;
    Object->BitsPerPixel = GifBitSize(ColorCount);

    if (ColorMap != NULL) {
        memcpy((char *)Object->Colors,
               (char *)ColorMap, ColorCount * sizeof(GifColorType));
    }

    return (Object);
}

/*******************************************************************************
Free a color map object
*******************************************************************************/
void
GifFreeMapObject(ColorMapObject *Object)
{
    if (Object != NULL) {
        (void)free(Object->Colors);
        (void)free(Object);
    }
}

/******************************************************************************
 Extension record functions                              
******************************************************************************/
void
GifFreeExtensions(int *ExtensionBlockCount,
		  ExtensionBlock **ExtensionBlocks)
{
    ExtensionBlock *ep;

    if (*ExtensionBlocks == NULL)
        return;

    for (ep = *ExtensionBlocks;
	 ep < (*ExtensionBlocks + *ExtensionBlockCount); 
	 ep++)
        (void)free((char *)ep->Bytes);
    (void)free((char *)*ExtensionBlocks);
    *ExtensionBlocks = NULL;
    *ExtensionBlockCount = 0;
}

/******************************************************************************
 Image block allocation functions                          
******************************************************************************/

/* Private Function:
 * Frees the last image in the GifFile->SavedImages array
 */
void
FreeLastSavedImage(GifFileType *GifFile)
{
    SavedImage *sp;
    
    if ((GifFile == NULL) || (GifFile->SavedImages == NULL))
        return;

    /* Remove one SavedImage from the GifFile */
    GifFile->ImageCount--;
    sp = &GifFile->SavedImages[GifFile->ImageCount];

    /* Deallocate its Colormap */
    if (sp->ImageDesc.ColorMap != NULL) {
        GifFreeMapObject(sp->ImageDesc.ColorMap);
        sp->ImageDesc.ColorMap = NULL;
    }

    /* Deallocate the image data */
    if (sp->RasterBits != NULL)
        free((char *)sp->RasterBits);

    /* Deallocate any extensions */
    GifFreeExtensions(&sp->ExtensionBlockCount, &sp->ExtensionBlocks);

    /*** FIXME: We could realloc the GifFile->SavedImages structure but is
     * there a point to it? Saves some memory but we'd have to do it every
     * time.  If this is used in GifFreeSavedImages then it would be inefficient
     * (The whole array is going to be deallocated.)  If we just use it when
     * we want to free the last Image it's convenient to do it here.
     */
}

void
GifFreeSavedImages(GifFileType *GifFile)
{
    SavedImage *sp;

    if ((GifFile == NULL) || (GifFile->SavedImages == NULL)) {
        return;
    }
    for (sp = GifFile->SavedImages;
         sp < GifFile->SavedImages + GifFile->ImageCount; sp++) {
        if (sp->ImageDesc.ColorMap != NULL) {
            GifFreeMapObject(sp->ImageDesc.ColorMap);
            sp->ImageDesc.ColorMap = NULL;
        }

        if (sp->RasterBits != NULL)
            free((char *)sp->RasterBits);
	
	GifFreeExtensions(&sp->ExtensionBlockCount, &sp->ExtensionBlocks);
    }
    free((char *)GifFile->SavedImages);
    GifFile->SavedImages = NULL;
}

/* end */
