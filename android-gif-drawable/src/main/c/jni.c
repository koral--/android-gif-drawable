#include "gif.h"

/**
* Global VM reference, initialized in JNI_OnLoad
*/
static JavaVM *g_jvm;
static struct JavaVMAttachArgs attachArgs = {.version=JNI_VERSION_1_6, .group=NULL, .name="GifIOThread"};
static ColorMapObject *defaultCmap;

JNIEnv *getEnv(void) {
	JNIEnv *env;
	if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, &attachArgs) == JNI_OK) {
		return env;
	}
	return NULL;
}

void DetachCurrentThread(void) {
	(*g_jvm)->DetachCurrentThread(g_jvm);
}

__unused JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *__unused reserved) {
	g_jvm = vm;
	JNIEnv *env;
	if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}

	defaultCmap = GifMakeMapObject(8, NULL);
	if (defaultCmap != NULL) {
		uint_fast16_t iColor;
		for (iColor = 1; iColor < 256; iColor++) {
			defaultCmap->Colors[iColor].Red = (GifByteType) iColor;
			defaultCmap->Colors[iColor].Green = (GifByteType) iColor;
			defaultCmap->Colors[iColor].Blue = (GifByteType) iColor;
		}
	} else {
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
	}

	struct timespec ts;
	if (clock_gettime(CLOCK_MONOTONIC_RAW, &ts) == -1) {
		//sanity check here instead of on each clock_gettime() call
		throwException(env, RUNTIME_EXCEPTION_BARE, "CLOCK_MONOTONIC_RAW is not present");
	}
	return JNI_VERSION_1_6;
}

__unused JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *__unused vm, void *__unused reserved) {
	GifFreeMapObject(defaultCmap);
}

ColorMapObject *getDefColorMap(void) {
	return defaultCmap;
}