package pl.droidsonroids.gif

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.view.Surface
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import pl.droidsonroids.gif.LibraryLoader.loadLibrary
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Char
import kotlin.Exception
import kotlin.IndexOutOfBoundsException
import kotlin.Int
import kotlin.Long
import kotlin.LongArray
import kotlin.String
import kotlin.Throwable
import kotlin.Throws
import kotlin.code
import kotlin.require
import kotlin.synchronized

/**
 * Native library wrapper
 */
class GifInfoHandle {
    /**
     * Pointer to native structure. Access must be synchronized, heap corruption may occur otherwise
     * when [.recycle] is called during another operation.
     */
    @Volatile
    private var gifInfoPtr: Long = 0

    constructor()
    constructor(fileDescriptor: FileDescriptor) {
        gifInfoPtr = openFileDescriptor(fileDescriptor, 0, true)
    }

    constructor(bytes: ByteArray?) {
        gifInfoPtr = openByteArray(bytes)
    }

    constructor(buffer: ByteBuffer?) {
        gifInfoPtr = openDirectByteBuffer(buffer)
    }

    constructor(filePath: String?) {
        gifInfoPtr = openFile(filePath)
    }

    constructor(stream: InputStream) {
        require(stream.markSupported()) { "InputStream does not support marking" }
        gifInfoPtr = openStream(stream)
    }

    constructor(afd: AssetFileDescriptor) {
        gifInfoPtr = try {
            openFileDescriptor(afd.fileDescriptor, afd.startOffset, false)
        } finally {
            try {
                afd.close()
            } catch (ignored: IOException) {
                //no-op
            }
        }
    }

    @Synchronized
    fun renderFrame(frameBuffer: Bitmap?): Long {
        return renderFrame(gifInfoPtr, frameBuffer)
    }

    fun bindSurface(surface: Surface, savedState: LongArray?) {
        bindSurface(gifInfoPtr, surface, savedState)
    }

    @Synchronized
    fun recycle() {
        free(gifInfoPtr)
        gifInfoPtr = 0L
    }

    @Synchronized
    fun restoreRemainder(): Long {
        return restoreRemainder(gifInfoPtr)
    }

    @Synchronized
    fun reset(): Boolean {
        return reset(gifInfoPtr)
    }

    @Synchronized
    fun saveRemainder() {
        saveRemainder(gifInfoPtr)
    }

    @get:Synchronized
    val comment: String
        get() = getComment(gifInfoPtr)

    @get:Synchronized
    var loopCount: Int
        get() = getLoopCount(gifInfoPtr)
        set(loopCount) {
            require(!(loopCount < 0 || loopCount > Character.MAX_VALUE.code)) { "Loop count of range <0, 65535>" }
            synchronized(this) { setLoopCount(gifInfoPtr, loopCount.toChar()) }
        }

    @get:Synchronized
    val inputSourceByteCount: Long
        get() = getSourceLength(gifInfoPtr)
/**
 * Returns length of the input source obtained at the opening time or -1 if
 * length cannot be determined. Returned value does not change during runtime.
 * If GifDrawable is constructed from [InputStream] -1 is always returned.
 * In case of byte array and [ByteBuffer] length is always known.
 * In other cases length -1 can be returned if length cannot be determined.
 *
 * @return number of bytes backed by input source or -1 if it is unknown
 */

    @get:Synchronized
    val nativeErrorCode: Int
        get() = getNativeErrorCode(gifInfoPtr)

    fun setSpeedFactor(@FloatRange(from = 0.0, fromInclusive = false) factor: Float) {
        var floatFactor = factor
        require(!(floatFactor <= 0f || floatFactor.isNaN())) { "Speed factor is not positive" }
        if (floatFactor < 1f / Int.MAX_VALUE) {
            floatFactor = 1f / Int.MAX_VALUE
        }
        synchronized(this) { setSpeedFactor(gifInfoPtr, floatFactor) }
    }

    @get:Synchronized
    val duration: Int
        get() = getDuration(gifInfoPtr)

    @get:Synchronized
    val currentPosition: Int
        get() = getCurrentPosition(gifInfoPtr)

    @get:Synchronized
    val currentFrameIndex: Int
        get() = getCurrentFrameIndex(gifInfoPtr)

    @get:Synchronized
    val currentLoop: Int
        get() = getCurrentLoop(gifInfoPtr)

