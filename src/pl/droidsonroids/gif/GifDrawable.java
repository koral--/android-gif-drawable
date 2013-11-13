package pl.droidsonroids.gif;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

/**
 * A {@link Drawable} which can be used to hold GIF images, especially animations.
 * Basic GIF metadata can be also obtained.  
 * @author koral--
 */
public class GifDrawable extends Drawable implements Animatable
{
	static
	{
		System.loadLibrary( "gif" );
	}

	private native synchronized int renderFrame ( int[] pixels, int gifFileInPtr );

	private native int openFd ( int[] metaData, FileDescriptor fd, long offset );

	private native int openStream ( int[] metaData, InputStream stream );

	private native int openFile ( int[] metaData, String filePath );

	private native void free ( int gifFileInPtr );

	private native synchronized String getComment ( int gifFileInPtr );

	private native synchronized int getLoopCount ( int gifFileInPtr );

	private volatile int mGifInfoPtr;
	private final Paint mPaint = new Paint( Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG );
	private volatile boolean mIsRunning = true;

	private final int[] mColors;
	private final int[] mMetaData = new int[ 4 ];//[w,h,imageCount,errorCode]
	private InputStream mStream;

	/**
	 * Creates drawable from resource.
	 * @param res Resources to read from
	 * @param id resource id
	 * @throws NotFoundException  if the given ID does not exist.
	 * @throws IOException when opening failed
	 */
	public GifDrawable ( Resources res, int id ) throws NotFoundException, IOException
	{
		this( res.openRawResourceFd( id ) );
	}

	/**
	 * Creates drawable from asset.
	 * @param assets AssetManager to read from
	 * @param assetName name of the asset
	 * @throws IOException when opening failed
	 */
	public GifDrawable ( AssetManager assets, String assetName ) throws IOException
	{
		this( assets.openFd( assetName ) );
	}

	/**
	 * Constructs drawable from given file path.<br>
	 * Only metadata is read, no graphic data is decoded here.
	 * In practice can be called from main thread. However it will violate
	 * {@link StrictMode} policy if disk reads detection is enabled.<br>
	 * @param filePath path to the GIF file
	 * @throws IOException when opening failed
	 */
	public GifDrawable ( String filePath ) throws IOException
	{
		mGifInfoPtr = openFile( mMetaData, filePath );
		mColors = new int[ mMetaData[ 0 ] * mMetaData[ 1 ] ];
		checkError();
	}

	/**
	 * Eqivalent to {@code} GifDrawable(file.getPath())}
	 * @param file the GIF file 
	 * @throws IOException when opening failed
	 */
	public GifDrawable ( File file ) throws IOException
	{
		this( file.getPath() );
	}

	/**
	 * Creates drawable from InputStream.
	 * InputStream must support marking, IOException will be thrown otherwise.
	 * @param stream stream to read from
	 * @throws IOException when opening failed
	 */
	public GifDrawable ( InputStream stream ) throws IOException
	{
		if ( !stream.markSupported() )
			throw new IOException( "InputStream must support marking" );
		mStream = stream;
		mGifInfoPtr = openStream( mMetaData, stream );
		mColors = new int[ mMetaData[ 0 ] * mMetaData[ 1 ] ];
		checkError();
	}

	/**
	 * Creates drawable from AssetFileDescriptor.
	 * Convenience wrapper for {@link GifDrawable#GifDrawable(FileDescriptor)}
	 * @param afd source
	 * @throws IOException when opening failed
	 */
	public GifDrawable ( AssetFileDescriptor afd ) throws IOException
	{
		FileDescriptor fd = afd.getFileDescriptor();
		mGifInfoPtr = openFd( mMetaData, fd, afd.getStartOffset() );
		mColors = new int[ mMetaData[ 0 ] * mMetaData[ 1 ] ];
		checkError();
	}

	/**
	 * Creates drawable from FileDescriptor
	 * @param fd source
	 * @throws IOException when opening failed
	 */
	public GifDrawable ( FileDescriptor fd ) throws IOException
	{
		mGifInfoPtr = openFd( mMetaData, fd, 0 );
		mColors = new int[ mMetaData[ 0 ] * mMetaData[ 1 ] ];
		checkError();
	}

