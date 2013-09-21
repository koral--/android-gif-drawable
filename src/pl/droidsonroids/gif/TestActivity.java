package pl.droidsonroids.gif;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

/**
 * Activity for testing purposes.
 * 
 * @author koral--
 * 
 */
public class TestActivity extends Activity implements OnClickListener
{
	ImageButton v;
	GifDrawable drw;

	/** Called when the activity is first created. */
	@SuppressLint ( "NewApi" )
	@Override
	public void onCreate ( Bundle savedInstanceState )
	{
//		if ( BuildConfig.DEBUG && Build.VERSION.SDK_INT > 8 )
//		{
//			StrictMode.setThreadPolicy( new ThreadPolicy.Builder().detectAll().build() );
//			StrictMode.setVmPolicy( new VmPolicy.Builder().detectAll().build() );
//		}
		super.onCreate( savedInstanceState );
		v = new ImageButton( this );
		v.setOnClickListener( this );
		v.setScaleType( ScaleType.FIT_XY );
		try
		{
			String pth="/sdcard/gifs/piggy.gif";
			//drw = new GifDrawable(pth);
			final InputStream stream =
					new BufferedInputStream( new FileInputStream(pth),16356041) ;
				//	getResources().openRawResource( R.drawable.piggy );
			drw = new GifDrawable(pth);
			//drw.recycle();
		}
		catch ( Exception e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		v.setImageDrawable( drw );

		// Gallery v=new Gallery(this);
		// v.setAdapter(new ImageAdapter(new File("/sdcard/gifs").listFiles()));

		setContentView( v );
	}

	public void onClick ( View v2 )
	{
		if ( drw != null )
			drw.recycle();
		drw = new GifDrawable( "/sdcard/gifs/t100x02.gif" );
		v.setImageDrawable( drw );
	}

	public class ImageAdapter extends BaseAdapter
	{
		final File[] items;

		public ImageAdapter ( File[] items )
		{
			this.items = items;
		}

		public int getCount ()
		{
			return items.length;
		}

		public File getItem ( int position )
		{
			return items[ position ];
		}

		public long getItemId ( int position )
		{
			return position;
		}

		public View getView ( int position, View convertView, ViewGroup parent )
		{
			ImageView i = new ImageView( TestActivity.this );
			// i .setImageDrawable(new
			// GifDrawable(getItem(position).getAbsolutePath()));
			return i;
		}

	}
}
