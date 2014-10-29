package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * TODO
 */
public class GifDrawableBuilder {
    private Source mSource;
    private GifDrawable mOldDrawable;

    public GifDrawable build() throws IOException {
        if (mSource == null)
            throw new NullPointerException("Source is not set");
        return mSource.build(mOldDrawable);
    }

    public GifDrawableBuilder with(GifDrawable drawable) {
        mOldDrawable = drawable;
        return this;
    }

    public GifDrawableBuilder from(InputStream inputStream) {
        mSource = new InputStreamSource(inputStream);
        return this;
    }

    public GifDrawableBuilder from(AssetFileDescriptor assetFileDescriptor) {
        mSource = new AssetFileDescriptorSource(assetFileDescriptor);
        return this;
    }

    public GifDrawableBuilder from(AssetManager assetManager, String assetName) {
        mSource = new AssetSource(assetManager, assetName);
        return this;
    }

    public GifDrawableBuilder from(Uri uri, ContentResolver contentResolver) {
        mSource = new UriSource(contentResolver, uri);
        return this;
    }

    //TODO add other sources
    private static class UriSource implements Source {
        private final ContentResolver mContentResolver;
        private final Uri mUri;

        private UriSource(ContentResolver contentResolver, Uri uri) {
            mContentResolver = contentResolver;
            mUri = uri;
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new AssetFileDescriptorSource(mContentResolver.openAssetFileDescriptor(mUri, "r")).build(oldDrawable);
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
            return new AssetFileDescriptorSource(mAssetManager.openFd(mAssetName)).build(oldDrawable);
        }
    }

    private static class AssetFileDescriptorSource implements Source {
        private final AssetFileDescriptor mAfd;

        private AssetFileDescriptorSource(AssetFileDescriptor assetFileDescriptor) {
            this.mAfd = assetFileDescriptor;
        }

        @Override
        public GifDrawable build(GifDrawable oldDrawable) throws IOException {
            return new GifDrawable(GifInfoHandle.openFd(mAfd.getFileDescriptor(), mAfd.getStartOffset(), false), mAfd.getLength(), oldDrawable);
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
