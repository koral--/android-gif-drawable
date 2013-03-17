/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <time.h>
#include <stdio.h>
#include <android/log.h>
#include "giflib/gif_lib.h"

#include <stdbool.h>
#include <string.h>
#include <limits.h>
#include <pthread.h>

//#define  LOG_TAG    "libplasma"
//#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)


typedef struct {
	uint8_t blue;
	uint8_t green;
	uint8_t red;
	uint8_t alpha;
} argb;

static ColorMapObject* defaultCmap = NULL;

typedef struct
{
	unsigned int duration;
	unsigned short transpIndex;
	unsigned char disposalMethod;
} FrameInfo;

typedef struct {
	GifFileType* gifFilePtr;
	unsigned long nextStartTime;
	int currentIndex;
	unsigned int lastDrawIndex;
	FrameInfo* infos;
	argb* backupPtr;
	int startPos;
	unsigned char* rasterBits;
	char* comment;
	unsigned short loopCount;
	int currentLoop;
	bool hasAlpha;
	unsigned long tmp;
} GifInfo;

static ColorMapObject* genDefColorMap() {
	ColorMapObject* cmap = GifMakeMapObject(256, NULL);
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

static void cleanUp(GifInfo* info) {
	free(info->backupPtr);
	free(info->infos);
	free(info->rasterBits);
	free(info->comment);
	info->rasterBits=NULL;

	GifFileType* GifFile= info->gifFilePtr;
    if (GifFile->SavedImages != NULL)
    {
    	SavedImage *sp;
		for (sp = GifFile->SavedImages;
			 sp < GifFile->SavedImages + GifFile->ImageCount; sp++) {
			if (sp->ImageDesc.ColorMap != NULL) {
				GifFreeMapObject(sp->ImageDesc.ColorMap);
				sp->ImageDesc.ColorMap = NULL;
			}
		}
		free((char *)GifFile->SavedImages);
		GifFile->SavedImages = NULL;
    }
	DGifCloseFile(info->gifFilePtr);
	free(info);
}

static void PrintGifError(int _GifError) { //TODO texts only in debug
/*	char *Err = GifErrorString(_GifError);
	if (Err != NULL)
		LOGE("\nGIF-LIB error: %s.\n", Err);
	else
		LOGE("\nGIF-LIB undefined error %d.\n", _GifError);*/
}
/**
 * Returns the real time, in ms
 */
static unsigned long getRealTime() {
	struct timespec ts;
	const clockid_t id = CLOCK_MONOTONIC;
	if (id != (clockid_t) -1 && clock_gettime(id, &ts) != -1)
		return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
	return -1;
}

static int readFun(GifFileType* gif, GifByteType* bytes, int size) {
	FILE* file = (FILE*) gif->UserData;
	return fread(bytes, 1, size, file);
}

static int getComment(GifByteType* Bytes, char** cmt)
{
	unsigned int len=(unsigned int)Bytes[0];
	unsigned int offset=*cmt!=NULL?strlen(*cmt):0;
	char* ret= (char*)realloc(*cmt,(len+offset+1)*sizeof(char));
	if (ret!=NULL)
	{
		memcpy(ret+offset,&Bytes[1],len);
		ret[len+offset]=0;
		*cmt=ret;
		return GIF_OK;
	}
	return GIF_ERROR;
}
static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo* info)
{
	if (ExtData==NULL)
		return GIF_OK;
	if (ExtFunction == GRAPHICS_EXT_FUNC_CODE &&ExtData[0] == 4)
	{
		FrameInfo* fi=&info->infos[info->gifFilePtr->ImageCount];
		fi->transpIndex=-1;
		char* b = (char*) ExtData+1;
		unsigned short delay = ((b[2] << 8) | b[1]);
		fi->duration = delay > 1 ? delay * 10 : 100;
		fi->disposalMethod = ((b[0] >> 2) & 7);
		bool has_transparency = ((ExtData[1] & 1) == 1);
		if (has_transparency)
		{
			fi->transpIndex = (unsigned short) b[3];
			if (!info->hasAlpha)
				info->hasAlpha=has_transparency;
		}
	}
	else if (ExtFunction==COMMENT_EXT_FUNC_CODE)
	{
		if (getComment(ExtData,&info->comment)==GIF_ERROR)
		{
			info->gifFilePtr->Error=D_GIF_ERR_NOT_ENOUGH_MEM;
			return GIF_ERROR;//TODO set error, check else
		}
	}
	else if (ExtFunction==APPLICATION_EXT_FUNC_CODE&&ExtData[0] == 11)
	{
		if (strncmp("NETSCAPE2.0",&ExtData[1],11)||strncmp("ANIMEXTS1.0",&ExtData[1],11))
		{
			if (DGifGetExtensionNext(info->gifFilePtr,&ExtData,&ExtFunction)==GIF_ERROR)
				return GIF_ERROR;
			if (ExtFunction==APPLICATION_EXT_FUNC_CODE&&ExtData[0] == 3&&ExtData[1] == 1)
			{
				info->loopCount=(unsigned short)(ExtData[2]+(ExtData[3]<<8));
			}
		}
	}
	return GIF_OK;
}

