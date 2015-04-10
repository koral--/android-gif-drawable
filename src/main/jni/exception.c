#include <errno.h>
#include "gif.h"

#define ILLEGAL_STATE_EXCEPTION_CLASS_NAME "java/lang/IllegalStateException"
#define OUT_OF_MEMORY_ERROR_CLASS_NAME "java/lang/OutOfMemoryError"
#define NULL_POINTER_EXCEPTION_CLASS_NAME "java/lang/NullPointerException"

inline void throwException(JNIEnv *env, enum Exception exception, char *message) {
    if ((*env)->ExceptionCheck(env) == JNI_TRUE)
        return;
    if (errno == ENOMEM)
        exception = OUT_OF_MEMORY_ERROR;

    const char *exceptionClassName;
    switch (exception) {
        case OUT_OF_MEMORY_ERROR:
            exceptionClassName = OUT_OF_MEMORY_ERROR_CLASS_NAME;
            break;
        case NULL_POINTER_EXCEPTION:
            exceptionClassName = NULL_POINTER_EXCEPTION_CLASS_NAME;
            break;
        case ILLEGAL_STATE_EXCEPTION_ERRNO:
            exceptionClassName = ILLEGAL_STATE_EXCEPTION_CLASS_NAME;
            char fullMessage[64];
            if (snprintf(fullMessage, 64, "%s, errno: %d", message, errno) > 0)
                message = fullMessage;
            break;
        default:
            exceptionClassName = ILLEGAL_STATE_EXCEPTION_CLASS_NAME;
    }

    jclass exClass = (*env)->FindClass(env, exceptionClassName);
    if (exClass != NULL)
        (*env)->ThrowNew(env, exClass, message);
}

inline bool isSourceNull(void *ptr, JNIEnv *env) {
    if (ptr != NULL)
        return false;
    throwException(env, NULL_POINTER_EXCEPTION, "Input source is null");
    return true;
}

void throwGifIOException(int errorCode, JNIEnv *env) {
//nullchecks just to prevent segfaults, LinkageError will be thrown if GifIOException cannot be instantiated
    if ((*env)->ExceptionCheck(env) == JNI_TRUE)
        return;
    jclass exClass = (*env)->FindClass(env, "pl/droidsonroids/gif/GifIOException");
    if (exClass == NULL)
        return;
    jmethodID mid = (*env)->GetMethodID(env, exClass, "<init>", "(I)V");
    if (mid == NULL)
        return;
    jobject exception = (*env)->NewObject(env, exClass, mid, errorCode);
    if (exception != NULL)
        (*env)->Throw(env, exception);
}