	/**
	 * Reads and renders new frame if needed then draws last rendered frame.
	 * @param canvas canvas to draw into
	 */
	@Override
	public void draw ( Canvas canvas )
	{
		if ( mIsRunning )
		{
			mMetaData[ 3 ] = renderFrame( mColors, mGifInfoPtr );
			if ( mMetaData[ 2 ] > 1 )
				invalidateSelf();
		}
		canvas.drawBitmap( mColors, 0, mMetaData[ 0 ], 0f, 0f, mMetaData[ 0 ], mMetaData[ 1 ], true, mPaint );
	}

	/**
	 * Frees any mamory allocated native way.
	 * Operation is irreversible. After this call, nothing will be drawn.
	 * This method is idempotent, subsequent calls have no effect.
	 * Like {@link android.graphics.Bitmap#recycle()} this is an advanced call and 
	 * is invoked implicitly by finalizer.
	 */
	public void recycle ()
	{
		mIsRunning = false;
		int tmpPtr = mGifInfoPtr;
		mGifInfoPtr = 0;
		free( tmpPtr );
		if ( mStream != null )
			try
			{
				mStream.close();
			}
			catch ( IOException ex )
			{
				//ignored
			}
	}

	@Override
	protected void finalize () throws Throwable
	{
		try
		{
			recycle();
		}
		finally
		{
			super.finalize();
		}
	}

	@Override
	public int getIntrinsicHeight ()
	{
		return mMetaData[ 1 ];
	}

	@Override
	public int getIntrinsicWidth ()
	{
		return mMetaData[ 0 ];
	}

	@Override
	public void setAlpha ( int alpha )
	{
		mPaint.setAlpha( alpha );
	}

	@Override
	public void setColorFilter ( ColorFilter cf )
	{
		mPaint.setColorFilter( cf );
	}

	/**
	 * See {@link Drawable#getOpacity()}
	 * @return always {@link PixelFormat#TRANSPARENT}
	 */
	@Override
	public int getOpacity ()
	{
		return PixelFormat.TRANSPARENT;
	}

	/**
	 * Starts the animation. Does nothing if GIF is not animated.
	 * Can be called from background thread.
	 */
	@Override
	public void start ()
	{
		mIsRunning = true;
	}

	/**
	 * Stops the animation. Does nothing if GIF is not animated.
	 * Can be called from background thread.
	 */
	@Override
	public void stop ()
	{
		mIsRunning = false;
	}

	@Override
	public boolean isRunning ()
	{
		return mIsRunning;
	}

	/**
	 * Returns GIF comment
	 * @return comment or null if there is no one defined in file
	 */
	public String getComment ()
	{
		return getComment( mGifInfoPtr );
	}

	/**
	 * Returns loop count previously read from GIF's application extension block.
	 * Defaults to 0 (infinite loop) if there is no such extension.
	 * @return loop count, 0 means infinte loop, 1 means one repetition (animation is played twice) etc.
	 */
	public int getLoopCount ()
	{
		return getLoopCount( mGifInfoPtr );
	}

	/**
	 * @return basic description of the GIF including size and number of frames
	 */
	@Override
	public String toString ()
	{
		return String.format( Locale.US, "Size: %dx%d, %d frames", mMetaData[ 1 ], mMetaData[ 2 ], mMetaData[ 0 ] );
	}

	/**
	 * @return number of frames in GIF, 0 if loading failed.
	 */
	public int getNumberOfFrames ()
	{
		return mMetaData[ 2 ];
	}

	/**
	 * Retrieves last error which is also the indicator of current GIF status.
	 *  
	 * @return current error or {@link GifError#NO_ERROR} if there was no error 
	 */
	public GifError getError ()
	{
		return GifError.fromCode( mMetaData[ 3 ] );
	}

	private void checkError () throws IOException
	{
		if ( mGifInfoPtr == 0 )
			throw new IOException( GifError.fromCode( mMetaData[ 3 ] ).getFormattedDescription() );
	}

	/**
	 * An {@link GifDrawable#GifDrawable(Resources, int)} wrapper but returns null 
	 * instead of throwing exception if creation fails. 
	 * @param res resources to read from
	 * @param resourceId resource id
	 * @return correct drawble or null if creation failed
	 */
	public static GifDrawable createFromResource ( Resources res, int resourceId )
	{
		try
		{
			return new GifDrawable( res, resourceId );
		}
		catch ( IOException e )
		{
			//ignored
		}
		return null;
	}
}