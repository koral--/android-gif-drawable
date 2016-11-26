#include "gif.h"

static const jlong NULL_GIF_INFO = (jlong) (intptr_t) NULL;

uint_fast8_t fileRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
	FILE *file = (FILE *) gif->UserData;
	return (uint_fast8_t) fread(bytes, 1, size, file);
}

uint_fast8_t directByteBufferRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
	DirectByteBufferContainer *dbbc = gif->UserData;
	if (dbbc->position + size > dbbc->capacity) {
		size -= dbbc->position + size - dbbc->capacity;
	}
	memcpy(bytes, dbbc->bytes + dbbc->position, (size_t) size);
	dbbc->position += size;
	return size;
}

uint_fast8_t byteArrayRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
	ByteArrayContainer *bac = gif->UserData;
	JNIEnv *env = getEnv();
	if (env == NULL) {
		return 0;
	}
	if (bac->position + size > bac->length) {
		size -= bac->position + size - bac->length;
	}
	(*env)->GetByteArrayRegion(env, bac->buffer, (jsize) bac->position, size, (jbyte *) bytes);
	bac->position += size;
	return size;
}

static jint bufferUpTo(JNIEnv *env, StreamContainer *sc, size_t size) {
	jint totalLength = 0;
	jint length;
	do {
		length = (*env)->CallIntMethod(env, sc->stream, sc->readMID, sc->buffer, totalLength, size - totalLength);
		if (length > 0) {
			totalLength += length;
		} else {
			if ((*env)->ExceptionCheck(env)) {
				(*env)->ExceptionClear(env);
			}
			break;
		}
	} while (totalLength < size);
	return totalLength;
}

uint_fast8_t streamRead(GifFileType *gif, GifByteType *bytes, uint_fast8_t size) {
	StreamContainer *sc = gif->UserData;
	JNIEnv *env = getEnv();
	if (env == NULL || (*env)->MonitorEnter(env, sc->stream) != 0) {
		return 0;
	}

	if (sc->bufferPosition == 0) {
		size_t bufferSize = sc->markCalled ? STREAM_BUFFER_SIZE : size;
		jint readLen = bufferUpTo(env, sc, bufferSize);
		if (readLen < size) {
			size = (uint_fast8_t) readLen;
		}
		(*env)->GetByteArrayRegion(env, sc->buffer, 0, size, (jbyte *) bytes);
		if (sc->markCalled) {
			sc->bufferPosition += size;
		}
	} else if (sc->bufferPosition + size <= STREAM_BUFFER_SIZE) {
		(*env)->GetByteArrayRegion(env, sc->buffer, sc->bufferPosition, size, (jbyte *) bytes);
		sc->bufferPosition += size;
	} else {
		jint tailSize = STREAM_BUFFER_SIZE - sc->bufferPosition;
		(*env)->GetByteArrayRegion(env, sc->buffer, sc->bufferPosition, tailSize, (jbyte *) bytes);
		bytes += tailSize;
		jint readLen = bufferUpTo(env, sc, STREAM_BUFFER_SIZE);
		jint headSize = size - tailSize;
		if (readLen < headSize) {
			size = (uint_fast8_t) readLen;
			headSize = readLen;
		}
		(*env)->GetByteArrayRegion(env, sc->buffer, 0, headSize, (jbyte *) bytes);
		sc->bufferPosition = headSize;
	}

	if ((*env)->MonitorExit(env, sc->stream) != 0) {
		return 0;
	}

	return (uint_fast8_t) size;
}

int fileRewind(GifInfo *info) {
	if (fseeko(info->gifFilePtr->UserData, info->startPos, SEEK_SET) == 0) {
		return 0;
	}
	info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
	return -1;
}

int streamRewind(GifInfo *info) {
	GifFileType *gif = info->gifFilePtr;
	StreamContainer *sc = gif->UserData;
	JNIEnv *env = getEnv();
	sc->bufferPosition = 0;
	if (env == NULL) {
		info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
		return -1;
	}
	(*env)->CallVoidMethod(env, sc->stream, sc->resetMID);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionClear(env);
		info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
		return -1;
	}
	return 0;
}

int byteArrayRewind(GifInfo *info) {
	ByteArrayContainer *bac = info->gifFilePtr->UserData;
	bac->position = (uint_fast32_t) info->startPos;
	return 0;
}

