package pl.droidsonroids.gif;

import java.io.File;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class Test extends Activity implements OnClickListener
{
	ImageButton v;
	GifDrawable drw;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        v = new ImageButton(this);
        v.setOnClickListener(this);
        v.setScaleType(ScaleType.FIT_XY);
//        Gallery v=new Gallery(this);
//        v.setAdapter(new ImageAdapter(new File("/sdcard/gifs").listFiles()));

        setContentView(v);
    }
    public void onClick(View v2) {
    	if (drw!=null)
    		drw.recycle();
		drw=new GifDrawable("/sdcard/gifs/msp.gif");
	       v.setImageDrawable(drw);
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

class GifDrawable extends Drawable implements Animatable

{
	static 
    {		
		System.loadLibrary("gif");
    }	
		
    private native int renderFrame(int[] pixels, int gifFileInPtr);
	private native int openFile(String fname, int[] dims);
    private native void free(int gifFileInPtr);
    private native String getComment(int gifFileInPtr);
    private native int getLoopCount(int gifFileInPtr);
    
    private volatile int gifInfoPtr;
    private final Paint mPaint=new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private volatile boolean mIsRunning;

    private final int[] colors;
    private final int[] metaData;//[w,h,imageCount,errorCode]

    public GifDrawable (String fname) 
    {
        metaData=new int[4];
        gifInfoPtr=openFile(fname, metaData);
        if (gifInfoPtr>0)
        {	
        	mIsRunning=true;
        	colors=new int[metaData[0]*metaData[1]];
        }
        else
        	colors=null;
    }
    @Override
	public synchronized void draw(final Canvas canvas) 
    {
        if (gifInfoPtr>0)
        {
      		canvas.drawBitmap(colors, 0,metaData[0], 0f, 0f, metaData[0], metaData[1], true, mPaint);
      		if (mIsRunning)
      		{	
      			renderFrame(colors, gifInfoPtr);
      			invalidateSelf();
      		}
        }
    }
	
    /**
     * Frees the native object associated with this drawable and bitmap holding frames.
     * Operation is irreversible. After call drawable will draw nothing, 
     * getters will return default values but no exceptions will be thrown after invoking other methods.
     * This method is idempotent, subsequent calls have no effect.
     * If GIF was not decoded successfuly then this call does nothing.
     * Like {@link android.graphics.Bitmap#recycle()} this is an advanced call and 
     * is invoked implicitly by garbage collector.
     */
    public synchronized void recycle()
    {
    	mIsRunning=false;
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
	public void start() {
		mIsRunning=true;		
	}
	public void stop() {
		mIsRunning=false;
	}
	public boolean isRunning() {
		return mIsRunning;
	}
	/**
	 * TODO it is not working yet
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
     * @return loop count, 0 means infinte loop, 1 means one repetition (animation is played twice) etc.
     */
    public int getLoopCount()
    {//FIXME fix reading
    	return getLoopCount(gifInfoPtr);
    }

    @Override
    public String toString() {
    	// TODO think some friendly text
    	return super.toString();
    }

	public int getNumberOfFrames() {
		return metaData[2];
	}
	
}
