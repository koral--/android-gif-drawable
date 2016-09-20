#include "gif.h"

#define RUNTIME_EXCEPTION_CLASS_NAME "java/lang/RuntimeException"
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
		case RUNTIME_EXCEPTION_ERRNO:
			exceptionClassName = RUNTIME_EXCEPTION_CLASS_NAME;
			char fullMessage[NL_TEXTMAX] = "";
			strncat(fullMessage, message, NL_TEXTMAX);

			char errnoMessage[NL_TEXTMAX];
			if (strerror_r(errno, errnoMessage, NL_TEXTMAX) == 0) {
				strncat(fullMessage, errnoMessage, NL_TEXTMAX);
			}
			message = fullMessage;
			break;
		default:
			exceptionClassName = RUNTIME_EXCEPTION_CLASS_NAME;
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