int directByteBufferRewind(GifInfo *info) {
	DirectByteBufferContainer *dbbc = info->gifFilePtr->UserData;
	dbbc->position = info->startPos;
	return 0;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openFile(JNIEnv *env, jclass __unused class, jstring jfname) {
	if (isSourceNull(jfname, env)) {
		return NULL_GIF_INFO;
	}

	const char *const filename = (*env)->GetStringUTFChars(env, jfname, NULL);
	if (filename == NULL) {
		throwException(env, RUNTIME_EXCEPTION_BARE, "GetStringUTFChars failed");
		return NULL_GIF_INFO;
	}
	FILE *file = fopen(filename, "rb");
	if (file == NULL) {
		throwGifIOException(D_GIF_ERR_OPEN_FAILED, env, true);
		(*env)->ReleaseStringUTFChars(env, jfname, filename);
		return NULL_GIF_INFO;
	}
	(*env)->ReleaseStringUTFChars(env, jfname, filename);

	struct stat st;
	const long sourceLength = stat(filename, &st) == 0 ? st.st_size : -1;

	GifInfo *const info = createGifInfoFromFile(env, file, sourceLength);
	if (info == NULL) {
		fclose(file);
	}
	return (jlong) (intptr_t) info;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openByteArray(JNIEnv *env, jclass __unused class, jbyteArray bytes) {
	if (isSourceNull(bytes, env)) {
		return NULL_GIF_INFO;
	}
	ByteArrayContainer *container = malloc(sizeof(ByteArrayContainer));
	if (container == NULL) {
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL_GIF_INFO;
	}
	container->buffer = (*env)->NewGlobalRef(env, bytes);
	if (container->buffer == NULL) {
		free(container);
		throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
		return NULL_GIF_INFO;
	}
	container->length = (unsigned int) (*env)->GetArrayLength(env, container->buffer);
	container->position = 0;
	GifSourceDescriptor descriptor = {
			.rewindFunc = byteArrayRewind,
			.sourceLength = container->length
	};
	descriptor.GifFileIn = DGifOpen(container, &byteArrayRead, &descriptor.Error);
	descriptor.startPos = container->position;

	GifInfo *info = createGifInfo(&descriptor, env);

	if (info == NULL) {
		(*env)->DeleteGlobalRef(env, container->buffer);
		free(container);
	}
	return (jlong) (intptr_t) info;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openDirectByteBuffer(JNIEnv *env, jclass __unused class, jobject buffer) {
	jbyte *bytes = (*env)->GetDirectBufferAddress(env, buffer);
	jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
	if (bytes == NULL || capacity <= 0) {
		if (!isSourceNull(buffer, env)) {
			throwGifIOException(D_GIF_ERR_INVALID_BYTE_BUFFER, env, false);
		}
		return NULL_GIF_INFO;
	}
	DirectByteBufferContainer *container = malloc(sizeof(DirectByteBufferContainer));
	if (container == NULL) {
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL_GIF_INFO;
	}
	container->bytes = bytes;
	container->capacity = capacity;
	container->position = 0;

	GifSourceDescriptor descriptor = {
			.rewindFunc = directByteBufferRewind,
			.sourceLength = container->capacity
	};
	descriptor.GifFileIn = DGifOpen(container, &directByteBufferRead, &descriptor.Error);
	descriptor.startPos = container->position;

	GifInfo *info = createGifInfo(&descriptor, env);
	if (info == NULL) {
		free(container);
	}
	return (jlong) (intptr_t) info;
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openStream(JNIEnv *env, jclass __unused class, jobject stream) {
	jclass streamCls = (*env)->NewGlobalRef(env, (*env)->GetObjectClass(env, stream));
	if (streamCls == NULL) {
		throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
		return NULL_GIF_INFO;
	}
	static jmethodID markMID = NULL;
	if (markMID == NULL) {
		markMID = (*env)->GetMethodID(env, streamCls, "mark", "(I)V");
	}
	static jmethodID readMID = NULL;
	if (readMID == NULL) {
		readMID = (*env)->GetMethodID(env, streamCls, "read", "([BII)I");
	}
	static jmethodID resetMID = NULL;
	if (resetMID == NULL) {
		resetMID = (*env)->GetMethodID(env, streamCls, "reset", "()V");
	}

	if (markMID == NULL || readMID == NULL || resetMID == NULL) {
		(*env)->DeleteGlobalRef(env, streamCls);
		return NULL_GIF_INFO;
	}

	StreamContainer *container = malloc(sizeof(StreamContainer));
	if (container == NULL) {
		(*env)->DeleteGlobalRef(env, streamCls);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL_GIF_INFO;
	}

	container->buffer = (*env)->NewByteArray(env, STREAM_BUFFER_SIZE);
	if (container->buffer == NULL) {
		(*env)->DeleteGlobalRef(env, streamCls);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL_GIF_INFO;
	}
	container->buffer = (*env)->NewGlobalRef(env, container->buffer);
	if (container->buffer == NULL) {
		throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
		return NULL_GIF_INFO;
	}

	container->readMID = readMID;
	container->resetMID = resetMID;
	container->stream = (*env)->NewGlobalRef(env, stream);
	if (container->stream == NULL) {
		free(container);
		(*env)->DeleteGlobalRef(env, streamCls);
		throwException(env, RUNTIME_EXCEPTION_BARE, "NewGlobalRef failed");
		return NULL_GIF_INFO;
	}
	container->streamCls = streamCls;

	GifSourceDescriptor descriptor = {
			.rewindFunc = streamRewind,
			.startPos = 0,
			.sourceLength = -1
	};
	container->bufferPosition = 0;
	container->markCalled = false;
	descriptor.GifFileIn = DGifOpen(container, &streamRead, &descriptor.Error);

	(*env)->CallVoidMethod(env, stream, markMID, LONG_MAX);
	container->markCalled = true;
	container->bufferPosition = 0;
	if (!(*env)->ExceptionCheck(env)) {
		GifInfo *info = createGifInfo(&descriptor, env);
		return (jlong) (intptr_t) info;
	} else {
		(*env)->DeleteGlobalRef(env, streamCls);
		(*env)->DeleteGlobalRef(env, container->stream);
		(*env)->DeleteGlobalRef(env, container->buffer);
		free(container);
		return NULL_GIF_INFO;
	}
}

__unused JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_GifInfoHandle_openFd(JNIEnv *env, jclass __unused handleClass, jobject jfd, jlong offset) {
	if (isSourceNull(jfd, env)) {
		return NULL_GIF_INFO;
	}
	jclass fdClass = (*env)->GetObjectClass(env, jfd);
	static jfieldID fdClassDescriptorFieldID = NULL;
	if (fdClassDescriptorFieldID == NULL) {
		fdClassDescriptorFieldID = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
	}
	if (fdClassDescriptorFieldID == NULL) {
		return NULL_GIF_INFO;
	}
	const jint oldFd = (*env)->GetIntField(env, jfd, fdClassDescriptorFieldID);
	const int fd = dup(oldFd);
	if (fd == -1) {
		throwGifIOException(D_GIF_ERR_OPEN_FAILED, env, true);
		return NULL_GIF_INFO;
	}
	if (lseek64(fd, offset, SEEK_SET) != -1) {
		FILE *file = fdopen(fd, "rb");
		if (file == NULL) {
			throwGifIOException(D_GIF_ERR_OPEN_FAILED, env, true);
			close(fd);
			return NULL_GIF_INFO;
		}
		struct stat st;
		const long sourceLength = fstat(fd, &st) == 0 ? st.st_size : -1;

		GifInfo *const info = createGifInfoFromFile(env, file, sourceLength);
		if (info == NULL) {
			close(fd);
		}
		return (jlong) info;
	} else {
		throwGifIOException(D_GIF_ERR_OPEN_FAILED, env, true);
		close(fd);
		return (jlong) (intptr_t) NULL;
	}
}

static GifInfo *createGifInfoFromFile(JNIEnv *env, FILE *file, const long sourceLength) {
	GifSourceDescriptor descriptor = {
			.rewindFunc = fileRewind,
			.sourceLength = sourceLength
	};
	descriptor.GifFileIn = DGifOpen(file, &fileRead, &descriptor.Error);
	descriptor.startPos = ftell(file);

	return createGifInfo(&descriptor, env);
}