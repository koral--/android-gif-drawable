#include "gif.h"

__unused JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_free(JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	if (info == NULL) {
		return;
	}
	if (info->destructor != NULL) {
		info->destructor(info, env);
	}
	if (info->rewindFunction == streamRewind) {
		StreamContainer *streamContainer = info->gifFilePtr->UserData;

		(*env)->CallVoidMethod(env, streamContainer->stream, streamContainer->closeMethodID);

		if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
#ifdef DEBUG
			(*env)->ExceptionDescribe(env);
#endif
			(*env)->ExceptionClear(env);
		}

		(*env)->DeleteGlobalRef(env, streamContainer->stream);
		(*env)->DeleteGlobalRef(env, streamContainer->buffer);

		free(streamContainer);
	} else if (info->rewindFunction == fileRewind) {
		fclose(info->gifFilePtr->UserData);
	} else if (info->rewindFunction == byteArrayRewind) {
		ByteArrayContainer *container = info->gifFilePtr->UserData;
		(*env)->DeleteGlobalRef(env, container->buffer);
		free(container);
	} else if (info->rewindFunction == directByteBufferRewind) {
		DirectByteBufferContainer *container = info->gifFilePtr->UserData;
		(*env)->DeleteGlobalRef(env, container->bufferRef);
		free(container);
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
