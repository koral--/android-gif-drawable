package pl.droidsonroids.gif;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
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
public class TestActivity extends Activity implements OnClickListener {
	public enum Error {
		OPEN_FAILED(101, "Failed to open given file"), READ_FAILED(102,
				"Failed to read from given file"), NOT_GIF_FILE(103,
				"Data is not in GIF format"), NO_SCRN_DSCR(104,
				"No screen descriptor detected"), NO_IMAG_DSCR(105,
				"No Image Descriptor detected"), NO_COLOR_MAP(106,
				"Neither global nor local color map"), WRONG_RECORD(107,
				"Wrong record type detected"), DATA_TOO_BIG(108,
				"Number of pixels bigger than width * height"), NOT_ENOUGH_MEM(
				109, "Failed to allocate required memory"), CLOSE_FAILED(110,
				"Failed to close given file"), NOT_READABLE(111,
				"Given file was not opened for read"), IMAGE_DEFECT(112,
				"Image is defective, decoding aborted"), EOF_TOO_SOON(113,
				"Image EOF detected before image complete"), NO_FRAMES(1000,
				"No frames found, at least one frame required"), INVALID_SCR_DIMS(
				1001, "Invalid screen size"), INVALID_IMG_DIMS(1002,
				"Invalid image size"), IMG_NOT_CONFINED(1003,
				"Image size exceeds screen size");
		private Error(int code, String description) {
			super.name();
		}

	}

	ImageButton v;
	GifDrawable drw;

	/** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (BuildConfig.DEBUG && Build.VERSION.SDK_INT > 8) {
			StrictMode.setThreadPolicy(new ThreadPolicy.Builder().detectAll()
					.build());
			StrictMode.setVmPolicy(new VmPolicy.Builder().detectAll().build());
		}
		super.onCreate(savedInstanceState);
		v = new ImageButton(this);
		v.setOnClickListener(this);
		v.setScaleType(ScaleType.FIT_XY);
		drw = new GifDrawable("/sdcard/gifs/msp.gif");
		v.setImageDrawable(drw);

		// Gallery v=new Gallery(this);
		// v.setAdapter(new ImageAdapter(new File("/sdcard/gifs").listFiles()));

		setContentView(v);
	}

	public void onClick(View v2) {
		if (drw != null)
			drw.recycle();
		drw = new GifDrawable("/sdcard/gifs/test.gif");
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
			ImageView i = new ImageView(TestActivity.this);
			// i .setImageDrawable(new
			// GifDrawable(getItem(position).getAbsolutePath()));
			return i;
		}

	}
}
