package pl.droidsonroids.gif;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

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
    final int imageCount;

    GifInfoHandle(long gifInfoPtr, int width, int height, int imageCount) {
        this.gifInfoPtr = gifInfoPtr;
        this.width = width;
        this.height = height;
        this.imageCount = imageCount;
    }

    static {
        System.loadLibrary("gif");
    }

    /**
     * Decodes a frame if needed.
     *
     * @param buffer       frame destination
     * @param gifFileInPtr GifInfo pointer
     * @return true if loop of the animation is completed
     */
    private static native long renderFrame(Bitmap buffer, long gifFileInPtr);

    static native GifInfoHandle openFd(FileDescriptor fd, long offset, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openByteArray(byte[] bytes, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openDirectByteBuffer(ByteBuffer buffer, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openFile(String filePath, boolean justDecodeMetaData) throws GifIOException;

    private static native void free(long gifFileInPtr);

    private static native void reset(long gifFileInPtr);

    private static native void setSpeedFactor(long gifFileInPtr, float factor);

    private static native String getComment(long gifFileInPtr);

    private static native int getLoopCount(long gifFileInPtr);

    private static native int getDuration(long gifFileInPtr);

    private static native int getCurrentPosition(long gifFileInPtr);

    private static native void seekToTime(long gifFileInPtr, int pos, Bitmap buffer);

    private static native void seekToFrame(long gifFileInPtr, int frameNr, Bitmap buffer);

    private static native void saveRemainder(long gifFileInPtr);

    private static native void restoreRemainder(long gifFileInPtr);

    private static native long getAllocationByteCount(long gifFileInPtr);

    private static native int getNativeErrorCode(long gifFileInPtr);

    static GifInfoHandle openMarkableInputStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException {
        if (!stream.markSupported())
            throw new IllegalArgumentException("InputStream does not support marking");
        return openStream(stream, justDecodeMetaData);
    }

    static GifInfoHandle openAssetFileDescriptor(AssetFileDescriptor afd, boolean justDecodeMetaData) throws IOException {
        try {
            return openFd(afd.getFileDescriptor(), afd.getStartOffset(), justDecodeMetaData);
        } finally {
            afd.close();
        }
    }

    synchronized long renderFrame(Bitmap buffer) {
        return renderFrame(buffer, gifInfoPtr);
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

    synchronized int getNativeErrorCode() {
        return getNativeErrorCode(gifInfoPtr);
    }

    synchronized void setSpeedFactor(float factor) {
        setSpeedFactor(gifInfoPtr, factor);
    }

    synchronized int getDuration() {
        return getDuration(gifInfoPtr);
    }

    synchronized int getCurrentPosition() {
        return getCurrentPosition(gifInfoPtr);
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
}