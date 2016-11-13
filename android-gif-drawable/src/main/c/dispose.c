#include "gif.h"

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_free(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL)
		return;
	if (info->destructor != NULL) {
		info->destructor(info, env);
	}
	if (info->rewindFunction == streamRewind) {
		StreamContainer *sc = info->gifFilePtr->UserData;
		static jmethodID closeMID = NULL;
		if (closeMID == NULL) {
			(*env)->GetMethodID(env, sc->streamCls, "close", "()V");
		}
		if (closeMID != NULL) {
			(*env)->CallVoidMethod(env, sc->stream, closeMID);
		}
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionClear(env);
		}

		(*env)->DeleteGlobalRef(env, sc->streamCls);
		(*env)->DeleteGlobalRef(env, sc->stream);

		if (sc->buffer != NULL) {
			(*env)->DeleteGlobalRef(env, sc->buffer);
		}

		free(sc);
	}
	else if (info->rewindFunction == fileRewind) {
		fclose(info->gifFilePtr->UserData);
	}
	else if (info->rewindFunction == byteArrayRewind) {
		ByteArrayContainer *bac = info->gifFilePtr->UserData;
		if (bac->buffer != NULL) {
			(*env)->DeleteGlobalRef(env, bac->buffer);
		}
		free(bac);
	}
	else if (info->rewindFunction == directByteBufferRewind) {
		free(info->gifFilePtr->UserData);
	}
	info->gifFilePtr->UserData = NULL;
	cleanUp(info);
}

void cleanUp(GifInfo *info) {
	free(info->backupPtr);
	info->backupPtr = NULL;
	free(info->controlBlock);
	info->controlBlock = NULL;
	free(info->rasterBits);
	info->rasterBits = NULL;
	free(info->comment);
	info->comment = NULL;

	DGifCloseFile(info->gifFilePtr);
	free(info);
}
