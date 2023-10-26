package pl.droidsonroids.gif

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import androidx.annotation.RawRes
import pl.droidsonroids.gif.GifAnimationMetaData
import pl.droidsonroids.gif.GifInfoHandle.Companion.openUri
import pl.droidsonroids.gif.annotations.Beta
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.jvm.Throws

/**
 * Lightweight version of [pl.droidsonroids.gif.GifDrawable] used to retrieve metadata of GIF only,
 * without having to allocate the memory for its pixels.
 */
class GifAnimationMetaData : Serializable, Parcelable {
    /**
     * See [GifDrawable.getLoopCount]
     *
     * @return loop count, 0 means that animation is infinite
     */
    val loopCount: Int

    /**
     * See [GifDrawable.getDuration]
     *
     * @return duration of one loop the animation in milliseconds. Result is always multiple of 10.
     */
    val duration: Int

    /**
     * @return height of the GIF canvas in pixels
     */
    val height: Int

    /**
     * @return width of the GIF canvas in pixels
     */
    val width: Int

    /**
     * @return number of frames in GIF, at least one
     */
    val numberOfFrames: Int

    /**
     * Like [GifDrawable.getAllocationByteCount] but does not include memory needed for backing [android.graphics.Bitmap].
     * `Bitmap` in `GifDrawable` may be allocated at the time of creation or existing one may be reused if [GifDrawableInit.with]
     * is used.
     * This method assumes no subsampling (sample size = 1).<br></br>
     * To calculate allocation byte count of [GifDrawable] created from the same input source [.getDrawableAllocationByteCount]
     * can be used.
     *
     * @return possible size of the memory needed to store pixels excluding backing [android.graphics.Bitmap] and assuming no subsampling
     */
    val allocationByteCount: Long

    /**
     * See[GifDrawable.getAllocationByteCount] ()}
     *
     * @return maximum possible size of the allocated memory needed to store metadata
     */
    val metadataAllocationByteCount: Long

    /**
     * Retrieves from resource.
     *
     * @param res Resources to read from
     * @param id  resource id
     * @throws android.content.res.Resources.NotFoundException if the given ID does not exist.
     * @throws java.io.IOException                             when opening failed
     * @throws NullPointerException                            if res is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(res: Resources, @RawRes @DrawableRes id: Int) : this(res.openRawResourceFd(id))

    /**
     * Retrieves metadata from asset.
     *
     * @param assets    AssetManager to read from
     * @param assetName name of the asset
     * @throws IOException          when opening failed
     * @throws NullPointerException if assets or assetName is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(assets: AssetManager, assetName: String) : this(assets.openFd(assetName))

    /**
     * Constructs metadata from given file path.<br></br>
     * Only metadata is read, no graphic data is decoded here.
     * In practice can be called from main thread. However it will violate
     * [android.os.StrictMode] policy if disk reads detection is enabled.<br></br>
     *
     * @param filePath path to the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if filePath is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(filePath: String) : this(GifInfoHandle(filePath))

    /**
     * Equivalent to `GifMetadata(file.getPath())`
     *
     * @param file the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if file is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(file: File) : this(file.path)

    /**
     * Retrieves metadata from InputStream.
     * InputStream must support marking, IllegalArgumentException will be thrown otherwise.
     *
     * @param stream stream to read from
     * @throws IOException              when opening failed
     * @throws IllegalArgumentException if stream does not support marking
     * @throws NullPointerException     if stream is null
     */
    @Throws(IOException::class, NullPointerException::class, IllegalArgumentException::class)
    constructor(stream: InputStream) : this(GifInfoHandle(stream))

