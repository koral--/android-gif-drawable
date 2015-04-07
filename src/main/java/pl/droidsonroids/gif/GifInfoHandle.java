package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
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
        System.loadLibrary(BuildConfig.NATIVE_LIBRARY_NAME);
    }

    static native GifInfoHandle openFd(FileDescriptor fd, long offset, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openByteArray(byte[] bytes, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openDirectByteBuffer(ByteBuffer buffer, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openFile(String filePath, boolean justDecodeMetaData) throws GifIOException;

    private static native long renderFrame(long gifFileInPtr, Bitmap frameBuffer);

    private static native void bindSurface(long gifInfoPtr, Surface surface, int startPosition);

    private static native void free(long gifFileInPtr);

    private static native void reset(long gifFileInPtr);

    private static native void setSpeedFactor(long gifFileInPtr, float factor);

    private static native String getComment(long gifFileInPtr);

    private static native int getLoopCount(long gifFileInPtr);

    private static native long getSourceLength(long gifFileInPtr);

    private static native int getDuration(long gifFileInPtr);

    private static native int getCurrentPosition(long gifFileInPtr);

    private static native void seekToTime(long gifFileInPtr, int pos, Bitmap buffer);

    private static native void seekToFrame(long gifFileInPtr, int frameNr, Bitmap buffer);

    private static native void saveRemainder(long gifFileInPtr);

    private static native void restoreRemainder(long gifFileInPtr);

    private static native long getAllocationByteCount(long gifFileInPtr);

    private static native int getNativeErrorCode(long gifFileInPtr);

    private static native int getCurrentFrameIndex(long gifFileInPtr);

    private static native int getCurrentLoop(long gifFileInPtr);

    private static native int postUnbindSurface(long gifFileInPtr);

    static GifInfoHandle openMarkableInputStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException {
        if (!stream.markSupported()) {
            throw new IllegalArgumentException("InputStream does not support marking");
        }
        return openStream(stream, justDecodeMetaData);
    }

    static GifInfoHandle openAssetFileDescriptor(AssetFileDescriptor afd, boolean justDecodeMetaData) throws IOException {
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

    void bindSurface(Surface surface, int startPosition) {
        bindSurface(gifInfoPtr, surface, startPosition);
    }

    synchronized void recycle() {
        free(gifInfoPtr);
        gifInfoPtr = 0L;
    }

    synchronized void restoreRemainder() {
        restoreRemainder(gifInfoPtr);
    }

    synchronized void reset() {
        reset(gifInfoPtr);
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

    synchronized long getSourceLength() {
        return getSourceLength(gifInfoPtr);
    }

    synchronized int getNativeErrorCode() {
        return getNativeErrorCode(gifInfoPtr);
    }

    synchronized void setSpeedFactor(float factor) {
        if (factor <= 0f || Float.isNaN(factor)) {
            throw new IllegalArgumentException("Speed factor is not positive");
        }
        setSpeedFactor(gifInfoPtr, factor);
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

    synchronized void seekToTime(int position, Bitmap buffer) {
        seekToTime(gifInfoPtr, position, buffer);
    }

    synchronized void seekToFrame(int frameIndex, Bitmap buffer) {
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

    synchronized int postUnbindSurface() {
        return postUnbindSurface(gifInfoPtr);
    }
}