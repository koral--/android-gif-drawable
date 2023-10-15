package pl.droidsonroids.gif

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.IntRange
import pl.droidsonroids.gif.InputSource.AssetFileDescriptorSource
import pl.droidsonroids.gif.InputSource.AssetSource
import pl.droidsonroids.gif.InputSource.ByteArraySource
import pl.droidsonroids.gif.InputSource.DirectByteBufferSource
import pl.droidsonroids.gif.InputSource.FileDescriptorSource
import pl.droidsonroids.gif.InputSource.ResourcesSource
import pl.droidsonroids.gif.InputSource.UriSource
import pl.droidsonroids.gif.annotations.Beta
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * The base class for using the builder pattern with subclasses.
 * @see [link](https://community.oracle.com/blogs/emcmanus/2010/10/24/using-builder-pattern-subclasses)
</T> */
abstract class GifDrawableInit<T : GifDrawableInit<T>?> {
    /**
     * Getter for the input source.
     *
     * @return Current [InputSource] or null if it wasn't set.
     */
    private var inputSource: InputSource? = null

    /**
     * Getter for the old drawable.
     *
     * @return Instance of the old [GifDrawable] or null if it wasn't set.
     */
    private var oldDrawable: GifDrawable? = null

    /**
     * Getter for the executor.
     *
     * @return [ScheduledThreadPoolExecutor] or null if it wasn't set.
     */
    private var executor: ScheduledThreadPoolExecutor? = null

    /**
     * @return True if rendering of the next frame is scheduled after drawing, false otherwise.
     */
    private var isRenderingTriggeredOnDraw = true

    /**
     * Getter for the GIF options.
     *
     * @return [GifOptions].
     */
    val options = GifOptions()

    /**
     * Used in accordance with `getThis()` pattern.
     *
     * @return The builder type.
     * @see [link](http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html.FAQ205)
     */
    protected abstract fun self(): T

    /**
     * Sample size controlling subsampling, see [GifOptions.setInSampleSize] for more details.
     * Note that this call will overwrite sample size set previously by [.options]
     *
     * @param sampleSize the sample size
     * @return this builder instance, to chain calls
     */
    fun sampleSize(@IntRange(from = 1, to = Character.MAX_VALUE.code.toLong()) sampleSize: Int): T {
        options.setInSampleSize(sampleSize)
        return self()
    }

    /**
     * Appropriate constructor wrapper. Must be preceded by on of `from()` calls.
     *
     * @return new drawable instance
     * @throws IOException when creation fails
     */
    @Throws(IOException::class)
    fun build(): GifDrawable {
        if (inputSource == null) {
            throw NullPointerException("Source is not set")
        }
        return inputSource!!.createGifDrawable(
            oldDrawable,
            executor,
            isRenderingTriggeredOnDraw,
            options
        )
    }

    /**
     * Sets drawable to be reused when creating new one.
     *
     * @param drawable drawable to be reused
     * @return this builder instance, to chain calls
     */
    fun with(drawable: GifDrawable?): T {
        oldDrawable = drawable
        return self()
    }

    /**
     * Sets thread pool size for rendering tasks.
     * Warning: custom executor set by [.taskExecutor]
     * will be overwritten after setting pool size
     *
     * @param threadPoolSize size of the pool
     * @return this builder instance, to chain calls
     */
    fun threadPoolSize(threadPoolSize: Int): T {
        executor = ScheduledThreadPoolExecutor(threadPoolSize)
        return self()
    }

    /**
     * Sets or resets executor for rendering tasks.
     * Warning: value set by [.threadPoolSize] will not be taken into account after setting executor
     *
     * @param executor executor to be used or null for default (each drawable instance has its own executor)
     * @return this builder instance, to chain calls
     */
    fun taskExecutor(executor: ScheduledThreadPoolExecutor?): T {
        this.executor = executor
        return self()
    }

    /**
     * Sets whether rendering of the next frame is scheduled after drawing current one (so animation
     * will be paused if drawing does not happen) or just after rendering frame (no matter if it is
     * drawn or not). However animation will never run if drawable is set to not visible. See
     * [GifDrawable.isVisible] for more information about drawable visibility.
     * By default this option is enabled. Note that drawing does not happen if view containing
     * drawable is obscured. Disabling this option will prevent that however battery draining will be
     * higher.
     *
     * @param isRenderingTriggeredOnDraw whether rendering of the next frame is scheduled after drawing (default)
     * current one or just after it is rendered
     * @return this builder instance, to chain calls
     */
    private fun renderingTriggeredOnDraw(isRenderingTriggeredOnDraw: Boolean): T {
        this.isRenderingTriggeredOnDraw = isRenderingTriggeredOnDraw
        return self()
    }

    /**
     * Equivalent to [.renderingTriggeredOnDraw]. This method does not follow naming convention
     * and is preserved for backwards compatibility only.
     *
     * @param isRenderingTriggeredOnDraw whether rendering of the next frame is scheduled after drawing (default)
     * current one or just after it is rendered
     * @return this builder instance, to chain calls
     */
    fun setRenderingTriggeredOnDraw(isRenderingTriggeredOnDraw: Boolean): T {
        return renderingTriggeredOnDraw(isRenderingTriggeredOnDraw)
    }

    /**
     * Indicates whether the content of this source is opaque. GIF that is known to be opaque can
     * take a faster drawing case than non-opaque one. See [GifTextureView.setOpaque]
     * for more information.<br></br>
     * Currently it is used only by [GifTextureView], not by [GifDrawable].
     *
     *
     * Note that this call will overwrite sample size set previously by [.sampleSize]
     *
     * @param options null-ok; options controlling parameters like subsampling and opacity
     * @return this builder instance, to chain calls
     */
    @Beta
    fun options(options: GifOptions): T {
        options.setFrom(options)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param inputStream data source
     * @return this builder instance, to chain calls
     */
    fun from(inputStream: InputStream): T {
        inputSource = InputSource.InputStreamSource(inputStream)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param assetFileDescriptor data source
     * @return this builder instance, to chain calls
     */
    fun from(assetFileDescriptor: AssetFileDescriptor): T {
        inputSource = AssetFileDescriptorSource(assetFileDescriptor)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param fileDescriptor data source
     * @return this builder instance, to chain calls
     */
    fun from(fileDescriptor: FileDescriptor): T {
        inputSource = FileDescriptorSource(fileDescriptor)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param assetManager assets source
     * @param assetName    asset file name
     * @return this builder instance, to chain calls
     */
    fun from(assetManager: AssetManager, assetName: String): T {
        inputSource = AssetSource(assetManager, assetName)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param uri             data source
     * @param contentResolver resolver used to query `uri`
     * @return this builder instance, to chain calls
     */
    fun from(contentResolver: ContentResolver, uri: Uri): T {
        inputSource = UriSource(contentResolver, uri)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param file data source
     * @return this builder instance, to chain calls
     */
    fun from(file: File): T {
        inputSource = InputSource.FileSource(file)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param filePath data source
     * @return this builder instance, to chain calls
     */
    fun from(filePath: String): T {
        inputSource = InputSource.FileSource(filePath)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param bytes data source
     * @return this builder instance, to chain calls
     */
    fun from(bytes: ByteArray): T {
        inputSource = ByteArraySource(bytes)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param byteBuffer data source
     * @return this builder instance, to chain calls
     */
    fun from(byteBuffer: ByteBuffer): T {
        inputSource = DirectByteBufferSource(byteBuffer)
        return self()
    }

    /**
     * Wrapper of [pl.droidsonroids.gif.GifDrawable.GifDrawable]
     *
     * @param resources  Resources to read from
     * @param resourceId resource id (data source)
     * @return this builder instance, to chain calls
     */
    fun from(resources: Resources, resourceId: Int): T {
        inputSource = ResourcesSource(resources, resourceId)
        return self()
    }
}