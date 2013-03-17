package pl.droidsonroids.gif;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class Test extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
        .detectAll()
        .penaltyLog()
        .penaltyDeath()
        .build());
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
        .detectAll()   // or .detectAll() for all detectable problems
        .penaltyLog()
        .build());    
        super.onCreate(savedInstanceState);
        ImageButton v = new ImageButton(this);
        v.setScaleType(ScaleType.FIT_XY);
		GifDrawable drw=new GifDrawable("/sdcard/gifs/large.gif");
       v.setImageDrawable(drw);
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
      //	  i	.setImageDrawable(new GifDrawable(getItem(position).getAbsolutePath()));
            return i;
        }


    }    
}

class GifDrawable extends Drawable 

{
	static 
    {
		System.loadLibrary("gif");
    }	
		
    private native boolean renderFrame(int[] pixels, int gifFileInPtr);
	private native int openFile(String fname, int[] dims);
    private native void free(int gifFileInPtr);
    private native String getComment(int gifFileInPtr);
    private native int getLoopCount(int gifFileInPtr);
    
    private volatile int gifInfoPtr;
    private final Paint mPaint=new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private boolean mIsRunning;
    private int frameCount;

    private final int[] colors, metaData;
    public GifDrawable (String fname) 
    {
        metaData=new int[4];
        gifInfoPtr=openFile(fname, metaData);
        if (gifInfoPtr>0)
        {	
        	frameCount=metaData[3];   
        	mIsRunning=true;
        	colors=new int[metaData[0]*metaData[1]];
        }
        else
        	colors=null;
    }

    @Override
	public void draw(final Canvas canvas) 
    {
        if (gifInfoPtr>0)
        {
      		canvas.drawBitmap(colors, 0,metaData[0], 0f, 0f, metaData[0], metaData[1], true, mPaint);
      		renderFrame(colors, gifInfoPtr);
      		invalidateSelf();
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
		return metaData[1];
	}
	@Override
	public int getIntrinsicWidth() {
		return metaData[0];
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
		return PixelFormat.TRANSPARENT;
	}
//	@Override
//	public void start() {
//		mIsRunning=true;		
//	}
//	@Override
//	public void stop() {
//		mIsRunning=false;
//	}
//	@Override
//	public boolean isRunning() {
//		return mIsRunning;
//	}
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
    	return null;
    }
    @Override
    public String toString() {
    	// TODO think some friendly text
    	return super.toString();
    }
/*	@Override
	public int getDuration(int i) 
	{
		// TODO Auto-generated method stub
		return super.getDuration(i);
	}
	@Override
	public int getNumberOfFrames() {
		return frameCount;
	}*/
}
