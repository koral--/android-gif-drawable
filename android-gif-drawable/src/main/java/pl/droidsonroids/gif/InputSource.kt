package pl.droidsonroids.gif

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import pl.droidsonroids.gif.GifIOException
import pl.droidsonroids.gif.GifInfoHandle.Companion.openUri
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * Abstract class for all input sources, to be used with [GifTextureView]
 */
abstract class InputSource private constructor() {
    @Throws(IOException::class)
    abstract fun open(): GifInfoHandle

    @Throws(IOException::class)
    fun createGifDrawable(
        oldDrawable: GifDrawable?, executor: ScheduledThreadPoolExecutor?,
        isRenderingAlwaysEnabled: Boolean, options: GifOptions
    ): GifDrawable {
        return GifDrawable(
            createHandleWith(options),
            oldDrawable,
            executor,
            isRenderingAlwaysEnabled
        )
    }

    @Throws(IOException::class)
    fun createHandleWith(options: GifOptions): GifInfoHandle {
        val handle = open()
        handle.setOptions(options.inSampleSize, options.inIsOpaque)
        return handle
    }

    /**
     * Input using [ByteBuffer] as a source. It must be direct.
     */
    class DirectByteBufferSource
    /**
     * Constructs new source.
     * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param byteBuffer source buffer, must be direct
     */(private val byteBuffer: ByteBuffer) : InputSource() {
        @Throws(GifIOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(byteBuffer)
        }
    }

    /**
     * Input using byte array as a source.
     */
    class ByteArraySource
    /**
     * Constructs new source.
     * Array can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param bytes source array
     */(private val bytes: ByteArray) : InputSource() {
        @Throws(GifIOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(bytes)
        }
    }

    /**
     * Input using [File] or path as source.
     */
    class FileSource : InputSource {
        private val mPath: String

        /**
         * Constructs new source.
         *
         * @param file source file
         */
        constructor(file: File) {
            mPath = file.path
        }

        /**
         * Constructs new source.
         *
         * @param filePath source file path
         */
        constructor(filePath: String) {
            mPath = filePath
        }

        @Throws(GifIOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(mPath)
        }
    }

    /**
     * Input using [Uri] as source.
     */
    class UriSource
    /**
     * Constructs new source.
     *
     * @param uri             GIF Uri, cannot be null.
     * @param contentResolver resolver, null is allowed for file:// scheme Uris only
     */(private val mContentResolver: ContentResolver?, private val mUri: Uri) : InputSource() {
        @Throws(IOException::class)
        override fun open(): GifInfoHandle {
            return openUri(mContentResolver, mUri)
        }
    }

    /**
     * Input using android asset as source.
     */
    class AssetSource
    /**
     * Constructs new source.
     *
     * @param assetManager AssetManager to read from
     * @param assetName    name of the asset
     */(private val mAssetManager: AssetManager, private val mAssetName: String) : InputSource() {
        @Throws(IOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(mAssetManager.openFd(mAssetName))
        }
    }

    /**
     * Input using [FileDescriptor] as a source.
     */
    class FileDescriptorSource
    /**
     * Constructs new source.
     *
     * @param fileDescriptor source file descriptor
     */(private val mFd: FileDescriptor) : InputSource() {
        @Throws(IOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(mFd)
        }
    }

    /**
     * Input using [InputStream] as a source.
     */
    class InputStreamSource
    /**
     * Constructs new source.
     *
     * @param inputStream source input stream, it must support marking
     */(private val inputStream: InputStream) : InputSource() {
        @Throws(IOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(inputStream)
        }
    }

    /**
     * Input using android resource (raw or drawable) as a source.
     */
    class ResourcesSource
    /**
     * Constructs new source.
     *
     * @param resources  Resources to read from
     * @param resourceId resource id
     */(
        private val mResources: Resources,
        @param:RawRes @param:DrawableRes private val mResourceId: Int
    ) : InputSource() {
        @Throws(IOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(mResources.openRawResourceFd(mResourceId))
        }
    }

    /**
     * Input using [AssetFileDescriptor] as a source.
     */
    class AssetFileDescriptorSource
    /**
     * Constructs new source.
     *
     * @param assetFileDescriptor source asset file descriptor.
     */(private val mAssetFileDescriptor: AssetFileDescriptor) : InputSource() {
        @Throws(IOException::class)
        override fun open(): GifInfoHandle {
            return GifInfoHandle(mAssetFileDescriptor)
        }
    }
}