    @Synchronized
    fun seekToTime(@IntRange(from = 0, to = Int.MAX_VALUE.toLong()) position: Int, buffer: Bitmap) {
        seekToTime(gifInfoPtr, position, buffer)
    }

    @Synchronized
    fun seekToFrame(
        @IntRange(from = 0, to = Int.MAX_VALUE.toLong()) frameIndex: Int,
        buffer: Bitmap
    ) {
        seekToFrame(gifInfoPtr, frameIndex, buffer)
    }

    @get:Synchronized
    val allocationByteCount: Long
        get() = getAllocationByteCount(gifInfoPtr)

    @get:Synchronized
    val metadataAllocationByteCount: Long
        get() = getMetadataByteCount(gifInfoPtr)

    @get:Synchronized
    val isRecycled: Boolean
        get() = gifInfoPtr == 0L

    @Throws(Throwable::class)
    protected fun finalize() {
        recycle()
    }

    @Synchronized
    fun postUnbindSurface() {
        postUnbindSurface(gifInfoPtr)
    }

    @get:Synchronized
    val isAnimationCompleted: Boolean
        get() = isAnimationCompleted(gifInfoPtr)

    @get:Synchronized
    val savedState: LongArray
        get() = getSavedState(gifInfoPtr)

    @Synchronized
    fun restoreSavedState(savedState: LongArray, mBuffer: Bitmap?): Int {
        return restoreSavedState(gifInfoPtr, savedState, mBuffer)
    }

    @Synchronized
    fun getFrameDuration(@IntRange(from = 0) index: Int): Int {
        throwIfFrameIndexOutOfBounds(index)
        return getFrameDuration(gifInfoPtr, index)
    }

    fun setOptions(sampleSize: Char, isOpaque: Boolean) {
        setOptions(gifInfoPtr, sampleSize, isOpaque)
    }

    @get:Synchronized
    val width: Int
        get() = getWidth(gifInfoPtr)

    @get:Synchronized
    val height: Int
        get() = getHeight(gifInfoPtr)

    @get:Synchronized
    val numberOfFrames: Int
        get() = getNumberOfFrames(gifInfoPtr)

    @get:Synchronized
    val isOpaque: Boolean
        get() = isOpaque(gifInfoPtr)

    fun glTexImage2D(target: Int, level: Int) {
        glTexImage2D(gifInfoPtr, target, level)
    }

    fun glTexSubImage2D(target: Int, level: Int) {
        glTexSubImage2D(gifInfoPtr, target, level)
    }

    fun startDecoderThread() {
        startDecoderThread(gifInfoPtr)
    }

    fun stopDecoderThread() {
        stopDecoderThread(gifInfoPtr)
    }

    fun initTexImageDescriptor() {
        initTexImageDescriptor(gifInfoPtr)
    }

    fun seekToFrameGL(@IntRange(from = 0) index: Int) {
        throwIfFrameIndexOutOfBounds(index)
        seekToFrameGL(gifInfoPtr, index)
    }

    private fun throwIfFrameIndexOutOfBounds(@IntRange(from = 0) index: Int) {
        val numberOfFrames = getNumberOfFrames(gifInfoPtr)
        if (index < 0 || index >= numberOfFrames) {
            throw IndexOutOfBoundsException("Frame index is not in range <0;$numberOfFrames>")
        }
    }

