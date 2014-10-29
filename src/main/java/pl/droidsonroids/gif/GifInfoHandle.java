package pl.droidsonroids.gif;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

class GifInfoHandle {
    volatile long gifInfoPtr;
    final int width;
    final int height;
    final int imageCount;

    GifInfoHandle(long gifInfoPtr, int width, int height, int imageCount) {
        this.gifInfoPtr = gifInfoPtr;
        this.width = width;
        this.height = height;
        this.imageCount = imageCount;
    }

    boolean isEqualSized(GifInfoHandle another) {
        return gifInfoPtr != 0L && width == another.width && height == another.height;
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
    static native long renderFrame(Bitmap buffer, long gifFileInPtr);

    static native GifInfoHandle openFd(FileDescriptor fd, long offset, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openByteArray(byte[] bytes, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openDirectByteBuffer(ByteBuffer buffer, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openFile(String filePath, boolean justDecodeMetaData) throws GifIOException;

    static native void free(long gifFileInPtr);

    static native void reset(long gifFileInPtr);

    static native void setSpeedFactor(long gifFileInPtr, float factor);

    static native String getComment(long gifFileInPtr);

    static native int getLoopCount(long gifFileInPtr);

    static native int getDuration(long gifFileInPtr);

    static native int getCurrentPosition(long gifFileInPtr);

    static native void seekToTime(long gifFileInPtr, int pos, Bitmap buffer);

    static native void seekToFrame(long gifFileInPtr, int frameNr, Bitmap buffer);

    static native void saveRemainder(long gifFileInPtr);

    static native void restoreRemainder(long gifFileInPtr);

    static native long getAllocationByteCount(long gifFileInPtr);

    static native int getNativeErrorCode(long gifFileInPtr);

    static GifInfoHandle openMarkableInputStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException {
        if (!stream.markSupported())
            throw new IllegalArgumentException("InputStream does not support marking");
        return openStream(stream, justDecodeMetaData);
    }

    static GifInfoHandle openAssetFileDescriptor(AssetFileDescriptor afd, boolean justDecodeMetaData) throws IOException {
        try {
            return openFd(afd.getFileDescriptor(), afd.getStartOffset(), justDecodeMetaData);
        } catch (IOException ex) {
            afd.close();
            throw ex;
        }
    }
}
