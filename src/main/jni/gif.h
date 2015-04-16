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
#include <pthread.h>
#include <poll.h>
#include <errno.h>
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

#define GET_ADDR(bm, width, left, top) bm + top * width + left

#define OOME_MESSAGE "Failed to allocate native memory"

enum Exception {
    ILLEGAL_STATE_EXCEPTION_ERRNO, ILLEGAL_STATE_EXCEPTION_BARE, OUT_OF_MEMORY_ERROR, NULL_POINTER_EXCEPTION
};

typedef struct {
    GifColorType rgb;
    uint8_t alpha;
} argb;

typedef struct GifInfo GifInfo;

typedef int
(*RewindFunc)(GifInfo *);

typedef struct {
    struct pollfd eventPollFd;
    void *surfaceBackupPtr;
    uint8_t slurpHelper;
    pthread_mutex_t slurpMutex;
    pthread_cond_t slurpCond;
    uint8_t renderHelper;
    pthread_mutex_t renderMutex;
    pthread_cond_t renderCond;
    JavaVM * jvm;
} SurfaceDescriptor;

struct GifInfo {
    GifFileType *gifFilePtr;
    long lastFrameRemainder;
    long nextStartTime;
    uint_fast32_t currentIndex;
    GraphicsControlBlock *infos;
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
    jboolean isOpaque;
    SurfaceDescriptor* surfaceDescriptor;
};

typedef struct {
    jobject stream;
    jclass streamCls;
    jmethodID readMID;
    jmethodID resetMID;
    jbyteArray buffer;
    JavaVM * jvm;
} StreamContainer;

typedef struct {
    uint_fast32_t pos;
    jbyteArray buffer;
    jsize arrLen;
    JavaVM * jvm;
} ByteArrayContainer;

typedef struct {
    jlong pos;
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
//static JavaVM *g_jvm;

/**
* @return the real time, in ms
*/
inline long getRealTime();

/**
* Frees dynamically allocated memory
*/
void cleanUp(GifInfo *info);

void throwException(JNIEnv *env, enum Exception exception, char *message);

bool isSourceNull(void *ptr, JNIEnv *env);

static uint_fast8_t fileRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

inline JNIEnv *getEnv(JavaVM* jvm);

static uint_fast8_t directByteBufferReadFun(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

static uint_fast8_t byteArrayReadFun(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

static uint_fast8_t streamReadFun(GifFileType *gif, GifByteType *bytes, uint_fast8_t size);

static int fileRewind(GifInfo *info);

static int streamRewind(GifInfo *info);

static int byteArrayRewind(GifInfo *info);

static int directByteBufferRewindFun(GifInfo *info);

static int getComment(GifByteType *Bytes, GifInfo*);

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info);

void DDGifSlurp(GifInfo *info, bool shouldDecode);

void throwGifIOException(int errorCode, JNIEnv *env);

jobject createGifHandle(GifSourceDescriptor *descriptor, JNIEnv *env, jboolean justDecodeMetaData);

static inline void blitNormal(argb *bm, GifInfo *info, SavedImage *frame, ColorMapObject *cmap);

static void drawFrame(argb *bm, GifInfo *info, SavedImage *frame);

static bool checkIfCover(const SavedImage *target, const SavedImage *covered);

static void disposeFrameIfNeeded(argb *bm, GifInfo *info);

uint_fast32_t getBitmap(argb *bm, GifInfo *info);

bool reset(GifInfo *info);

int lockPixels(JNIEnv *env, jobject jbitmap, GifInfo *info, void **pixels);

void unlockPixels(JNIEnv *env, jobject jbitmap);

long calculateInvalidationDelay(GifInfo *info, long renderStartTime, uint_fast32_t frameDuration);

jint restoreSavedState(GifInfo *info, JNIEnv *env, jlongArray state, void *pixels);

void releaseSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *pConst);

inline void prepareCanvas(argb *bm, GifInfo *info);
