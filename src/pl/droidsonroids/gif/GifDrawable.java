package pl.droidsonroids.gif;

import java.util.Locale;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 *  
 * @author koral--
 */
class GifDrawable extends Drawable implements Animatable
{
	static 
    {		
		System.loadLibrary("gif");
    }	
		
    private native synchronized int renderFrame(int[] pixels, int gifFileInPtr);
	private native int openFile(String filePAth, int[] metaData);
    private native void free(int gifFileInPtr);
    private native synchronized String getComment(int gifFileInPtr);
    private native synchronized int getLoopCount(int gifFileInPtr);
    
    private volatile int mGifInfoPtr;
    private final Paint mPaint=new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private volatile boolean mIsRunning=true;

    private final int[] mColors;
    private final int[] mMetaData;//[w,h,imageCount,errorCode]

    
    /**
     * Constructs drawable from given file path.<br>
     * Only metadata is read, no graphic data is decoded here.
     * In practice can be called from main thread. However it will violate
     * {@link StrictMode} policy if disk reads detection is enabled.<br>
     * No exception is thrown if file cannot be read (or is not a valid GIF).
     * Drawable will be just empty.<br>
     * Result of the operation can be checked by {@link #getErrorCode()}.
     * @param filePath path to the GIF file
     */
    public GifDrawable (String filePath) 
    {
        mMetaData=new int[4];
        mGifInfoPtr=openFile(filePath, mMetaData);
        mColors=new int[mMetaData[0]*mMetaData[1]];
        if (BuildConfig.DEBUG&&mGifInfoPtr==0)
        	Log.d("GifDrawable", String.format(Locale.US, "Error %d while reading %s", mMetaData[3],filePath));
    }
    @Override
	public void draw(final Canvas canvas) 
    {
		if (mIsRunning) 
		{
			mMetaData[3]=renderFrame(mColors, mGifInfoPtr);
			if (mMetaData[2]>1)
				invalidateSelf();
		}
		canvas.drawBitmap(mColors, 0, mMetaData[0], 0f, 0f, mMetaData[0],	mMetaData[1], true, mPaint);		
	}
	
    /**
     * Frees any mamory allocated native way.
     * Operation is irreversible. After this call, nothing will be drawn.
     * This method is idempotent, subsequent calls have no effect.
     * Like {@link android.graphics.Bitmap#recycle()} this is an advanced call and 
     * is invoked implicitly by finalizer.
     */
    public void recycle()
    {
    	mIsRunning=false;
    	synchronized (this)
    	{
    		free(mGifInfoPtr);
    		mGifInfoPtr=0;
    	}
    }    
    @Override
    protected void finalize() throws Throwable 
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
	public int getIntrinsicHeight() {
		return mMetaData[1];
	}
	@Override
	public int getIntrinsicWidth() {
		return mMetaData[0];
	}
	@Override
	public void setAlpha(int alpha) {
		mPaint.setAlpha(alpha);
	}
	@Override
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
	}
	/**
	 * See {@link Drawable#getOpacity()}
	 * @return always {@link PixelFormat#TRANSPARENT}
	 */
	@Override
	public int getOpacity() {
		return PixelFormat.TRANSPARENT;
	}
	/**
	 * Starts the animation. Does nothing if GIF is not animated.
	 * Can be called from background thread.
	 */
	public void start() {
		mIsRunning=true;		
	}
	/**
	 * Stops the animation. Does nothing if GIF is not animated.
	 * Can be called from background thread.
	 */
	public void stop() {
		mIsRunning=false;
	}
	/**
	 * @return true if animation is running
	 */
	public boolean isRunning() {
		return mIsRunning;
	}
	/**
	 * Returns GIF comment
	 * @return comment or null if there is no one defined in file
	 */
    public String getComment() 
    {
		return getComment(mGifInfoPtr);
	}
    /**
     * Returns loop count previously read from GIF's application extension block.
     * Defaults to 0 (infinite loop) if there is no such extension.
     * @return loop count, 0 means infinte loop, 1 means one repetition (animation is played twice) etc.
     */
    public int getLoopCount()
    {
    	return getLoopCount(mGifInfoPtr);
    }

    /**
     * @return basic description of the GIF
     */
    @Override
    public String toString() {
    	// TODO add error information
    	return String.format(Locale.US, "Size: %dx%d, %d frames", 
    			mMetaData[1],mMetaData[2],mMetaData[0]);
    }

    /**
     * @return number of frames in GIF, 0 if loading failed.
     */
	public int getNumberOfFrames() {
		return mMetaData[2];
	}
	
	/**
	 * Retrieves error code which is also the indicator of current GIF status. 
	 * Error codes are consistent with those defined in GIFLib.<br>
	 * Additional codes: 1000 - no frames, 1001 - invalid screen dimensions
	 * TODO port gif_err.c to java
	 * @return current error code or 0 if there was no error 
	 */
	public int getErrorCode()
	{
		return mMetaData[3];
	}
}