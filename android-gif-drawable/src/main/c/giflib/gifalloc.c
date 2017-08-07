/*****************************************************************************

 GIF construction tools

****************************************************************************/

#include <stdlib.h>
#include <string.h>
#include "gif_lib.h"

//#define MAX(x, y)    (((x) > (y)) ? (x) : (y))

/******************************************************************************
 Miscellaneous utility functions                          
******************************************************************************/

/* return smallest bitfield size n will fit in */
//int
//GifBitSize(int n)
//{
//    register int i;
//
//    for (i = 1; i <= 8; i++)
//        if ((1 << i) >= n)
//            break;
//    return (i);
//}

/******************************************************************************
  Color map object functions                              
******************************************************************************/

/*
 * Allocate a color map of given size; initialize with contents of
 * ColorMap if that pointer is non-NULL.
 */
ColorMapObject *
GifMakeMapObject(uint_fast8_t BitsPerPixel, const GifColorType *ColorMap) {
	ColorMapObject *Object;

	/*** Our ColorCount has to be a power of two.  Is it necessary to
	 * make the user know that or should we automatically round up instead? */
//    if (ColorCount != (1 << GifBitSize(ColorCount))) {
//        return ((ColorMapObject *) NULL);
//    }

	Object = (ColorMapObject *) malloc(sizeof(ColorMapObject));
	if (Object == (ColorMapObject *) NULL) {
		return ((ColorMapObject *) NULL);
	}

	Object->Colors = (GifColorType *) calloc(256, sizeof(GifColorType));
	if (Object->Colors == (GifColorType *) NULL) {
		free(Object);
		return ((ColorMapObject *) NULL);
	}

	Object->ColorCount = (uint_fast16_t) (1 << BitsPerPixel);
	Object->BitsPerPixel = BitsPerPixel;

	if (ColorMap != NULL) {
		memcpy((char *) Object->Colors,
		       (char *) ColorMap, Object->ColorCount * sizeof(GifColorType));
	}

	return (Object);
}

/*******************************************************************************
Free a color map object
*******************************************************************************/
void
GifFreeMapObject(ColorMapObject *Object) {
	if (Object != NULL) {
		free(Object->Colors);
		free(Object);
	}
}

/******************************************************************************
 Image block allocation functions                          
******************************************************************************/

void
GifFreeSavedImages(GifFileType *GifFile) {
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

//        if (sp->RasterBits != NULL)
//            free((char *)sp->RasterBits);
//
//	GifFreeExtensions(&sp->ExtensionBlockCount, &sp->ExtensionBlocks);
	}
	free((char *) GifFile->SavedImages);
	GifFile->SavedImages = NULL;
}

/* end */
