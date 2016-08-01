package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Native library wrapper
 */
final class GifInfoHandle {

	/**
	 * Pointer to native structure. Access must be synchronized, heap corruption may occur otherwise
	 * when {@link #recycle()} is called during another operation.
	 */
	private volatile long gifInfoPtr;

	static final GifInfoHandle NULL_INFO = new GifInfoHandle();

	static {
		LibraryLoader.loadLibrary(null, LibraryLoader.BASE_LIBRARY_NAME);
	}

	private GifInfoHandle() {
	}

	GifInfoHandle(FileDescriptor fd, boolean justDecodeMetaData) throws GifIOException {
		gifInfoPtr = openFd(fd, 0, justDecodeMetaData);
	}

	GifInfoHandle(byte[] bytes, boolean justDecodeMetaData) throws GifIOException {
		gifInfoPtr = openByteArray(bytes, justDecodeMetaData);
	}

	GifInfoHandle(ByteBuffer buffer, boolean justDecodeMetaData) throws GifIOException {
		gifInfoPtr = openDirectByteBuffer(buffer, justDecodeMetaData);
	}

	GifInfoHandle(String filePath, boolean justDecodeMetaData) throws GifIOException {
		gifInfoPtr = openFile(filePath, justDecodeMetaData);
	}

	GifInfoHandle(InputStream stream, boolean justDecodeMetaData) throws GifIOException {
		if (!stream.markSupported()) {
			throw new IllegalArgumentException("InputStream does not support marking");
		}
		gifInfoPtr = openStream(stream, justDecodeMetaData);
	}

	GifInfoHandle(AssetFileDescriptor afd, boolean justDecodeMetaData) throws IOException {
		try {
			gifInfoPtr = openFd(afd.getFileDescriptor(), afd.getStartOffset(), justDecodeMetaData);
		} finally {
			afd.close();
		}
	}

