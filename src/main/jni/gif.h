#include <jni.h>
#include <time.h>
#include <stdio.h>
#include <limits.h>
#include <stdlib.h>
#include <malloc.h>

#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <limits.h>
#include "giflib/gif_lib.h"

//#include <android/log.h>
//#define  LOG_TAG    "libgif"
//#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

/**
 * some gif files are not strictly follow 89a.
 * DGifSlurp will return read head error or get record type error.
 * but the image still can display. so here should ignore the error.
 */
//#define STRICT_FORMAT_89A


/**
 * Decoding error - no frames
 */
#define D_GIF_ERR_NO_FRAMES     	1000
#define D_GIF_ERR_INVALID_SCR_DIMS 	1001
#define D_GIF_ERR_INVALID_IMG_DIMS 	1002
#define D_GIF_ERR_IMG_NOT_CONFINED 	1003
#define D_GIF_ERR_REWIND_FAILED 	1004

typedef struct
{
	uint8_t blue;
	uint8_t green;
	uint8_t red;
	uint8_t alpha;
} argb;


typedef struct
{
	unsigned int duration;
	int transpIndex;
	unsigned char disposalMethod;
} FrameInfo;

typedef struct GifInfo GifInfo;
typedef int
(*RewindFunc)(GifInfo *);

struct GifInfo
{
	GifFileType* gifFilePtr;
    __time_t lastFrameReaminder;
	__time_t nextStartTime;
	int currentIndex;
    FrameInfo* infos;
	argb* backupPtr;
	long startPos;
	unsigned char* rasterBits;
	char* comment;
	unsigned short loopCount;
	int currentLoop;
	RewindFunc rewindFunction;
	jfloat speedFactor;
};

typedef struct
{
	jobject stream;
	jclass streamCls;
	jmethodID readMID;
	jmethodID resetMID;
	jbyteArray buffer;
} StreamContainer;

typedef struct
{
	long pos;
	jbyteArray buffer;
	jsize arrLen;
} ByteArrayContainer;

typedef struct
{
	long pos;
	jbyte* bytes;
	jlong capacity;
} DirectByteBufferContainer;