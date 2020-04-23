#pragma once

#ifdef __clang__
#pragma clang system_header
#pragma clang diagnostic ignored "-Wgnu"
#elif __GNUC__
#pragma GCC system_header
#pragma GCC diagnostic ignored "-Wgnu"
#endif

#include <unistd.h>
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
#include <sys/cdefs.h>
#include <sys/stat.h>
#include <pthread.h>
#include <poll.h>
#include <errno.h>
#include <sys/eventfd.h>
#include "giflib/gif_lib.h"

#ifdef DEBUG
#include <android/log.h>
#define  LOG_TAG    "libgif"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#else
#define  LOGE(...)
#endif

#define THROW_ON_NONZERO_RESULT(fun, message) if ((fun) !=0) throwException(env, RUNTIME_EXCEPTION_ERRNO, message)
#define GET_ADDR(bm, width, left, top) ((bm) + (top) * (width) + (left))

#define OOME_MESSAGE "Failed to allocate native memory"
#define DEFAULT_FRAME_DURATION_MS 100
#define STREAM_BUFFER_SIZE  8192
#define NULL_GIF_INFO (jlong) (intptr_t) NULL

/**
 * Some gif files are not strictly follow 89a.
 * DGifSlurp will return read head error or get record type error.
 * but the image still can display. so here should ignore the error.
 */
//#define STRICT_FORMAT_89A

/**
 * Decoding error - no frames
 */
#define D_GIF_ERR_NO_FRAMES        1000
/**
* Decoding error - invalid GIF screen size
*/
#define D_GIF_ERR_INVALID_SCR_DIMS    1001
/**
* Decoding error - invalid frame size
*/
#define D_GIF_ERR_INVALID_IMG_DIMS    1002
/**
* Decoding error - frame size is greater that screen size
*/
#define D_GIF_ERR_IMG_NOT_CONFINED    1003
/**
* Decoding error - input source rewind failed
*/
#define D_GIF_ERR_REWIND_FAILED    1004
/**
* Decoding error - invalid and/or indirect byte buffer specified
*/
#define D_GIF_ERR_INVALID_BYTE_BUFFER    1005

enum Exception {
	RUNTIME_EXCEPTION_ERRNO, RUNTIME_EXCEPTION_BARE, OUT_OF_MEMORY_ERROR, NULL_POINTER_EXCEPTION
};

typedef struct {
	GifColorType rgb;
	uint8_t alpha;
} argb;

typedef struct GifInfo GifInfo;

typedef int
(*RewindFunc)(GifInfo *);

struct GifInfo {
	void (*destructor)(GifInfo *, JNIEnv *);
	GifFileType *gifFilePtr;
	GifWord originalWidth, originalHeight;
	uint_fast16_t sampleSize;
	long long lastFrameRemainder;
	long long nextStartTime;
	uint_fast32_t currentIndex;
	GraphicsControlBlock *controlBlock;
	argb *backupPtr;
	long long startPos;
	unsigned char *rasterBits;
	uint_fast32_t rasterSize;
	char *comment;
	uint_fast16_t loopCount;
	uint_fast16_t currentLoop;
	RewindFunc rewindFunction;
	jfloat speedFactor;
	uint32_t stride;
	jlong sourceLength;
	bool isOpaque;
	void *frameBufferDescriptor;
};

typedef struct {
	jobject stream;
	jmethodID readMethodID;
	jmethodID resetMethodID;
	jmethodID closeMethodID;
	jbyteArray buffer;
	jint bufferPosition;
	bool markCalled;
} StreamContainer;

typedef struct {
	uint_fast32_t position;
	jbyteArray buffer;
	unsigned int length;
} ByteArrayContainer;

typedef struct {
	jlong position;
	jbyte *bytes;
	jlong capacity;
	jobject bufferRef;
} DirectByteBufferContainer;

typedef struct {
	GifFileType *GifFileIn;
	int Error;
	long long startPos;
	RewindFunc rewindFunc;
	jlong sourceLength;
} GifSourceDescriptor;

void DetachCurrentThread(void);

ColorMapObject *getDefColorMap(void);

/**
* @return the real time, in ms
*/
long getRealTime(void);

/**
* Frees dynamically allocated memory
*/
void cleanUp(GifInfo *info);

void throwException(JNIEnv *env, enum Exception exception, char *message);

bool isSourceNull(void *ptr, JNIEnv *env);

static uint_fast8_t fileRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

static uint_fast8_t directByteBufferRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

static uint_fast8_t byteArrayRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

static uint_fast8_t streamRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

int fileRewind(GifInfo *info);

int streamRewind(GifInfo *info);

int byteArrayRewind(GifInfo *info);

int directByteBufferRewind(GifInfo *info);

static int getComment(GifByteType *Bytes, GifInfo *);

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info);

void DDGifSlurp(GifInfo *info, bool decode, bool exitAfterFrame);

void throwGifIOException(int gifErrorCode, JNIEnv *env, bool readErrno);

GifInfo *createGifInfo(GifSourceDescriptor *descriptor, JNIEnv *env);

static inline void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap);

static void drawFrame(argb *bm, GifInfo *info, SavedImage *frame);

static bool checkIfCover(const SavedImage *target, const SavedImage *covered);

static void disposeFrameIfNeeded(argb *bm, GifInfo *info);

uint_fast32_t getBitmap(argb *bm, GifInfo *info);

bool reset(GifInfo *info);

int lockPixels(JNIEnv *env, jobject jbitmap, GifInfo *info, void **pixels);

void unlockPixels(JNIEnv *env, jobject jbitmap);

long long calculateInvalidationDelay(GifInfo *info, long renderStartTime, uint_fast32_t frameDuration);

jint restoreSavedState(GifInfo *info, JNIEnv *env, jlongArray state, void *pixels);

void prepareCanvas(const argb *bm, GifInfo *info);

void drawNextBitmap(argb *bm, GifInfo *info);

uint_fast32_t getFrameDuration(GifInfo *info);

JNIEnv *getEnv(void);

uint_fast32_t seek(GifInfo *info, uint_fast32_t desiredIndex, void *pixels);

void setGCBDefaults(GraphicsControlBlock *gcb);

static GifInfo *createGifInfoFromFile(JNIEnv *env, FILE *file, long long sourceLength);