    companion object {
        init {
            loadLibrary()
        }

        @Throws(GifIOException::class)
        private fun openFileDescriptor(
            fileDescriptor: FileDescriptor,
            offset: Long,
            closeOriginalDescriptor: Boolean
        ): Long {
            val nativeFileDescriptor = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                try {
                    getNativeFileDescriptor(fileDescriptor, closeOriginalDescriptor)
                } catch (e: Exception) { //cannot catch ErrnoException due to VerifyError on API <= 19
                    throw GifIOException(GifError.OPEN_FAILED.errorCode, e.message)
                }
            } else {
                extractNativeFileDescriptor(fileDescriptor, closeOriginalDescriptor)
            }
            return openNativeFileDescriptor(nativeFileDescriptor, offset)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        @Throws(
            GifIOException::class, ErrnoException::class
        )
        private fun getNativeFileDescriptor(
            fileDescriptor: FileDescriptor,
            closeOriginalDescriptor: Boolean
        ): Int {
            return try {
                val nativeFileDescriptor = createTempNativeFileDescriptor()
                Os.dup2(fileDescriptor, nativeFileDescriptor)
                nativeFileDescriptor
            } finally {
                if (closeOriginalDescriptor) {
                    Os.close(fileDescriptor)
                }
            }
        }

        @Throws(IOException::class)
        fun openUri(resolver: ContentResolver, uri: Uri): GifInfoHandle {
            if (ContentResolver.SCHEME_FILE == uri.scheme) { //workaround for #128
                return GifInfoHandle(uri.path)
            }
            val assetFileDescriptor = resolver.openAssetFileDescriptor(uri, "r")
                ?: throw IOException("Could not open AssetFileDescriptor for $uri")
            return GifInfoHandle(assetFileDescriptor)
        }

        @Throws(GifIOException::class)
        external fun openNativeFileDescriptor(fd: Int, offset: Long): Long
        @Throws(GifIOException::class)
        external fun extractNativeFileDescriptor(
            fileDescriptor: FileDescriptor?,
            closeOriginalDescriptor: Boolean
        ): Int

        @Throws(GifIOException::class)
        external fun createTempNativeFileDescriptor(): Int
        @Throws(GifIOException::class)
        external fun openByteArray(bytes: ByteArray?): Long
        @Throws(GifIOException::class)
        external fun openDirectByteBuffer(buffer: ByteBuffer?): Long
        @Throws(GifIOException::class)
        external fun openStream(stream: InputStream?): Long
        @Throws(GifIOException::class)
        external fun openFile(filePath: String?): Long
        private external fun renderFrame(gifFileInPtr: Long, frameBuffer: Bitmap?): Long
        private external fun bindSurface(gifInfoPtr: Long, surface: Surface, savedState: LongArray?)
        private external fun free(gifFileInPtr: Long)
        private external fun reset(gifFileInPtr: Long): Boolean
        private external fun setSpeedFactor(gifFileInPtr: Long, factor: Float)
        private external fun getComment(gifFileInPtr: Long): String
        private external fun getLoopCount(gifFileInPtr: Long): Int
        private external fun setLoopCount(gifFileInPtr: Long, loopCount: Char)
        private external fun getSourceLength(gifFileInPtr: Long): Long
        private external fun getDuration(gifFileInPtr: Long): Int
        private external fun getCurrentPosition(gifFileInPtr: Long): Int
        private external fun seekToTime(gifFileInPtr: Long, position: Int, buffer: Bitmap)
        private external fun seekToFrame(gifFileInPtr: Long, frameNr: Int, buffer: Bitmap)
        private external fun saveRemainder(gifFileInPtr: Long)
        private external fun restoreRemainder(gifFileInPtr: Long): Long
        private external fun getAllocationByteCount(gifFileInPtr: Long): Long
        private external fun getMetadataByteCount(gifFileInPtr: Long): Long
        private external fun getNativeErrorCode(gifFileInPtr: Long): Int
        private external fun getCurrentFrameIndex(gifFileInPtr: Long): Int
        private external fun getCurrentLoop(gifFileInPtr: Long): Int
        private external fun postUnbindSurface(gifFileInPtr: Long)
        private external fun isAnimationCompleted(gifInfoPtr: Long): Boolean
        private external fun getSavedState(gifInfoPtr: Long): LongArray
        private external fun restoreSavedState(
            gifInfoPtr: Long,
            savedState: LongArray,
            mBuffer: Bitmap?
        ): Int

        private external fun getFrameDuration(gifInfoPtr: Long, index: Int): Int
        private external fun setOptions(gifInfoPtr: Long, sampleSize: Char, isOpaque: Boolean)
        private external fun getWidth(gifFileInPtr: Long): Int
        private external fun getHeight(gifFileInPtr: Long): Int
        private external fun getNumberOfFrames(gifInfoPtr: Long): Int
        private external fun isOpaque(gifInfoPtr: Long): Boolean
        private external fun startDecoderThread(gifInfoPtr: Long)
        private external fun stopDecoderThread(gifInfoPtr: Long)
        private external fun glTexImage2D(gifInfoPtr: Long, target: Int, level: Int)
        private external fun glTexSubImage2D(gifInfoPtr: Long, target: Int, level: Int)
        private external fun seekToFrameGL(gifInfoPtr: Long, index: Int)
        private external fun initTexImageDescriptor(gifInfoPtr: Long)
    }
}