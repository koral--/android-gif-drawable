package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Locale;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import pl.droidsonroids.gif.annotations.Beta;

/**
 * Lightweight version of {@link pl.droidsonroids.gif.GifDrawable} used to retrieve metadata of GIF only,
 * without having to allocate the memory for its pixels.
 */
public class GifAnimationMetaData implements Serializable, Parcelable {
	private static final long serialVersionUID = 5692363926580237325L;

	private final int mLoopCount;
	private final int mDuration;
	private final int mHeight;
	private final int mWidth;
	private final int mImageCount;
	private final long mPixelsBytesCount;
	private final long mMetadataBytesCount;

	/**
	 * Retrieves from resource.
	 *
	 * @param res Resources to read from
	 * @param id  resource id
	 * @throws android.content.res.Resources.NotFoundException if the given ID does not exist.
	 * @throws java.io.IOException                             when opening failed
	 * @throws NullPointerException                            if res is null
	 */
	public GifAnimationMetaData(@NonNull Resources res, @RawRes @DrawableRes int id) throws Resources.NotFoundException, IOException {
		this(res.openRawResourceFd(id));
	}

	/**
	 * Retrieves metadata from asset.
	 *
	 * @param assets    AssetManager to read from
	 * @param assetName name of the asset
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if assets or assetName is null
	 */
	public GifAnimationMetaData(@NonNull AssetManager assets, @NonNull String assetName) throws IOException {
		this(assets.openFd(assetName));
	}

	/**
	 * Constructs metadata from given file path.<br>
	 * Only metadata is read, no graphic data is decoded here.
	 * In practice can be called from main thread. However it will violate
	 * {@link android.os.StrictMode} policy if disk reads detection is enabled.<br>
	 *
	 * @param filePath path to the GIF file
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if filePath is null
	 */
	public GifAnimationMetaData(@NonNull String filePath) throws IOException {
		this(new GifInfoHandle(filePath));
	}

	/**
	 * Equivalent to {@code} GifMetadata(file.getPath())}
	 *
	 * @param file the GIF file
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if file is null
	 */
	public GifAnimationMetaData(@NonNull File file) throws IOException {
		this(file.getPath());
	}

	/**
	 * Retrieves metadata from InputStream.
	 * InputStream must support marking, IllegalArgumentException will be thrown otherwise.
	 *
	 * @param stream stream to read from
	 * @throws IOException              when opening failed
	 * @throws IllegalArgumentException if stream does not support marking
	 * @throws NullPointerException     if stream is null
	 */
	public GifAnimationMetaData(@NonNull InputStream stream) throws IOException {
		this(new GifInfoHandle(stream));
	}

	/**
	 * Retrieves metadata from AssetFileDescriptor.
	 * Convenience wrapper for {@link GifAnimationMetaData#GifAnimationMetaData(FileDescriptor)}
	 *
	 * @param afd source
	 * @throws NullPointerException if afd is null
	 * @throws IOException          when opening failed
	 */
	public GifAnimationMetaData(@NonNull AssetFileDescriptor afd) throws IOException {
		this(new GifInfoHandle(afd));
	}

	/**
	 * Retrieves metadata from FileDescriptor
	 *
	 * @param fd source
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if fd is null
	 */
	public GifAnimationMetaData(@NonNull FileDescriptor fd) throws IOException {
		this(new GifInfoHandle(fd));
	}

	/**
	 * Retrieves metadata from byte array.<br>
	 * It can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
	 *
	 * @param bytes raw GIF bytes
	 * @throws IOException          if bytes does not contain valid GIF data
	 * @throws NullPointerException if bytes are null
	 */
	public GifAnimationMetaData(@NonNull byte[] bytes) throws IOException {
		this(new GifInfoHandle(bytes));
	}

	/**
	 * Retrieves metadata from {@link ByteBuffer}. Only direct buffers are supported.
	 * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
	 *
	 * @param buffer buffer containing GIF data
	 * @throws IOException          if buffer does not contain valid GIF data or is indirect
	 * @throws NullPointerException if buffer is null
	 */
	public GifAnimationMetaData(@NonNull ByteBuffer buffer) throws IOException {
		this(new GifInfoHandle(buffer));
	}

	/**
	 * Retrieves metadata from {@link android.net.Uri} which is resolved using {@code resolver}.
	 * {@link android.content.ContentResolver#openAssetFileDescriptor(android.net.Uri, String)}
	 * is used to open an Uri.
	 *
	 * @param uri      GIF Uri, cannot be null.
	 * @param resolver resolver, null is allowed for file:// scheme Uris only
	 * @throws IOException if resolution fails or destination is not a GIF.
	 */
	public GifAnimationMetaData(@Nullable ContentResolver resolver, @NonNull Uri uri) throws IOException {
		this(GifInfoHandle.openUri(resolver, uri));
	}

