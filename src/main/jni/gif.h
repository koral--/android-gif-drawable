#include <unistd.h>
#include <jni.h>
#include <android/bitmap.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <time.h>
#include <stdio.h>
#include <limits.h>
#include <stdlib.h>
#include <malloc.h>

#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <limits.h>
#include <sys/cdefs.h>
#include "giflib/gif_lib.h"

//#include <android/log.h>
//#define  LOG_TAG    "libgif"
//#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

/**
 * Some gif files are not strictly follow 89a.
 * DGifSlurp will return read head error or get record type error.
 * but the image still can display. so here should ignore the error.
 */
//#define STRICT_FORMAT_89A

/**
 * Decoding error - no frames
 */
#define D_GIF_ERR_NO_FRAMES     	1000
/**
* Decoding error - invalid GIF screen size
*/
#define D_GIF_ERR_INVALID_SCR_DIMS 	1001
/**
* Decoding error - invalid frame size
*/
#define D_GIF_ERR_INVALID_IMG_DIMS 	1002
/**
* Decoding error - frame size is greater that screen size
*/
#define D_GIF_ERR_IMG_NOT_CONFINED 	1003
/**
* Decoding error - input source rewind failed
*/
#define D_GIF_ERR_REWIND_FAILED 	1004

#define ILLEGAL_STATE_EXCEPTION "java/lang/IllegalStateException"
#define OUT_OF_MEMORY_ERROR "java/lang/OutOfMemoryError"

#define PACK_RENDER_FRAME_RESULT(invalidationDelay, isAnimationCompleted) (jlong) ((invalidationDelay << 1) | (isAnimationCompleted & 1L))
#define GET_ADDR(bm, width, left, top) bm + top * width + left

typedef struct
{
	uint8_t red;
	uint8_t green;
	uint8_t blue;
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
    time_t lastFrameRemainder;
    time_t nextStartTime;
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
	int32_t stride;
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

/**
* Generates default color map, used when there is no color map defined in GIF file.
* Upon successful allocation in JNI_OnLoad it is stored for further use.
*
*/
static ColorMapObject *genDefColorMap(void);

/**
* @return the real time, in ms
*/
static time_t getRealTime(JNIEnv *env);

/**
* Frees dynamically allocated memory
*/
static void cleanUp(GifInfo *info);

static void throwException(JNIEnv *env, char *exceptionClass, char *message);

static bool isSourceNull(void *ptr, JNIEnv *env);

static bool lockPixels(JNIEnv *env, jobject jbitmap, void **pixels, bool throwOnError);

static void unlockPixels(JNIEnv *env, jobject jbitmap);

static int fileRead(GifFileType *gif, GifByteType *bytes, int size);

static JNIEnv *getEnv(void);

static int directByteBufferReadFun(GifFileType *gif, GifByteType *bytes, int size);

static int byteArrayReadFun(GifFileType *gif, GifByteType *bytes, int size);

static int streamReadFun(GifFileType *gif, GifByteType *bytes, int size);

static int fileRewind(GifInfo *info);

static int streamRewind(GifInfo *info);

static int byteArrayRewind(GifInfo *info);

static int directByteBufferRewindFun(GifInfo *info);

static int getComment(GifByteType *Bytes, char **cmt);

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info);

static int DDGifSlurp(GifFileType *GifFile, GifInfo *info, bool shouldDecode);

static void throwGifIOException(int errorCode, JNIEnv *env);

static jobject createGifHandle(GifFileType *GifFileIn, int Error, long startPos, RewindFunc rewindFunc, JNIEnv *env, const jboolean justDecodeMetaData);

