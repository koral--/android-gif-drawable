package pl.droidsonroids.gif;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class Test extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        ImageView v = new ImageView(this);
        v.setImageDrawable(new GifDrawable());
        v.setScaleType(ScaleType.CENTER_INSIDE);
        setContentView(v);
        Log.e("a", ""+getResources().openRawResource(R.raw.s).markSupported());
    }
}

class GifDrawable extends Drawable 
{
    static {
        System.loadLibrary("gif");
        init();
    }	
    private Bitmap mBitmap;
    private static native boolean renderFrame(Bitmap  bitmap, int gifFileInPtr);
    private static native int openFile(String fname, int[] dims);
    private static native void free(int gifFileInPtr);
    private static native void init();    
    private int gifInfoPtr=0;

    public GifDrawable() {
        String fname="test.gif";
        fname="/sdcard/gifs/"+fname;
        int[] dims=new int[2];
        long st=SystemClock.elapsedRealtime();
        gifInfoPtr=openFile(fname, dims);
        if (gifInfoPtr>0)
        	mBitmap = Bitmap.createBitmap(dims[0], dims[1], Bitmap.Config.ARGB_8888);
        Log.w("time", "open "+(SystemClock.elapsedRealtime()-st));  
    }

    private void freeNative()
    {
   		free(gifInfoPtr);
   		gifInfoPtr=0;
    }
    @Override
	public void draw(Canvas canvas) {
    	//canvas.drawARGB(0xFF, 0xFF, 0, 0);
        if (gifInfoPtr>0)
        {
        	long st=SystemClock.elapsedRealtime();
        	if (renderFrame(mBitmap, gifInfoPtr))
        		canvas.drawBitmap(mBitmap, 0, 0, null);
    		invalidateSelf();
        	Log.d("time", "render "+(SystemClock.elapsedRealtime()-st));  
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
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setColorFilter(ColorFilter cf) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public int getOpacity() {
		// TODO Auto-generated method stub
		return 0;
	}
}
