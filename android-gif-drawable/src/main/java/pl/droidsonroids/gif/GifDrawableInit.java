package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import pl.droidsonroids.gif.annotations.Beta;

/**
 * The base class for using the builder pattern with subclasses.
 *
 * @param <T> The type of the builder that is a subclass of this class.
 * @see <a href="https://community.oracle.com/blogs/emcmanus/2010/10/24/using-builder-pattern-subclasses">link</a>
 */
public abstract class GifDrawableInit<T extends GifDrawableInit<T>> {

    private InputSource mInputSource;
    private GifDrawable mOldDrawable;
    private ScheduledThreadPoolExecutor mExecutor;
    private boolean mIsRenderingTriggeredOnDraw = true;
    private final GifOptions mOptions = new GifOptions();

    /**
     * Used in accordance with `getThis()` pattern.
     *
     * @return The builder type.
     * @see <a href="http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205">link</a>
     */
    protected abstract T self();

    /**
     * Sample size controlling subsampling, see {@link GifOptions#setInSampleSize(int)} for more details.
     * Note that this call will overwrite sample size set previously by {@link #options(GifOptions)}
     *
     * @param sampleSize the sample size
     * @return this builder instance, to chain calls
     */
    public T sampleSize(@IntRange(from = 1, to = Character.MAX_VALUE) final int sampleSize) {
        mOptions.setInSampleSize(sampleSize);
        return self();
    }

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
        return mInputSource.createGifDrawable(mOldDrawable, mExecutor, mIsRenderingTriggeredOnDraw, mOptions);
    }

    /**
     * Sets drawable to be reused when creating new one.
     *
     * @param drawable drawable to be reused
     * @return this builder instance, to chain calls
     */
    public T with(GifDrawable drawable) {
        mOldDrawable = drawable;
        return self();
    }

    /**
     * Sets thread pool size for rendering tasks.
     * Warning: custom executor set by {@link #taskExecutor(java.util.concurrent.ScheduledThreadPoolExecutor)}
     * will be overwritten after setting pool size
     *
     * @param threadPoolSize size of the pool
     * @return this builder instance, to chain calls
     */
    public T threadPoolSize(int threadPoolSize) {
        mExecutor = new ScheduledThreadPoolExecutor(threadPoolSize);
        return self();
    }

    /**
     * Sets or resets executor for rendering tasks.
     * Warning: value set by {@link #threadPoolSize(int)} will not be taken into account after setting executor
     *
     * @param executor executor to be used or null for default (each drawable instance has its own executor)
     * @return this builder instance, to chain calls
     */
    public T taskExecutor(ScheduledThreadPoolExecutor executor) {
        mExecutor = executor;
        return self();
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
    public T renderingTriggeredOnDraw(boolean isRenderingTriggeredOnDraw) {
        mIsRenderingTriggeredOnDraw = isRenderingTriggeredOnDraw;
        return self();
    }

    /**
     * Equivalent to {@link #renderingTriggeredOnDraw(boolean)}. This method does not follow naming convention
     * and is preserved for backwards compatibility only.
     *
     * @param isRenderingTriggeredOnDraw whether rendering of the next frame is scheduled after drawing (default)
     *                                   current one or just after it is rendered
     * @return this builder instance, to chain calls
     */
    public T setRenderingTriggeredOnDraw(boolean isRenderingTriggeredOnDraw) {
        return renderingTriggeredOnDraw(isRenderingTriggeredOnDraw);
    }

    /**
     * Indicates whether the content of this source is opaque. GIF that is known to be opaque can
     * take a faster drawing case than non-opaque one. See {@link GifTextureView#setOpaque(boolean)}
     * for more information.<br>
     * Currently it is used only by {@link GifTextureView}, not by {@link GifDrawable}.
     * <p>
     * Note that this call will overwrite sample size set previously by {@link #sampleSize(int)}
     *
     * @param options null-ok; options controlling parameters like subsampling and opacity
     * @return this builder instance, to chain calls
     */
    @Beta
    public T options(@Nullable GifOptions options) {
        mOptions.setFrom(options);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.InputStream)}
     *
     * @param inputStream data source
     * @return this builder instance, to chain calls
     */
    public T from(InputStream inputStream) {
        mInputSource = new InputSource.InputStreamSource(inputStream);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.AssetFileDescriptor)}
     *
     * @param assetFileDescriptor data source
     * @return this builder instance, to chain calls
     */
    public T from(AssetFileDescriptor assetFileDescriptor) {
        mInputSource = new InputSource.AssetFileDescriptorSource(assetFileDescriptor);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.FileDescriptor)}
     *
     * @param fileDescriptor data source
     * @return this builder instance, to chain calls
     */
    public T from(FileDescriptor fileDescriptor) {
        mInputSource = new InputSource.FileDescriptorSource(fileDescriptor);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.AssetManager, java.lang.String)}
     *
     * @param assetManager assets source
     * @param assetName    asset file name
     * @return this builder instance, to chain calls
     */
    public T from(AssetManager assetManager, String assetName) {
        mInputSource = new InputSource.AssetSource(assetManager, assetName);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.ContentResolver, android.net.Uri)}
     *
     * @param uri             data source
     * @param contentResolver resolver used to query {@code uri}
     * @return this builder instance, to chain calls
     */
    public T from(ContentResolver contentResolver, Uri uri) {
        mInputSource = new InputSource.UriSource(contentResolver, uri);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.io.File)}
     *
     * @param file data source
     * @return this builder instance, to chain calls
     */
    public T from(File file) {
        mInputSource = new InputSource.FileSource(file);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.lang.String)}
     *
     * @param filePath data source
     * @return this builder instance, to chain calls
     */
    public T from(String filePath) {
        mInputSource = new InputSource.FileSource(filePath);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(byte[])}
     *
     * @param bytes data source
     * @return this builder instance, to chain calls
     */
    public T from(byte[] bytes) {
        mInputSource = new InputSource.ByteArraySource(bytes);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(java.nio.ByteBuffer)}
     *
     * @param byteBuffer data source
     * @return this builder instance, to chain calls
     */
    public T from(ByteBuffer byteBuffer) {
        mInputSource = new InputSource.DirectByteBufferSource(byteBuffer);
        return self();
    }

    /**
     * Wrapper of {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(android.content.res.Resources, int)}
     *
     * @param resources  Resources to read from
     * @param resourceId resource id (data source)
     * @return this builder instance, to chain calls
     */
    public T from(Resources resources, int resourceId) {
        mInputSource = new InputSource.ResourcesSource(resources, resourceId);
        return self();
    }

    /**
     * Getter for the input source.
     *
     * @return Current {@link InputSource} or null if it wasn't set.
     */
    public InputSource getInputSource() {
        return mInputSource;
    }

    /**
     * Getter for the old drawable.
     *
     * @return Instance of the old {@link GifDrawable} or null if it wasn't set.
     */
    public GifDrawable getOldDrawable() {
        return mOldDrawable;
    }

    /**
     * Getter for the executor.
     *
     * @return {@link ScheduledThreadPoolExecutor} or null if it wasn't set.
     */
    public ScheduledThreadPoolExecutor getExecutor() {
        return mExecutor;
    }

    /**
     * @return True if rendering of the next frame is scheduled after drawing, false otherwise.
     */
    public boolean isRenderingTriggeredOnDraw() {
        return mIsRenderingTriggeredOnDraw;
    }

    /**
     * Getter for the GIF options.
     *
     * @return {@link GifOptions}.
     */
    public GifOptions getOptions() {
        return mOptions;
    }
}
