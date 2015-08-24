package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Abstract class for all input sources, to be used with {@link GifTextureView}
 */
public abstract class InputSource {
    private boolean mIsOpaque; //TODO propagate

    InputSource() {
    }

    abstract GifInfoHandle open() throws IOException;

    final GifDrawable build(GifDrawable oldDrawable, ScheduledThreadPoolExecutor executor, boolean isRenderingAlwaysEnabled) throws IOException {
        return new GifDrawable(open(), oldDrawable, executor, isRenderingAlwaysEnabled);
    }

    final boolean isOpaque() {
        return mIsOpaque;
    }

    /**
     * Indicates whether the content of this source is opaque. GIF that is known to be opaque can
     * take a faster drawing case than non-opaque one. See {@link GifTextureView#setOpaque(boolean)}
     * for more information.<br>
     * Currently it is used only by {@link GifTextureView}, not by {@link GifDrawable}
     * @param isOpaque whether the content of this source is opaque
     * @return this InputSource
     */
    final InputSource setOpaque(boolean isOpaque) {
        mIsOpaque = isOpaque;
        return this;
    }

    /**
     * Input using {@link ByteBuffer} as a source. It must be direct.
     */
    public static final class DirectByteBufferSource extends InputSource {
        private final ByteBuffer byteBuffer;

        /**
         * Constructs new source.
         * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
         *
         * @param byteBuffer source buffer, must be direct
         */
        public DirectByteBufferSource(@NonNull ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
        }

        @Override
        GifInfoHandle open() throws GifIOException {
            return GifInfoHandle.openDirectByteBuffer(byteBuffer, false);
        }
    }

    /**
     * Input using byte array as a source.
     */
    public static final class ByteArraySource extends InputSource {
        private final byte[] bytes;

        /**
         * Constructs new source.
         * Array can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
         *
         * @param bytes source array
         */
        public ByteArraySource(@NonNull byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        GifInfoHandle open() throws GifIOException {
            return GifInfoHandle.openByteArray(bytes, false);
        }
    }

    /**
     * Input using {@link File} or path as source.
     */
    public static final class FileSource extends InputSource {
        private final String mPath;

        /**
         * Constructs new source.
         *
         * @param file source file
         */
        public FileSource(@NonNull File file) {
            mPath = file.getPath();
        }

        /**
         * Constructs new source.
         *
         * @param filePath source file path
         */
        public FileSource(@NonNull String filePath) {
            mPath = filePath;
        }

        @Override
        GifInfoHandle open() throws GifIOException {
            return GifInfoHandle.openFile(mPath, false);
        }
    }

    /**
     * Input using {@link Uri} as source.
     */
    public static final class UriSource extends InputSource {
        private final ContentResolver mContentResolver;
        private final Uri mUri;

        /**
         * Constructs new source.
         *
         * @param uri             GIF Uri, cannot be null.
         * @param contentResolver resolver, null is allowed for file:// scheme Uris only
         */
        public UriSource(@Nullable ContentResolver contentResolver, @NonNull Uri uri) {
            mContentResolver = contentResolver;
            mUri = uri;
        }

        @Override
        GifInfoHandle open() throws IOException {
            return GifInfoHandle.openUri(mContentResolver, mUri, false);
        }
    }

    /**
     * Input using android asset as source.
     */
    public static final class AssetSource extends InputSource {
        private final AssetManager mAssetManager;
        private final String mAssetName;

        /**
         * Constructs new source.
         *
         * @param assetManager AssetManager to read from
         * @param assetName    name of the asset
         */
        public AssetSource(@NonNull AssetManager assetManager, @NonNull String assetName) {
            mAssetManager = assetManager;
            mAssetName = assetName;
        }

        @Override
        GifInfoHandle open() throws IOException {
            return GifInfoHandle.openAssetFileDescriptor(mAssetManager.openFd(mAssetName), false);
        }
    }

    /**
     * Input using {@link FileDescriptor} as a source.
     */
    public static final class FileDescriptorSource extends InputSource {
        private final FileDescriptor mFd;

        /**
         * Constructs new source.
         *
         * @param fileDescriptor source file descriptor
         */
        public FileDescriptorSource(@NonNull FileDescriptor fileDescriptor) {
            mFd = fileDescriptor;
        }

        @Override
        GifInfoHandle open() throws IOException {
            return GifInfoHandle.openFd(mFd, 0, false);
        }
    }

    /**
     * Input using {@link InputStream} as a source.
     */
    public static final class InputStreamSource extends InputSource {
        private final InputStream inputStream;

        /**
         * Constructs new source.
         *
         * @param inputStream source input stream, it must support marking
         */
        public InputStreamSource(@NonNull InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        GifInfoHandle open() throws IOException {
            return GifInfoHandle.openMarkableInputStream(inputStream, false);
        }
    }

    /**
     * Input using android resource (raw or drawable) as a source.
     */
    public static class ResourcesSource extends InputSource {
        private final Resources mResources;
        private final int mResourceId;

        /**
         * Constructs new source.
         *
         * @param resources  Resources to read from
         * @param resourceId resource id
         */
        public ResourcesSource(@NonNull Resources resources, @DrawableRes @RawRes int resourceId) {
            mResources = resources;
            mResourceId = resourceId;
        }

        @Override
        GifInfoHandle open() throws IOException {
            return GifInfoHandle.openAssetFileDescriptor(mResources.openRawResourceFd(mResourceId), false);
        }
    }

    /**
     * Input using {@link AssetFileDescriptor} as a source.
     */
    public static class AssetFileDescriptorSource extends InputSource {
        private final AssetFileDescriptor mAssetFileDescriptor;

        /**
         * Constructs new source.
         * @param assetFileDescriptor source asset file descriptor.
         */
        public AssetFileDescriptorSource(@NonNull AssetFileDescriptor assetFileDescriptor) {
            mAssetFileDescriptor = assetFileDescriptor;
        }

        @Override
        GifInfoHandle open() throws IOException {
            return GifInfoHandle.openAssetFileDescriptor(mAssetFileDescriptor, false);
        }
    }

}