    /**
     * Retrieves metadata from AssetFileDescriptor.
     * Convenience wrapper for [GifAnimationMetaData.GifAnimationMetaData]
     *
     * @param afd source
     * @throws NullPointerException if afd is null
     * @throws IOException          when opening failed
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(afd: AssetFileDescriptor) : this(GifInfoHandle(afd))

    /**
     * Retrieves metadata from FileDescriptor
     *
     * @param fd source
     * @throws IOException          when opening failed
     * @throws NullPointerException if fd is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(fd: FileDescriptor) : this(GifInfoHandle(fd))

    /**
     * Retrieves metadata from byte array.<br></br>
     * It can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param bytes raw GIF bytes
     * @throws IOException          if bytes does not contain valid GIF data
     * @throws NullPointerException if bytes are null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(bytes: ByteArray) : this(GifInfoHandle(bytes))

    /**
     * Retrieves metadata from [ByteBuffer]. Only direct buffers are supported.
     * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param buffer buffer containing GIF data
     * @throws IOException          if buffer does not contain valid GIF data or is indirect
     * @throws NullPointerException if buffer is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(buffer: ByteBuffer) : this(GifInfoHandle(buffer))

    /**
     * Retrieves metadata from [android.net.Uri] which is resolved using `resolver`.
     * [android.content.ContentResolver.openAssetFileDescriptor]
     * is used to open an Uri.
     *
     * @param uri      GIF Uri, cannot be null.
     * @param resolver resolver, null is allowed for file:// scheme Uris only
     * @throws IOException if resolution fails or destination is not a GIF.
     */
    @Throws(IOException::class)
    constructor(resolver: ContentResolver?, uri: Uri) : this(
        openUri(
            resolver, uri
        )
    )

    private constructor(gifInfoHandle: GifInfoHandle) {
        loopCount = gifInfoHandle.loopCount
        duration = gifInfoHandle.duration
        width = gifInfoHandle.width
        height = gifInfoHandle.height
        numberOfFrames = gifInfoHandle.numberOfFrames
        metadataAllocationByteCount = gifInfoHandle.metadataAllocationByteCount
        allocationByteCount = gifInfoHandle.allocationByteCount
        gifInfoHandle.recycle()
    }

    /**
     * @return true if GIF is animated (has at least 2 frames and positive duration), false otherwise
     */
    val isAnimated: Boolean
        get() = numberOfFrames > 1 && duration > 0

    /**
     * Like [.getAllocationByteCount] but includes also backing [android.graphics.Bitmap] and takes sample size into account.
     *
     * @param oldDrawable optional old drawable to be reused, pass `null` if there is no one
     * @param sampleSize  sample size, pass `1` if not using subsampling
     * @return possible size of the memory needed to store pixels
     * @throws IllegalArgumentException if sample size out of range
     */
    @Throws(IllegalArgumentException::class)
    @Beta
    fun getDrawableAllocationByteCount(
        oldDrawable: GifDrawable?,
        @IntRange(
            from = 1,
            to = Character.MAX_VALUE.code.toLong()
        ) sampleSize: Int
    ): Long {
        check(!(sampleSize < 1 || sampleSize > Character.MAX_VALUE.code)) { "Sample size " + sampleSize + " out of range <1, " + Character.MAX_VALUE + ">" }
        val sampleSizeFactor = sampleSize * sampleSize
        val bufferSize: Long = if (oldDrawable != null && !oldDrawable.mBuffer.isRecycled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                oldDrawable.mBuffer.allocationByteCount.toLong()
            } else {
                oldDrawable.frameByteCount.toLong()
            }
        } else {
            (width * height * 4 / sampleSizeFactor).toLong()
        }
        return allocationByteCount / sampleSizeFactor + bufferSize
    }

    override fun toString(): String {
        val loopCount = if (loopCount == 0) "Infinity" else loopCount.toString()
        val suffix = String.format(
            Locale.ENGLISH,
            "GIF: size: %dx%d, frames: %d, loops: %s, duration: %d",
            width,
            height,
            numberOfFrames,
            loopCount,
            duration
        )
        return if (isAnimated) "Animated $suffix" else suffix
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(loopCount)
        dest.writeInt(duration)
        dest.writeInt(height)
        dest.writeInt(width)
        dest.writeInt(numberOfFrames)
        dest.writeLong(metadataAllocationByteCount)
        dest.writeLong(allocationByteCount)
    }

    private constructor(parcel: Parcel) {
        loopCount = parcel.readInt()
        duration = parcel.readInt()
        height = parcel.readInt()
        width = parcel.readInt()
        numberOfFrames = parcel.readInt()
        metadataAllocationByteCount = parcel.readLong()
        allocationByteCount = parcel.readLong()
    }

    companion object {
        private const val serialVersionUID = 5692363926580237325L

        @JvmField
        val CREATOR: Creator<GifAnimationMetaData?> = object : Creator<GifAnimationMetaData?> {
            override fun createFromParcel(source: Parcel): GifAnimationMetaData {
                return GifAnimationMetaData(source)
            }

            override fun newArray(size: Int): Array<GifAnimationMetaData?> {
                return arrayOfNulls(size)
            }
        }
    }
}
