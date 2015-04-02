#include <unistd.h>
#include <jni.h>
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
#include <sys/stat.h>
#include "giflib/gif_lib.h"

#ifdef DEBUG
#include <android/log.h>
#define  LOG_TAG    "libgif"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif

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

#define PACK_RENDER_FRAME_RESULT(invalidationDelay, isAnimationCompleted) (jlong) ((invalidationDelay << 1) | (isAnimationCompleted & 1L))
#define GET_ADDR(bm, width, left, top) bm + top * width + left

#define OOME_MESSAGE "Failed to allocate native memory"

enum Exception {
    ILLEGAL_STATE_EXCEPTION_ERRNO, ILLEGAL_STATE_EXCEPTION_BARE, OUT_OF_MEMORY_ERROR, NULL_POINTER_EXCEPTION
};

typedef struct {
    GifColorType rgb;
    uint8_t alpha;
} argb;

typedef struct {
    uint_fast16_t duration;
    int_fast16_t transpIndex;
    uint_fast8_t disposalMethod;
} FrameInfo;

typedef struct GifInfo GifInfo;

typedef int
(*RewindFunc)(GifInfo *);

struct GifInfo {
    GifFileType *gifFilePtr;
    time_t lastFrameRemainder;
    time_t nextStartTime;
    int currentIndex;
    FrameInfo *infos;
    argb *backupPtr;
    long startPos;
    unsigned char *rasterBits;
    char *comment;
    uint_fast8_t loopCount;
    uint_fast8_t currentLoop;
    RewindFunc rewindFunction;
    jfloat speedFactor;
    int32_t stride;
    jlong sourceLength;
    int eventFd;
    void *surfaceBackupPtr;
};

typedef struct {
    jobject stream;
    jclass streamCls;
    jmethodID readMID;
    jmethodID resetMID;
    jbyteArray buffer;
} StreamContainer;

typedef struct {
    long pos;
    jbyteArray buffer;
    jsize arrLen;
} ByteArrayContainer;

typedef struct {
    long pos;
    jbyte *bytes;
    jlong capacity;
} DirectByteBufferContainer;

typedef struct {
    GifFileType *GifFileIn;
    int Error;
    long startPos;
    RewindFunc rewindFunc;
    jlong sourceLength;
} GifSourceDescriptor;

/**
* Global default color map, initialized by genDefColorMap(void)
*/
static ColorMapObject *defaultCmap;

/**
* Global VM reference, initialized in JNI_OnLoad
*/
static JavaVM *g_jvm;

/**
* Generates default color map, used when there is no color map defined in GIF file.
* Upon successful allocation in JNI_OnLoad it is stored for further use.
*
*/
ColorMapObject *genDefColorMap(void);

/**
* @return the real time, in ms
*/
inline time_t getRealTime();

/**
* Frees dynamically allocated memory
*/
void cleanUp(GifInfo *info);

void throwException(JNIEnv *env, enum Exception exception, char *message);

bool isSourceNull(void *ptr, JNIEnv *env);

static int fileRead(GifFileType *gif, GifByteType *bytes, int size);

inline JNIEnv *getEnv(void);

static int directByteBufferReadFun(GifFileType *gif, GifByteType *bytes, int size);

static int byteArrayReadFun(GifFileType *gif, GifByteType *bytes, int size);

static int streamReadFun(GifFileType *gif, GifByteType *bytes, int size);

static int fileRewind(GifInfo *info);

static int streamRewind(GifInfo *info);

static int byteArrayRewind(GifInfo *info);

static int directByteBufferRewindFun(GifInfo *info);

static int getComment(GifByteType *Bytes, char **cmt);

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info);

int DDGifSlurp(GifFileType *GifFile, GifInfo *info, bool shouldDecode);

void throwGifIOException(int errorCode, JNIEnv *env);

jobject createGifHandle(GifSourceDescriptor *descriptor, JNIEnv *env, jboolean justDecodeMetaData);

static void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap);

static void drawFrame(argb *bm, GifInfo *info, SavedImage *frame);

static bool checkIfCover(const SavedImage *target, const SavedImage *covered);

static void disposeFrameIfNeeded(argb *bm, GifInfo *info, int idx);

void getBitmap(argb *bm, GifInfo *info);

bool reset(GifInfo *info);

int lockPixels(JNIEnv *env, jobject jbitmap, GifInfo *info, void **pixels);

void unlockPixels(JNIEnv *env, jobject jbitmap);

int calculateInvalidationDelay(GifInfo *info, time_t renderStartTime);

static int getSkippedFramesCount(GifInfo *info, jint desiredPos);

jint getCurrentPosition(GifInfo *info);