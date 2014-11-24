package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Locale;

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

    /**
     * Retrieves from resource.
     *
     * @param res Resources to read from
     * @param id  resource id
     * @throws android.content.res.Resources.NotFoundException if the given ID does not exist.
     * @throws java.io.IOException                             when opening failed
     * @throws NullPointerException                            if res is null
     */
    public GifAnimationMetaData(Resources res, int id) throws Resources.NotFoundException, IOException {
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
    public GifAnimationMetaData(AssetManager assets, String assetName) throws IOException {
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
    public GifAnimationMetaData(String filePath) throws IOException {
        this(GifInfoHandle.openFile(filePath, true));
    }

    /**
     * Equivalent to {@code} GifMetadata(file.getPath())}
     *
     * @param file the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if file is null
     */
    public GifAnimationMetaData(File file) throws IOException {
        this(GifInfoHandle.openFile(file.getPath(), true));
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
    public GifAnimationMetaData(InputStream stream) throws IOException {
        this(GifInfoHandle.openMarkableInputStream(stream, true));
    }

    /**
     * Retrieves metadata from AssetFileDescriptor.
     * Convenience wrapper for {@link GifAnimationMetaData#GifAnimationMetaData(FileDescriptor)}
     *
     * @param afd source
     * @throws NullPointerException if afd is null
     * @throws IOException          when opening failed
     */
    public GifAnimationMetaData(AssetFileDescriptor afd) throws IOException {
        this(GifInfoHandle.openAssetFileDescriptor(afd, true));
    }

    /**
     * Retrieves metadata from FileDescriptor
     *
     * @param fd source
     * @throws IOException          when opening failed
     * @throws NullPointerException if fd is null
     */
    public GifAnimationMetaData(FileDescriptor fd) throws IOException {
        this(GifInfoHandle.openFd(fd, 0, true));
    }

    /**
     * Retrieves metadata from byte array.<br>
     * It can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param bytes raw GIF bytes
     * @throws IOException          if bytes does not contain valid GIF data
     * @throws NullPointerException if bytes are null
     */
    public GifAnimationMetaData(byte[] bytes) throws IOException {
        this(GifInfoHandle.openByteArray(bytes, true));
    }

    /**
     * Retrieves metadata from {@link ByteBuffer}. Only direct buffers are supported.
     * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param buffer buffer containing GIF data
     * @throws IOException              if buffer does not contain valid GIF data
     * @throws IllegalArgumentException if buffer is indirect
     * @throws NullPointerException     if buffer is null
     */
    public GifAnimationMetaData(ByteBuffer buffer) throws IOException {
        this(GifInfoHandle.openDirectByteBuffer(buffer, true));
    }

    /**
     * Retrieves metadata from {@link android.net.Uri} which is resolved using {@code resolver}.
     * {@link android.content.ContentResolver#openAssetFileDescriptor(android.net.Uri, String)}
     * is used to open an Uri.
     *
     * @param uri      GIF Uri, cannot be null.
     * @param resolver resolver, cannot be null.
     * @throws IOException if resolution fails or destination is not a GIF.
     */
    public GifAnimationMetaData(ContentResolver resolver, Uri uri) throws IOException {
        this(resolver.openAssetFileDescriptor(uri, "r"));
    }

    private GifAnimationMetaData(final GifInfoHandle gifInfoHandle) {
        mLoopCount = gifInfoHandle.getLoopCount();
        mDuration = gifInfoHandle.getDuration();
        gifInfoHandle.recycle();
        mWidth = gifInfoHandle.width;
        mHeight = gifInfoHandle.height;
        mImageCount = gifInfoHandle.imageCount;
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
     * Returns loop count previously read from GIF's application extension block.
     * Defaults to 0 (infinite loop) if there is no such extension.
     *
     * @return loop count, 0 means that animation is infinite
     */
    public int getLoopCount() {
        return mLoopCount;
    }

    /**
     * Retrieves duration of one loop of the animation.
     * If there is no data (no Graphics Control Extension blocks) 0 is returned.
     * Note that one-frame GIFs can have non-zero duration defined in Graphics Control Extension block,
     * use {@link #getNumberOfFrames()} to determine if there is one or more frames.
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

    @Override
    public String toString() {
        String loopCount = mLoopCount == 0 ? "Infinity" : Integer.toString(mLoopCount);
        String suffix = String.format(Locale.US,
                "GIF: size: %dx%d, frames: %d, loops: %s, duration: %d",
                mWidth, mHeight, mImageCount, loopCount, mDuration);
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
    }

    private GifAnimationMetaData(Parcel in) {
        mLoopCount = in.readInt();
        mDuration = in.readInt();
        mHeight = in.readInt();
        mWidth = in.readInt();
        mImageCount = in.readInt();
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