package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Builder for {@link pl.droidsonroids.gif.GifDrawable} which can be used to construct new drawables
 * by reusing old ones.
 */
public class GifDrawableBuilder {
    private Source mSource;
    private GifDrawable mOldDrawable;

    /**
     * Appropriate constructor wrapper. Must be preceded by on of {@code from()} calls.
     *
     * @return new drawable instance
     * @throws IOException when creation fails
     */
    public GifDrawable build() throws IOException {
        if (mSource == null)
            throw new NullPointerException("Source is not set");
        return mSource.build(mOldDrawable);
    }

    /**
     * Sets drawable to be reused when creating new one
     * @param drawable drawable to be reused
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder with(GifDrawable drawable) {
        mOldDrawable = drawable;
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.InputStream)}
     * @param inputStream data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(InputStream inputStream) {
        mSource = new InputStreamSource(inputStream);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.AssetFileDescriptor)}
     * @param assetFileDescriptor data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(AssetFileDescriptor assetFileDescriptor) {
        mSource = new FileDescriptorSource(assetFileDescriptor);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.FileDescriptor)}
     * @param fileDescriptor data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(FileDescriptor fileDescriptor) {
        mSource = new FileDescriptorSource(fileDescriptor);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.AssetManager, java.lang.String)}
     * @param assetManager assets source
     * @param assetName asset file name
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(AssetManager assetManager, String assetName) {
        mSource = new AssetSource(assetManager, assetName);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.ContentResolver, android.net.Uri)}
     *
     * @param uri             data source
     * @param contentResolver resolver used to query {@code uri}
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(ContentResolver contentResolver, Uri uri) {
        mSource = new UriSource(contentResolver, uri);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.File)}
     * @param file data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(File file) {
        mSource = new FileSource(file);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.lang.String)}
     * @param filePath data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(String filePath) {
        mSource = new FileSource(filePath);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(byte[])}
     * @param bytes data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(byte[] bytes) {
        mSource = new ByteArraySource(bytes);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.nio.ByteBuffer)}
     * @param byteBuffer data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(ByteBuffer byteBuffer) {
        mSource = new ByteBufferSource(byteBuffer);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.Resources, int)}
     * @param resources Resources to read from
     * @param resourceId resource id (data source)
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(Resources resources, int resourceId) {
        mSource = new FileDescriptorSource(resources, resourceId);
        return this;
    }

    private static class ByteBufferSource implements Source {
        private final ByteBuffer byteBuffer;

        private ByteBufferSource(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new GifDrawable(GifInfoHandle.openDirectByteBuffer(byteBuffer, false), byteBuffer.capacity(), oldDrawable);
        }
    }

    private static class ByteArraySource implements Source {
        private final byte[] bytes;

        private ByteArraySource(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new GifDrawable(GifInfoHandle.openByteArray(bytes, false), bytes.length, oldDrawable);
        }
    }

    private static class FileSource implements Source {
        private final File mFile;

        private FileSource(File file) {
            mFile = file;
        }

        private FileSource(String filePath) {
            mFile = new File(filePath);
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new GifDrawable(GifInfoHandle.openFile(mFile.getPath(), false), mFile.length(), oldDrawable);
        }
    }

    private static class UriSource implements Source {
        private final ContentResolver mContentResolver;
        private final Uri mUri;

        private UriSource(ContentResolver contentResolver, Uri uri) {
            mContentResolver = contentResolver;
            mUri = uri;
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new FileDescriptorSource(mContentResolver.openAssetFileDescriptor(mUri, "r")).build(oldDrawable);
        }
    }

    private static class AssetSource implements Source {
        private final AssetManager mAssetManager;
        private final String mAssetName;

        private AssetSource(AssetManager assetManager, String assetName) {
            mAssetManager = assetManager;
            mAssetName = assetName;
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new FileDescriptorSource(mAssetManager.openFd(mAssetName)).build(oldDrawable);
        }
    }

    private static class FileDescriptorSource implements Source {
        private final FileDescriptor mFd;
        private final long length, startOffset;

        private FileDescriptorSource(AssetFileDescriptor assetFileDescriptor) {
            mFd = assetFileDescriptor.getFileDescriptor();
            length = assetFileDescriptor.getLength();
            startOffset = assetFileDescriptor.getStartOffset();
        }

        private FileDescriptorSource(FileDescriptor fileDescriptor) {
            mFd = fileDescriptor;
            length = -1L;
            startOffset = 0;
        }

        private FileDescriptorSource(Resources resources, int resourceId) {
            this(resources.openRawResourceFd(resourceId));
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new GifDrawable(GifInfoHandle.openFd(mFd, startOffset, false), length, oldDrawable);
        }
    }

    private static class InputStreamSource implements Source {
        private final InputStream inputStream;

        private InputStreamSource(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new GifDrawable(GifInfoHandle.openMarkableInputStream(inputStream, false), -1L, oldDrawable);
        }
    }

    private static interface Source {
        GifDrawable build(GifDrawable oldDrawable) throws IOException;
    }
}
