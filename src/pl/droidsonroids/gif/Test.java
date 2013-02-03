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
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

public class Test extends Activity
{
	static 
	{
		System.loadLibrary("gif");
	}
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
//        ImageView v = new ImageView(this);
//        v.setImageDrawable(new GifDrawable());
//        v.setScaleType(ScaleType.CENTER_INSIDE);
        Gallery v=new Gallery(this);
        v.setAdapter(new ImageAdapter(new File("/sdcard/gifs").listFiles()));
        setContentView(v);
    }
    public class ImageAdapter extends BaseAdapter {
    	final File[] items;
    	
        public ImageAdapter(File[] items) {
			super();
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
    static {
        init();
    }	
    private Bitmap mBitmap;
    private static native boolean renderFrame(Bitmap  bitmap, int gifFileInPtr);
    private static native int openFile(String fname, int[] dims);
    private static native void free(int gifFileInPtr);
    private static native void init();    
    private int gifInfoPtr=0;
    private Paint mPaint=new Paint();
    private boolean mIsRunning;
    String fname;
    public GifDrawable(String fname) {
    	this.fname=fname;
    	//SystemClock.sleep(5000);
//        String fname="test.gif";
//        fname="/sdcard/gifs/"+fname;
        int[] dims=new int[2];
        long st=SystemClock.elapsedRealtime();
        gifInfoPtr=openFile(fname, dims);
        if (gifInfoPtr>0)
        {	
        	mBitmap = Bitmap.createBitmap(dims[0], dims[1], Bitmap.Config.ARGB_8888);
        	//mBitmap.setHasAlpha(false);  TODO fetch from native
        }
        Log.w("time", "open "+(SystemClock.elapsedRealtime()-st));  
    }

    private void freeNative()
    {
    	Log.d("fname", "freeing "+fname);
   		free(gifInfoPtr);
   		gifInfoPtr=0;
    }
    @Override
	public void draw(Canvas canvas) {
        if (gifInfoPtr>0)
        {
        	long st=SystemClock.elapsedRealtime();
        	Log.w("fname", "rendering "+fname);
        	if (renderFrame(mBitmap, gifInfoPtr)) //TODO pass mIsRunning
        	{	
        		canvas.drawBitmap(mBitmap, 0, 0, mPaint);
        		//Log.d("time", "render "+(SystemClock.elapsedRealtime()-st));  
        	}
    		invalidateSelf();
        	//Log.d("time", "render "+(SystemClock.elapsedRealtime()-st));  
        }	    
    }
    @Override
    protected void finalize() throws Throwable 
    {
    	freeNative();
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
		// TODO Auto-generated method stub
		return PixelFormat.TRANSPARENT;
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
	//getNrOfFrames
	//getComment
}