	private GifAnimationMetaData(final GifInfoHandle gifInfoHandle) {
		mLoopCount = gifInfoHandle.getLoopCount();
		mDuration = gifInfoHandle.getDuration();
		mWidth = gifInfoHandle.getWidth();
		mHeight = gifInfoHandle.getHeight();
		mImageCount = gifInfoHandle.getNumberOfFrames();
		mMetadataBytesCount = gifInfoHandle.getMetadataByteCount();
		mPixelsBytesCount = gifInfoHandle.getAllocationByteCount();
		gifInfoHandle.recycle();
	}

	/**
	 * @return width od the GIF canvas in pixels
	 */
	public int getWidth() {
		return mWidth;
	}

	/**
	 * @return height od the GIF canvas in pixels
	 */
	public int getHeight() {
		return mHeight;
	}

	/**
	 * @return number of frames in GIF, at least one
	 */
	public int getNumberOfFrames() {
		return mImageCount;
	}

	/**
	 * See {@link GifDrawable#getLoopCount()}
	 *
	 * @return loop count, 0 means that animation is infinite
	 */
	public int getLoopCount() {
		return mLoopCount;
	}

	/**
	 * See {@link GifDrawable#getDuration()}
	 *
	 * @return duration of of one loop the animation in milliseconds. Result is always multiple of 10.
	 */
	public int getDuration() {
		return mDuration;
	}

	/**
	 * @return true if GIF is animated (has at least 2 frames and positive duration), false otherwise
	 */
	public boolean isAnimated() {
		return mImageCount > 1 && mDuration > 0;
	}

	/**
	 * Like {@link GifDrawable#getAllocationByteCount()} but does not include memory needed for backing {@link android.graphics.Bitmap}.
	 * {@code Bitmap} in {@code GifDrawable} may be allocated at the time of creation or existing one may be reused if {@link GifDrawableInit#with(GifDrawable)}
	 * is used.
	 * This method assumes no subsampling (sample size = 1).<br>
	 * To calculate allocation byte count of {@link GifDrawable} created from the same input source {@link #getDrawableAllocationByteCount(GifDrawable, int)}
	 * can be used.
	 *
	 * @return possible size of the memory needed to store pixels excluding backing {@link android.graphics.Bitmap} and assuming no subsampling
	 */
	public long getAllocationByteCount() {
		return mPixelsBytesCount;
	}

	/**
	 * Like {@link #getAllocationByteCount()} but includes also backing {@link android.graphics.Bitmap} and takes sample size into account.
	 *
	 * @param oldDrawable optional old drawable to be reused, pass {@code null} if there is no one
	 * @param sampleSize  sample size, pass {@code 1} if not using subsampling
	 * @return possible size of the memory needed to store pixels
	 * @throws IllegalArgumentException if sample size out of range
	 */
	@Beta
	public long getDrawableAllocationByteCount(@Nullable GifDrawable oldDrawable, @IntRange(from = 1, to = Character.MAX_VALUE) int sampleSize) {
		if (sampleSize < 1 || sampleSize > Character.MAX_VALUE) {
			throw new IllegalStateException("Sample size " + sampleSize + " out of range <1, " + Character.MAX_VALUE + ">");
		}

		final int sampleSizeFactor = sampleSize * sampleSize;
		final long bufferSize;
		if (oldDrawable != null && !oldDrawable.mBuffer.isRecycled()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				bufferSize = oldDrawable.mBuffer.getAllocationByteCount();
			} else {
				bufferSize = oldDrawable.getFrameByteCount();
			}
		} else {
			bufferSize = (mWidth * mHeight * 4) / sampleSizeFactor;
		}
		return (mPixelsBytesCount / sampleSizeFactor) + bufferSize;
	}

	/**
	 * See{@link GifDrawable#getMetadataAllocationByteCount()}
	 *
	 * @return maximum possible size of the allocated memory needed to store metadata
	 */
	public long getMetadataAllocationByteCount() {
		return mMetadataBytesCount;
	}

	@Override
	@NonNull
	public String toString() {
		final String loopCount = mLoopCount == 0 ? "Infinity" : Integer.toString(mLoopCount);
		final String suffix = String.format(Locale.ENGLISH, "GIF: size: %dx%d, frames: %d, loops: %s, duration: %d", mWidth, mHeight, mImageCount, loopCount, mDuration);
		return isAnimated() ? "Animated " + suffix : suffix;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(mLoopCount);
		dest.writeInt(mDuration);
		dest.writeInt(mHeight);
		dest.writeInt(mWidth);
		dest.writeInt(mImageCount);
		dest.writeLong(mMetadataBytesCount);
		dest.writeLong(mPixelsBytesCount);
	}

	private GifAnimationMetaData(Parcel in) {
		mLoopCount = in.readInt();
		mDuration = in.readInt();
		mHeight = in.readInt();
		mWidth = in.readInt();
		mImageCount = in.readInt();
		mMetadataBytesCount = in.readLong();
		mPixelsBytesCount = in.readLong();
	}

	public static final Parcelable.Creator<GifAnimationMetaData> CREATOR = new Parcelable.Creator<GifAnimationMetaData>() {
		public GifAnimationMetaData createFromParcel(Parcel source) {
			return new GifAnimationMetaData(source);
		}

		public GifAnimationMetaData[] newArray(int size) {
			return new GifAnimationMetaData[size];
		}
	};
}