	static GifInfoHandle openUri(ContentResolver resolver, Uri uri, boolean justDecodeMetaData) throws IOException {
		if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) { //workaround for #128
			return new GifInfoHandle(uri.getPath(), justDecodeMetaData);
		}
		return new GifInfoHandle(resolver.openAssetFileDescriptor(uri, "r"), justDecodeMetaData);
	}

	static native long openFd(FileDescriptor fd, long offset, boolean justDecodeMetaData) throws GifIOException;

	static native long openByteArray(byte[] bytes, boolean justDecodeMetaData) throws GifIOException;

	static native long openDirectByteBuffer(ByteBuffer buffer, boolean justDecodeMetaData) throws GifIOException;

	static native long openStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException;

	static native long openFile(String filePath, boolean justDecodeMetaData) throws GifIOException;

	private static native long renderFrame(long gifFileInPtr, Bitmap frameBuffer);

	private static native void bindSurface(long gifInfoPtr, Surface surface, long[] savedState);

	private static native void free(long gifFileInPtr);

	private static native boolean reset(long gifFileInPtr);

	private static native void setSpeedFactor(long gifFileInPtr, float factor);

	private static native String getComment(long gifFileInPtr);

	private static native int getLoopCount(long gifFileInPtr);

	private static native void setLoopCount(long gifFileInPtr, char loopCount);

	private static native long getSourceLength(long gifFileInPtr);

	private static native int getDuration(long gifFileInPtr);

	private static native int getCurrentPosition(long gifFileInPtr);

	private static native void seekToTime(long gifFileInPtr, int position, Bitmap buffer);

	private static native void seekToFrame(long gifFileInPtr, int frameNr, Bitmap buffer);

	private static native void saveRemainder(long gifFileInPtr);

	private static native long restoreRemainder(long gifFileInPtr);

	private static native long getAllocationByteCount(long gifFileInPtr);

	private static native int getNativeErrorCode(long gifFileInPtr);

	private static native int getCurrentFrameIndex(long gifFileInPtr);

	private static native int getCurrentLoop(long gifFileInPtr);

	private static native void postUnbindSurface(long gifFileInPtr);

	private static native boolean isAnimationCompleted(long gifInfoPtr);

	private static native long[] getSavedState(long gifInfoPtr);

	private static native int restoreSavedState(long gifInfoPtr, long[] savedState, Bitmap mBuffer);

	private static native int getFrameDuration(long gifInfoPtr, int index);

	private static native void setOptions(long gifInfoPtr, char sampleSize, boolean isOpaque);

	private static native int getWidth(long gifFileInPtr);

	private static native int getHeight(long gifFileInPtr);

	private static native int getNumberOfFrames(long gifInfoPtr);

	private static native boolean isOpaque(long gifInfoPtr);

	private static native void startDecoderThread(long gifInfoPtr);

	private static native void stopDecoderThread(long gifInfoPtr);

	private static native void glTexImage2D(long gifInfoPtr, int target, int level);

	private static native void glTexSubImage2D(long gifInfoPtr, int target, int level);

	private static native void seekToFrameGL(long gifInfoPtr, int index);

	private static native void initTexImageDescriptor(long gifInfoPtr);

	synchronized long renderFrame(Bitmap frameBuffer) {
		return renderFrame(gifInfoPtr, frameBuffer);
	}

	void bindSurface(Surface surface, long[] savedState) {
		bindSurface(gifInfoPtr, surface, savedState);
	}

	synchronized void recycle() {
		free(gifInfoPtr);
		gifInfoPtr = 0L;
	}

	synchronized long restoreRemainder() {
		return restoreRemainder(gifInfoPtr);
	}

	synchronized boolean reset() {
		return reset(gifInfoPtr);
	}

	synchronized void saveRemainder() {
		saveRemainder(gifInfoPtr);
	}

	synchronized String getComment() {
		return getComment(gifInfoPtr);
	}

	synchronized int getLoopCount() {
		return getLoopCount(gifInfoPtr);
	}

	void setLoopCount(@IntRange(from = 0, to = Character.MAX_VALUE) final int loopCount) {
		if (loopCount < 0 || loopCount > Character.MAX_VALUE) {
			throw new IllegalArgumentException("Loop count of range <0, 65535>");
		}
		synchronized (this) {
			setLoopCount(gifInfoPtr, (char) loopCount);
		}
	}

	synchronized long getSourceLength() {
		return getSourceLength(gifInfoPtr);
	}

	synchronized int getNativeErrorCode() {
		return getNativeErrorCode(gifInfoPtr);
	}

	void setSpeedFactor(@FloatRange(from = 0, fromInclusive = false) float factor) {
		if (factor <= 0f || Float.isNaN(factor)) {
			throw new IllegalArgumentException("Speed factor is not positive");
		}
		if (factor < 1f / Integer.MAX_VALUE) {
			factor = 1f / Integer.MAX_VALUE;
		}
		synchronized (this) {
			setSpeedFactor(gifInfoPtr, factor);
		}
	}

	synchronized int getDuration() {
		return getDuration(gifInfoPtr);
	}

	synchronized int getCurrentPosition() {
		return getCurrentPosition(gifInfoPtr);
	}

	synchronized int getCurrentFrameIndex() {
		return getCurrentFrameIndex(gifInfoPtr);
	}

	synchronized int getCurrentLoop() {
		return getCurrentLoop(gifInfoPtr);
	}

	synchronized void seekToTime(@IntRange(from = 0, to = Integer.MAX_VALUE) final int position, final Bitmap buffer) {
		seekToTime(gifInfoPtr, position, buffer);
	}

	synchronized void seekToFrame(@IntRange(from = 0, to = Integer.MAX_VALUE) final int frameIndex, final Bitmap buffer) {
		seekToFrame(gifInfoPtr, frameIndex, buffer);
	}

	synchronized long getAllocationByteCount() {
		return getAllocationByteCount(gifInfoPtr);
	}

	synchronized boolean isRecycled() {
		return gifInfoPtr == 0L;
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			recycle();
		} finally {
			super.finalize();
		}
	}

	synchronized void postUnbindSurface() {
		postUnbindSurface(gifInfoPtr);
	}

	synchronized boolean isAnimationCompleted() {
		return isAnimationCompleted(gifInfoPtr);
	}

	synchronized long[] getSavedState() {
		return getSavedState(gifInfoPtr);
	}

	synchronized int restoreSavedState(long[] savedState, Bitmap mBuffer) {
		return restoreSavedState(gifInfoPtr, savedState, mBuffer);
	}

	int getFrameDuration(@IntRange(from = 0) final int index) {
		synchronized (this) {
			if (index < 0 || index >= getNumberOfFrames(gifInfoPtr)) {
				throw new IndexOutOfBoundsException("Frame index is out of bounds");
			}
			return getFrameDuration(gifInfoPtr, index);
		}
	}

	void setOptions(char sampleSize, boolean isOpaque) {
		setOptions(gifInfoPtr, sampleSize, isOpaque);
	}

	synchronized int getWidth() {
		return getWidth(gifInfoPtr);
	}

	synchronized int getHeight() {
		return getHeight(gifInfoPtr);
	}

	synchronized int getNumberOfFrames() {
		return getNumberOfFrames(gifInfoPtr);
	}

	synchronized boolean isOpaque() {
		return isOpaque(gifInfoPtr);
	}

	void glTexImage2D(int target, int level) {
		glTexImage2D(gifInfoPtr, target, level);
	}

	void glTexSubImage2D(int target, int level) {
		glTexSubImage2D(gifInfoPtr, target, level);
	}

	void startDecoderThread() {
		startDecoderThread(gifInfoPtr);
	}

	void stopDecoderThread() {
		stopDecoderThread(gifInfoPtr);
	}

	void initTexImageDescriptor() {
		initTexImageDescriptor(gifInfoPtr);
	}

	void seekToFrameGL(@IntRange(from = 0) final int index) {
		if (index < 0 || index >= getNumberOfFrames(gifInfoPtr)) {
			throw new IndexOutOfBoundsException("Frame index is out of bounds");
		}
		seekToFrameGL(gifInfoPtr, index);
	}
}