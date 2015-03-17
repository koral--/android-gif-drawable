#include "gif.h"

inline void throwException(JNIEnv *env, char *exceptionClass, char *message) {
    jclass exClass = (*env)->FindClass(env, exceptionClass);
    if (exClass != NULL)
        (*env)->ThrowNew(env, exClass, message);
}

inline bool isSourceNull(void *ptr, JNIEnv *env) {
    if (ptr != NULL)
        return false;
    throwException(env, "java/lang/NullPointerException", "Input source is null");
    return true;
}

void throwGifIOException(int errorCode, JNIEnv *env) {
//nullchecks just to prevent segfaults, LinkageError will be thrown if GifIOException cannot be instantiated
    jclass exClass = (*env)->FindClass(env,
            "pl/droidsonroids/gif/GifIOException");
    if (exClass == NULL)
        return;
    jmethodID mid = (*env)->GetMethodID(env, exClass, "<init>", "(I)V");
    if (mid == NULL)
        return;
    jobject exception = (*env)->NewObject(env, exClass, mid, errorCode);
    if (exception != NULL)
        (*env)->Throw(env, exception);
}