int DDGifSlurp(GifFileType *GifFile, GifInfo* info, bool shouldDecode) {
	GifRecordType RecordType;
	GifByteType *ExtData;
	int codeSize;
	int ExtFunction;
	size_t ImageSize;

	do {
		if (DGifGetRecordType(GifFile, &RecordType) == GIF_ERROR)
			return (GIF_ERROR);
		switch (RecordType) {
		case IMAGE_DESC_RECORD_TYPE:
			if (DGifGetImageDesc(GifFile,!shouldDecode) == GIF_ERROR)
				return (GIF_ERROR);
			int i = shouldDecode ? info->currentIndex:GifFile->ImageCount - 1;
			SavedImage* sp = &GifFile->SavedImages[i];
			ImageSize = sp->ImageDesc.Width * sp->ImageDesc.Height;

			if (sp->ImageDesc.Width < 1 || sp->ImageDesc.Height < 1
				||ImageSize > (SIZE_MAX / sizeof(GifPixelType))	) {
				return GIF_ERROR;
			}
			if (shouldDecode) {

				sp->RasterBits = info->rasterBits;

				if (sp->ImageDesc.Interlace) {
					int i, j;
					/*
					 * The way an interlaced image should be read -
					 * offsets and jumps...
					 */
					int InterlacedOffset[] = { 0, 4, 2, 1 };
					int InterlacedJumps[] = { 8, 8, 4, 2 };
					/* Need to perform 4 passes on the image */
					for (i = 0; i < 4; i++)
						for (j = InterlacedOffset[i]; j < sp->ImageDesc.Height;
								j += InterlacedJumps[i]) {
							if (DGifGetLine(GifFile,
									sp->RasterBits + j * sp->ImageDesc.Width,
									sp->ImageDesc.Width) == GIF_ERROR)
								return GIF_ERROR;
						}
				} else {
					if (DGifGetLine(GifFile, sp->RasterBits,
							ImageSize)==GIF_ERROR)
						return (GIF_ERROR);
				}
				if (info->currentIndex >= GifFile->ImageCount - 1)
				{
					if (info->loopCount>0)
						info->currentLoop++;
					if (fseek(GifFile->UserData, info->startPos, SEEK_SET) != 0)
						return GIF_ERROR;//TODO set error, check else
				}
				return GIF_OK;
			} else {
				if (DGifGetCode(GifFile, &codeSize, &ExtData) == GIF_ERROR)
					return (GIF_ERROR);
				while (ExtData != NULL ) {
					if (DGifGetCodeNext(GifFile, &ExtData) == GIF_ERROR)
						return (GIF_ERROR);
				}
			}
			break;

		case EXTENSION_RECORD_TYPE:
			if (DGifGetExtension(GifFile, &ExtFunction, &ExtData) == GIF_ERROR)
				return (GIF_ERROR);
			if (!shouldDecode)
			{
				info->infos=(FrameInfo*)realloc(info->infos,(GifFile->ImageCount+1)*sizeof(FrameInfo));
				if (readExtensions(ExtFunction,ExtData,info)==GIF_ERROR)
					return GIF_ERROR;
			}
			while (ExtData != NULL )
			{
				if (DGifGetExtensionNext(GifFile, &ExtData,
						&ExtFunction) == GIF_ERROR)
					return (GIF_ERROR);
				if (!shouldDecode)
				{
					if (readExtensions(ExtFunction,ExtData,info)==GIF_ERROR)
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
	bool ok = true;
	if (shouldDecode) {
		FILE* file = (FILE*) GifFile->UserData;
		ok = (fseek(file, info->startPos, SEEK_SET) == 0);
	}
	if (ok)
		return (GIF_OK);
	else
		return (GIF_ERROR);
}

JNIEXPORT jint JNICALL Java_pl_droidsonroids_gif_GifDrawable_openFile(
		JNIEnv * env, jobject obj, jstring jfname, jintArray dims) {
	const char *fname = (*env)->GetStringUTFChars(env, jfname, 0);
	int Error = 0;
	FILE * file = fopen(fname, "rb");
	if (file == NULL)
		return (jint) NULL ;
	GifFileType *GifFileIn = DGifOpen(file, readFun, &Error);
	int startPos = ftell(file);
	(*env)->ReleaseStringUTFChars(env, jfname, fname);
	if (startPos < 0 ||GifFileIn == NULL) {
		PrintGifError(Error);
		DGifCloseFile(GifFileIn);
		return (jint) NULL;
	}
	int width = GifFileIn->SWidth, height = GifFileIn->SHeight;
	if (width < 1 || height < 1) {
		//LOGE("Invalid dimensions: w=%d h=%d", width, height); //FIXME
		DGifCloseFile(GifFileIn);
		return (jint) NULL ;
	}

	GifInfo* info = (GifInfo*) malloc(sizeof(GifInfo));
	if (info == NULL) {
		//LOGE("malloc failed");//FIXME
		DGifCloseFile(GifFileIn);
		return (jint) NULL;
	}
	info->gifFilePtr = GifFileIn;
	info->startPos = startPos;
	info->currentIndex = -1;
	info->nextStartTime = 0;
	info->comment=NULL;
	info->loopCount=0;
	info->currentLoop=-1;
	info->hasAlpha=false;
	info->rasterBits = (char*) malloc(
			GifFileIn->SHeight * GifFileIn->SWidth * sizeof(GifPixelType));
	info->infos = (FrameInfo*)malloc(sizeof(FrameInfo));
	info->backupPtr = (argb*) malloc(width * height * sizeof(argb));

	if (info->rasterBits == NULL
			|| info->backupPtr == NULL ) {
		//LOGE("Initialization failed"); //FIXME
		cleanUp(info);
		return (jint) NULL ;
	}
	if (DDGifSlurp(GifFileIn, info, false) == GIF_ERROR) {
		PrintGifError(GifFileIn->Error);
	}
	jint *ints = (*env)->GetIntArrayElements(env, dims, 0);
	*ints++ = width;
	*ints++ = height;
	*ints++ = info->hasAlpha;
	*ints=GifFileIn->ImageCount;
	(*env)->ReleaseIntArrayElements(env, dims, ints, 0);

	if (GifFileIn->ImageCount < 1||fseek(file, startPos, SEEK_SET)!=0)
	{
		//LOGI("no valid frames or fseek failed"); //TODO texts
		cleanUp(info);
		return (jint) NULL ;
	}

	return (jint) info;
}

static void packARGB32(argb* pixel, GifByteType alpha, GifByteType red,
		GifByteType green, GifByteType blue) {
	pixel->alpha = alpha;
	pixel->red = red;
	pixel->green = green;
	pixel->blue = blue;
}
static void copyLine(argb* dst, const unsigned char* src,
		const ColorMapObject* cmap, int transparent, int width) {
	for (; width > 0; width--, src++, dst++) {
		if (*src != transparent) {
			GifColorType* col = &cmap->Colors[*src];
			packARGB32(dst, 0xFF, col->Red, col->Green, col->Blue);
		}
	}
}

static argb* getAddr(argb* bm, int width, int left, int top) {
	return bm + top * width + left;
}

static void blitNormal(argb* bm, int width, int height, const SavedImage* frame,
		const ColorMapObject* cmap, int transparent) {
	const unsigned char* src = (unsigned char*) frame->RasterBits;
	argb* dst = getAddr(bm, width, frame->ImageDesc.Left, frame->ImageDesc.Top);
	GifWord copyWidth = frame->ImageDesc.Width;
	if (frame->ImageDesc.Left + copyWidth > width) {
		copyWidth = width - frame->ImageDesc.Left;
	}

	GifWord copyHeight = frame->ImageDesc.Height;
	if (frame->ImageDesc.Top + copyHeight > height) {
		copyHeight = height - frame->ImageDesc.Top;
	}

	int srcPad, dstPad;
	dstPad = width - copyWidth;
	srcPad = frame->ImageDesc.Width - copyWidth;
	for (; copyHeight > 0; copyHeight--) {
		copyLine(dst, src, cmap, transparent, copyWidth);
		src += frame->ImageDesc.Width;
		dst += width;
	}
}

static void fillRect(argb* bm, int bmWidth, int bmHeight, GifWord left,
		GifWord top, GifWord width, GifWord height, argb col) {
	uint32_t* dst = (uint32_t*) getAddr(bm, bmWidth, left, top);
	GifWord copyWidth = width;
	if (left + copyWidth > bmWidth) {
		copyWidth = bmWidth - left;
	}

	GifWord copyHeight = height;
	if (top + copyHeight > bmHeight) {
		copyHeight = bmHeight - top;
	}
	uint32_t* pColor = (uint32_t*) (&col);
	for (; copyHeight > 0; copyHeight--) {
		memset(dst, *pColor, copyWidth*sizeof(argb));
		dst += bmWidth;
	}
}

static void drawFrame(argb* bm, int bmWidth, int bmHeight,
		const SavedImage* frame, const ColorMapObject* cmap, unsigned short transpIndex) {

	if (frame->ImageDesc.ColorMap != NULL) {
		// use local color table
		cmap = frame->ImageDesc.ColorMap;
	}

	if (cmap == NULL || cmap->ColorCount != (1 << cmap->BitsPerPixel)) {
		cmap = defaultCmap;
	}

	blitNormal(bm, bmWidth, bmHeight, frame, cmap, (int)transpIndex);
}

// return true if area of 'target' is completely covers area of 'covered'
static bool checkIfCover(const SavedImage* target, const SavedImage* covered) {
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

static void eraseColor(argb* bm, int w, int h, argb color) {
	uint32_t* pColor = (uint32_t*) (&color);
	memset((uint32_t*) bm, *pColor, w * h*sizeof(argb));
}
static inline void disposeFrameIfNeeded(argb* bm,GifInfo* info, unsigned int idx,
		argb* backup, argb color) {
	GifFileType* fGif=info->gifFilePtr;
	SavedImage* cur=&fGif->SavedImages[idx-1];
	SavedImage* next=&fGif->SavedImages[idx];
	// We can skip disposal process if next frame is not transparent
	// and completely covers current area
	bool curTrans=info->infos[idx-1].transpIndex!=-1;
	int curDisposal=info->infos[idx-1].disposalMethod;
	bool nextTrans=info->infos[idx].transpIndex!=-1;
	int nextDisposal=info->infos[idx].disposalMethod;
	argb* tmp;
	if ((curDisposal == 2 || curDisposal == 3)
			&& (nextTrans || !checkIfCover(next, cur))) {
		switch (curDisposal) {
		// restore to background color
		// -> 'background' means background under this image.
		case 2:

			fillRect(bm, fGif->SWidth, fGif->SHeight, cur->ImageDesc.Left,
					cur->ImageDesc.Top, cur->ImageDesc.Width,
					cur->ImageDesc.Height, color);
			break;

			// restore to previous
		case 3:
			tmp = bm;
			bm = backup;
			backup = tmp;
			break;
		}
	}

	// Save current image if next frame's disposal method == 3
	if (nextDisposal == 3) {
		memcpy(backup, bm, fGif->SWidth * fGif->SHeight * sizeof(argb));
	}
}

static void getBitmap(argb* bm, GifInfo* info, long baseTime, JNIEnv * env) {
	argb* fBackup = info->backupPtr;
	GifFileType* fGIF = info->gifFilePtr;

	argb bgColor;
	if (fGIF->SColorMap != NULL) {
		const GifColorType col = fGIF->SColorMap->Colors[fGIF->SBackGroundColor];
		packARGB32(&bgColor, 0xFF, col.Red, col.Green, col.Blue);
	} else
		packARGB32(&bgColor, 0, 0, 0, 0);
	argb paintingColor;
	int i = info->currentIndex;
	if (DDGifSlurp(fGIF, info, true) == GIF_ERROR)
		return;
	SavedImage* cur = &fGIF->SavedImages[i];

	unsigned short transpIndex=info->infos[i].transpIndex;
	if (i == 0)
	{
		if (transpIndex==-1 && fGIF->SColorMap != NULL) {
			paintingColor = bgColor;
		} else {
			packARGB32(&paintingColor, 0, 0, 0, 0);
		}

		eraseColor(bm, fGIF->SWidth, fGIF->SHeight, paintingColor);
		eraseColor(fBackup, fGIF->SWidth, fGIF->SHeight, paintingColor);
	} else {
		packARGB32(&paintingColor, 0, 0, 0, 0);
		// Dispose previous frame before move to next frame.
		disposeFrameIfNeeded(bm, info, i, fBackup, paintingColor);
	}
	drawFrame(bm, fGIF->SWidth, fGIF->SHeight, cur, fGIF->SColorMap, transpIndex);
}

JNIEXPORT jboolean JNICALL Java_pl_droidsonroids_gif_GifDrawable_renderFrame(
		JNIEnv * env, jobject obj, jintArray array, jobject gifInfo) {
	void* pixels;
	GifInfo* info = (GifInfo*) gifInfo;

	bool needRedraw = false;
	long rt = getRealTime();
//	long diff=rt-info->tmp;
//	double fps=1000.0/diff;
//LOGE("FPS %lf",fps);
//	info->tmp=rt;
	if (rt >= info->nextStartTime&&info->currentLoop<info->loopCount)
	{
		if (++info->currentIndex >= info->gifFilePtr->ImageCount)
			info->currentIndex = 0;
		needRedraw = true;
	}

	if (needRedraw)
	{
		jint *pixels = (*env)->GetIntArrayElements(env, array, 0);

		getBitmap((argb*) pixels, info, rt, env);
		(*env)->ReleaseIntArrayElements(env, array,pixels, 0);
		info->nextStartTime = rt + (unsigned long)(info->infos[info->currentIndex]).duration;
	}

	return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_pl_droidsonroids_gif_GifDrawable_free(JNIEnv * env,
		jobject obj, jobject gifInfo) {
	if (gifInfo == NULL)
		return;
	GifInfo* info = (GifInfo*) gifInfo;
	cleanUp(info);
}

//JNIEXPORT void JNICALL Java_pl_droidsonroids_gif_GifDrawable_init(JNIEnv * env,
//		jobject obj) {
//	if (defaultCmap != NULL)
//		return;
//	defaultCmap = genDefColorMap();
//	if (defaultCmap == NULL)
//		(*env)->ThrowNew(env,
//				(*env)->FindClass(env, "java/lang/RuntimeException"),
//				"Failed to create default color table");
//}
JNIEXPORT jstring JNICALL Java_pl_droidsonroids_gif_GifDrawable_getComment(JNIEnv * env,
		jobject obj, jobject gifInfo)
{
	if (gifInfo==NULL)
		return NULL;
	GifInfo* info=(GifInfo*) gifInfo;
	return (*env)->NewStringUTF(env, info->comment);
}
JNIEXPORT jint JNICALL Java_pl_droidsonroids_gif_GifDrawable_getLoopCount(JNIEnv * env,
		jobject obj, jobject gifInfo)
{
	if (gifInfo==NULL)
		return 0;
	return ((GifInfo*) gifInfo)->loopCount;
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	defaultCmap = genDefColorMap();
//	if (defaultCmap != NULL)
//	{
//		JNIEnv env;
//		(*vm)->GetEnv(vm,(void**)&env, JNI_VERSION_1_2);
//		env->ThrowNew(&env,
//				env->FindClass(&env, "java/lang/RuntimeException"),
//				"Failed to create default color table");
//	}
	return JNI_VERSION_1_2;
}

