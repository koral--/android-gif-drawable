package pl.droidsonroids.gif;

import java.io.File;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

public class Test extends Activity
{
	static ActivityManager activityManager; 
	static 
	{
		System.loadLibrary("gif");
	}
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        ImageView v = new ImageView(this);
		activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        
       v.setImageDrawable(new GifDrawable("/sdcard/gifs/canvas_bgnd.gif"));
       // Log.e("ggg", ""+new GifDrawable("/sdcard/gifs/m.gif").getLoopCount());
//        Gallery v=new Gallery(this);
//        v.setAdapter(new ImageAdapter(new File("/sdcard/gifs").listFiles()));
        setContentView(v);
    }
    public class ImageAdapter extends BaseAdapter {
    	final File[] items;
    	
        public ImageAdapter(File[] items) {
			this.items = items;
		}

		public int getCount() {
            return items.length;
        }

        public File getItem(int position) {
            return items[position];
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(Test.this);
      	  i	.setImageDrawable(new GifDrawable(getItem(position).getAbsolutePath()));
            return i;
        }


    }    
}

class GifDrawable extends Drawable implements Animatable 
{
    static 
    {
        init();
    }	
    private native boolean renderFrame(Bitmap  bitmap, int gifFileInPtr);
	private native int openFile(String fname, int[] dims);
    private native void free(int gifFileInPtr);
    private native String getComment(int gifFileInPtr);
    private native int getLoopCount(int gifFileInPtr);
    private static native void init();
    
    private Bitmap mBitmap;
    private int gifInfoPtr;
    private final Paint mPaint=new Paint();
    private boolean mIsRunning;
    private boolean mHasAlpha;
    private int frameCount;
    String fname;//temporary
    
    public GifDrawable(String fname) {
    	this.fname=fname;
 //   	SystemClock.sleep(5000);
//        String fname="test.gif";
//        fname="/sdcard/gifs/"+fname;
        int[] metaData=new int[4];
        long st=SystemClock.elapsedRealtime();
        gifInfoPtr=openFile(fname, metaData);
        if (gifInfoPtr>0)
        {	
        	mBitmap = Bitmap.createBitmap(metaData[0], metaData[1], Bitmap.Config.ARGB_8888);
        	mHasAlpha=metaData[2]==1;
        	mBitmap.setHasAlpha(mHasAlpha);
        	frameCount=metaData[3];        	
        }

        Log.w("time", "open "+(SystemClock.elapsedRealtime()-st));  
    }

    @Override
	public void draw(Canvas canvas) {
        if (gifInfoPtr>0)
        {
        	long st=SystemClock.elapsedRealtime();
        	//Log.w("fname", "rendering "+fname);
        	if (renderFrame(mBitmap, gifInfoPtr)) //TODO pass mIsRunning
        	{	
        		canvas.drawBitmap(mBitmap, 0, 0, mPaint);
        		//Log.d("time", "render "+(SystemClock.elapsedRealtime()-st));  
        	}
    		invalidateSelf();
        	//Log.d("time", "render "+(SystemClock.elapsedRealtime()-st));  
        }	    
    }
    /**
     * Frees the native object associated with this drawable and bitmap holding frames.
     * Operation is irreversible. After call drawable will draw nothing, 
     * getters will return default values but no exceptions will be thrown after invoking other methods.
     * This method is idempotent, subsequent calls have no effect.
     * If GIF was not decoded successfuly then this call also does nothing.
     * Like {@link android.graphics.Bitmap#recycle()} this is an advanced call and 
     * is invoked implicitly by garbage collector.
     */
    public void recycle()
    {
    	Log.d("fname", "freeing "+fname);
//    	if (mBitmap!=null)
//    	{	
//    		mBitmap.recycle();
//    		mBitmap=null;
//    	}
//    	mHasAlpha=false;
   		free(gifInfoPtr);
   		gifInfoPtr=0;
    }    
    @Override
    protected void finalize() throws Throwable 
    {
    	recycle();
    	super.finalize();
    }
	@Override
	public int getIntrinsicHeight() {
		return mBitmap!=null?mBitmap.getHeight():-1;
	}
	@Override
	public int getIntrinsicWidth() {
		return mBitmap!=null?mBitmap.getWidth():-1;
	}
	@Override
	public void setAlpha(int alpha) {
		mPaint.setAlpha(alpha);
	}
	@Override
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
	}
	@Override
	public int getOpacity() {
		return mHasAlpha?PixelFormat.TRANSPARENT:PixelFormat.OPAQUE;
	}
	@Override
	public void start() {
		mIsRunning=true;		
	}
	@Override
	public void stop() {
		mIsRunning=false;
	}
	@Override
	public boolean isRunning() {
		return mIsRunning;
	}
	/**
	 * Returns GIF comment
	 * @return comment or null if there is no one defined in file
	 */
    public String getComment() 
    {
		return getComment(gifInfoPtr);
	}
    /**
     * Returns loop count previously read from GIF's application extension block.
     * Defaults to 0 (infinite loop) if there is no such extension.
     * @return loop count, 0 means infinte loop, 1 means one repetition (animation is played twice)
     */
    public int getLoopCount()
    {
    	return getLoopCount(gifInfoPtr);
    }
    /**
     * Gets immutable bitmap representing current frame.
     * It may be null if no frame was decoded or after recycling
     * @return bitmap with current frame or null 
     */
    public Bitmap getCurrentFrame()
    {
    	if (mBitmap!=null)
    		return Bitmap.createBitmap(mBitmap);
    	return null;
    }
    /**
     * Returns number of frames or 0 when no frames were read successfully
     * @return number of frames (may be 0)
     */
    public int getFrameCount()
    {
    	return frameCount;
    }
    @Override
    public String toString() {
    	// TODO think some friendly text
    	return super.toString();
    }
}
