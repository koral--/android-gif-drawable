package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;

import android.system.ErrnoException;
import android.system.Os;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Native library wrapper
 */
final class GifInfoHandle {
	static {
		LibraryLoader.loadLibrary();
	}

	/**
	 * Pointer to native structure. Access must be synchronized, heap corruption may occur otherwise
	 * when {@link #recycle()} is called during another operation.
	 */
	private volatile long gifInfoPtr;

	GifInfoHandle() {
	}

	GifInfoHandle(FileDescriptor fileDescriptor) throws GifIOException {
		gifInfoPtr = openFileDescriptor(fileDescriptor, 0, true);
	}

	GifInfoHandle(byte[] bytes) throws GifIOException {
		gifInfoPtr = openByteArray(bytes);
	}

	GifInfoHandle(ByteBuffer buffer) throws GifIOException {
		gifInfoPtr = openDirectByteBuffer(buffer);
	}

	GifInfoHandle(String filePath) throws GifIOException {
		gifInfoPtr = openFile(filePath);
	}

	GifInfoHandle(InputStream stream) throws GifIOException {
		if (!stream.markSupported()) {
			throw new IllegalArgumentException("InputStream does not support marking");
		}
		gifInfoPtr = openStream(stream);
	}

	GifInfoHandle(AssetFileDescriptor afd) throws IOException {
		try {
			gifInfoPtr = openFileDescriptor(afd.getFileDescriptor(), afd.getStartOffset(), false);
		} finally {
			try {
				afd.close();
			} catch (IOException ignored) {
				//no-op
			}
		}
	}

	private static long openFileDescriptor(FileDescriptor fileDescriptor, long offset, boolean closeOriginalDescriptor) throws GifIOException {
		final int nativeFileDescriptor;
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
			try {
				nativeFileDescriptor = getNativeFileDescriptor(fileDescriptor, closeOriginalDescriptor);
			} catch (Exception e) { //cannot catch ErrnoException due to VerifyError on API <= 19
				throw new GifIOException(GifError.OPEN_FAILED.errorCode, e.getMessage());
			}
		} else {
			nativeFileDescriptor = extractNativeFileDescriptor(fileDescriptor, closeOriginalDescriptor);
		}
		return openNativeFileDescriptor(nativeFileDescriptor, offset);
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private static int getNativeFileDescriptor(FileDescriptor fileDescriptor, boolean closeOriginalDescriptor) throws GifIOException, ErrnoException {
		try {
			final int nativeFileDescriptor = createTempNativeFileDescriptor();
			Os.dup2(fileDescriptor, nativeFileDescriptor);
			return nativeFileDescriptor;
		} finally {
			if (closeOriginalDescriptor) {
				Os.close(fileDescriptor);
			}
		}
	}

	static GifInfoHandle openUri(ContentResolver resolver, Uri uri) throws IOException {
		if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) { //workaround for #128
			return new GifInfoHandle(uri.getPath());
		}
		final AssetFileDescriptor assetFileDescriptor = resolver.openAssetFileDescriptor(uri, "r");
		if (assetFileDescriptor == null) {
			throw new IOException("Could not open AssetFileDescriptor for " + uri);
		}
		return new GifInfoHandle(assetFileDescriptor);
	}

	static native long openNativeFileDescriptor(int fd, long offset) throws GifIOException;

	static native int extractNativeFileDescriptor(FileDescriptor fileDescriptor, boolean closeOriginalDescriptor) throws GifIOException;

	static native int createTempNativeFileDescriptor() throws GifIOException;

	static native long openByteArray(byte[] bytes) throws GifIOException;

	static native long openDirectByteBuffer(ByteBuffer buffer) throws GifIOException;

	static native long openStream(InputStream stream) throws GifIOException;

	static native long openFile(String filePath) throws GifIOException;

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

	private static native long getMetadataByteCount(long gifFileInPtr);

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

	synchronized void seekToFrame(@IntRange(from = 0, to = Integer.MAX_VALUE) final int frameIndex,
			final Bitmap buffer) {
		seekToFrame(gifInfoPtr, frameIndex, buffer);
	}

	synchronized long getAllocationByteCount() {
		return getAllocationByteCount(gifInfoPtr);
	}

	synchronized long getMetadataByteCount() {
		return getMetadataByteCount(gifInfoPtr);
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

	synchronized int getFrameDuration(@IntRange(from = 0) final int index) {
		throwIfFrameIndexOutOfBounds(index);
		return getFrameDuration(gifInfoPtr, index);
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
		throwIfFrameIndexOutOfBounds(index);
		seekToFrameGL(gifInfoPtr, index);
	}

	private void throwIfFrameIndexOutOfBounds(@IntRange(from = 0) final int index) {
		final int numberOfFrames = getNumberOfFrames(gifInfoPtr);
		if (index < 0 || index >= numberOfFrames) {
			throw new IndexOutOfBoundsException("Frame index is not in range <0;" + numberOfFrames + '>');
		}
	}
}