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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import static pl.droidsonroids.gif.InputSource.*;

/**
 * Builder for {@link pl.droidsonroids.gif.GifDrawable} which can be used to construct new drawables
 * by reusing old ones.
 */
public class GifDrawableBuilder {
    /**
     * Constructs empty builder.
     */
    public GifDrawableBuilder() {
    }

    private InputSource mInputSource;
    private GifDrawable mOldDrawable;
    private ScheduledThreadPoolExecutor mExecutor;
    private boolean mIsRenderingTriggeredOnDraw = true;

    /**
     * Appropriate constructor wrapper. Must be preceded by on of {@code from()} calls.
     *
     * @return new drawable instance
     * @throws IOException when creation fails
     */
    public GifDrawable build() throws IOException {
        if (mInputSource == null) {
            throw new NullPointerException("Source is not set");
        }
        return mInputSource.build(mOldDrawable, mExecutor, mIsRenderingTriggeredOnDraw);
    }

    /**
     * Sets drawable to be reused when creating new one. Currently it works only on {@link android.os.Build.VERSION_CODES#KITKAT}
     * and newer, on older API levels call has no effect.
     *
     * @param drawable drawable to be reused
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder with(GifDrawable drawable) {
        mOldDrawable = drawable;
        return this;
    }

    /**
     * Sets thread pool size for rendering tasks.
     * Warning: custom executor set by {@link #taskExecutor(java.util.concurrent.ScheduledThreadPoolExecutor)}
     * will be overwritten after setting pool size
     *
     * @param threadPoolSize size of the pool
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder threadPoolSize(int threadPoolSize) {
        mExecutor = new ScheduledThreadPoolExecutor(threadPoolSize);
        return this;
    }

    /**
     * Sets or resets executor for rendering tasks.
     * Warning: value set by {@link #threadPoolSize(int)} will not be taken into account after setting executor
     *
     * @param executor executor to be used or null for default (each drawable instance has its own executor)
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder taskExecutor(ScheduledThreadPoolExecutor executor) {
        mExecutor = executor;
        return this;
    }

    /**
     * Sets whether rendering of the next frame is scheduled after drawing current one (so animation
     * will be paused if drawing does not happen) or just after rendering frame (no matter if it is
     * drawn or not). However animation will never run if drawable is set to not visible. See
     * {@link GifDrawable#isVisible()} for more information about drawable visibility.
     * By default this option is enabled. Note that drawing does not happen if view containing
     * drawable is obscured. Disabling this option will prevent that however battery draining will be
     * higher.
     *
     * @param isRenderingTriggeredOnDraw whether rendering of the next frame is scheduled after drawing (default)
     *                                   current one or just after it is rendered
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder setRenderingTriggeredOnDraw(boolean isRenderingTriggeredOnDraw) {
        mIsRenderingTriggeredOnDraw = isRenderingTriggeredOnDraw;
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.InputStream)}
     *
     * @param inputStream data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(InputStream inputStream) {
        mInputSource = new InputStreamSource(inputStream);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.AssetFileDescriptor)}
     *
     * @param assetFileDescriptor data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(AssetFileDescriptor assetFileDescriptor) {
        mInputSource = new AssetFileDescriptorSource(assetFileDescriptor);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.FileDescriptor)}
     *
     * @param fileDescriptor data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(FileDescriptor fileDescriptor) {
        mInputSource = new FileDescriptorSource(fileDescriptor);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.AssetManager, java.lang.String)}
     *
     * @param assetManager assets source
     * @param assetName    asset file name
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(AssetManager assetManager, String assetName) {
        mInputSource = new AssetSource(assetManager, assetName);
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
        mInputSource = new UriSource(contentResolver, uri);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.File)}
     *
     * @param file data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(File file) {
        mInputSource = new FileSource(file);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.lang.String)}
     *
     * @param filePath data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(String filePath) {
        mInputSource = new FileSource(filePath);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(byte[])}
     *
     * @param bytes data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(byte[] bytes) {
        mInputSource = new ByteArraySource(bytes);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.nio.ByteBuffer)}
     *
     * @param byteBuffer data source
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(ByteBuffer byteBuffer) {
        mInputSource = new DirectByteBufferSource(byteBuffer);
        return this;
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.Resources, int)}
     *
     * @param resources  Resources to read from
     * @param resourceId resource id (data source)
     * @return this builder instance, to chain calls
     */
    public GifDrawableBuilder from(Resources resources, int resourceId) {
        mInputSource = new ResourcesSource(resources, resourceId);
        return this;
    }
}
