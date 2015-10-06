package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
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
    final int width;
    final int height;
    final int frameCount;

    @SuppressWarnings("SameParameterValue")
    //invoked from native code
    private GifInfoHandle(long gifInfoPtr, int width, int height, int frameCount) {
        this.gifInfoPtr = gifInfoPtr;
        this.width = width;
        this.height = height;
        this.frameCount = frameCount;
    }

    static final GifInfoHandle NULL_INFO = new GifInfoHandle(0, 0, 0, 0);

    static {
        System.loadLibrary("pl_droidsonroids_gif");
    }

    static native GifInfoHandle openFd(FileDescriptor fd, long offset, boolean justDecodeMetaData) throws
            GifIOException;

    static native GifInfoHandle openByteArray(byte[] bytes, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openDirectByteBuffer(ByteBuffer buffer, boolean justDecodeMetaData) throws
            GifIOException;

    static native GifInfoHandle openStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openFile(String filePath, boolean justDecodeMetaData) throws GifIOException;

    private static native long renderFrame(long gifFileInPtr, Bitmap frameBuffer);

    private static native void bindSurface(long gifInfoPtr, Surface surface, long[] savedState, boolean isOpaque);

    private static native void free(long gifFileInPtr);

    private static native boolean reset(long gifFileInPtr);

    private static native void setSpeedFactor(long gifFileInPtr, float factor);

    private static native String getComment(long gifFileInPtr);

    private static native int getLoopCount(long gifFileInPtr);

    private static native void setLoopCount(long gifFileInPtr, int loopCount);

    private static native long getSourceLength(long gifFileInPtr);

    private static native int getDuration(long gifFileInPtr);

    private static native int getCurrentPosition(long gifFileInPtr);

    private static native void seekToTime(long gifFileInPtr, int pos, Bitmap buffer);

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

    static GifInfoHandle openMarkableInputStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException {
        if (!stream.markSupported()) {
            throw new IllegalArgumentException("InputStream does not support marking");
        }
        return openStream(stream, justDecodeMetaData);
    }

    static GifInfoHandle openAssetFileDescriptor(AssetFileDescriptor afd, boolean justDecodeMetaData) throws
            IOException {
        try {
            return openFd(afd.getFileDescriptor(), afd.getStartOffset(), justDecodeMetaData);
        } finally {
            afd.close();
        }
    }

    static GifInfoHandle openUri(ContentResolver resolver, Uri uri, boolean justDecodeMetaData) throws IOException {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) //workaround for #128
        {
            return openFile(uri.getPath(), justDecodeMetaData);
        }
        return openAssetFileDescriptor(resolver.openAssetFileDescriptor(uri, "r"), justDecodeMetaData);
    }

    synchronized long renderFrame(Bitmap frameBuffer) {
        return renderFrame(gifInfoPtr, frameBuffer);
    }

    void bindSurface(Surface surface, long[] savedState, boolean isOpaque) {
        bindSurface(gifInfoPtr, surface, savedState, isOpaque);
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

    void setLoopCount(final int loopCount) {
        if (loopCount < 0 || loopCount > 0xFFFF) {
            throw new IllegalArgumentException("Loop count of range <0, 65535>");
        }
        synchronized (this) {
            setLoopCount(gifInfoPtr, loopCount);
        }
    }

    synchronized long getSourceLength() {
        return getSourceLength(gifInfoPtr);
    }

    synchronized int getNativeErrorCode() {
        return getNativeErrorCode(gifInfoPtr);
    }

    void setSpeedFactor(float factor) {
        if (factor <= 0f || Float.isNaN(factor)) {
            throw new IllegalArgumentException("Speed factor is not positive");
        }
        if (factor < 1 / Integer.MAX_VALUE) {
            factor = 1 / Integer.MAX_VALUE;
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

    synchronized int getFrameDuration(final int index) {
        if (index < 0 || index >= frameCount) {
            throw new IndexOutOfBoundsException("Frame index is out of bounds");
        }
        return getFrameDuration(gifInfoPtr, index);
    }
}