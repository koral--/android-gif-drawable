package pl.droidsonroids.gif.sample;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.BufferedInputStream;
import java.io.IOException;

import pl.droidsonroids.gif.GifTextureView;
import pl.droidsonroids.gif.InputSource;

public class TexturePlaceholderFragment extends BaseFragment implements GifTextureView.PlaceholderDrawListener {

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			if (container != null) {
				Snackbar.make(container, R.string.gif_texture_view_stub_api_level, Snackbar.LENGTH_LONG).show();
			}
			return null;
		} else {
			final GifTextureView view = new GifTextureView(getContext());
			try {
				final AssetFileDescriptor assetFileDescriptor = getContext().getAssets().openFd("Animated-Flag-Delaware.gif");
				view.setInputSource(new InputSource.InputStreamSource(new SlowLoadingInputStream(assetFileDescriptor)), this);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return view;
		}
	}

	@Override
	public void onDrawPlaceholder(Canvas canvas) {
		final Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
		canvas.drawBitmap(bitmap, 0, 0, null);
		bitmap.recycle();
	}

	private static class SlowLoadingInputStream extends BufferedInputStream {

		private final AssetFileDescriptor mAssetFileDescriptor;
		private int mSleepTimeMillis = 5;

		SlowLoadingInputStream(AssetFileDescriptor assetFileDescriptor) throws IOException {
			super(assetFileDescriptor.createInputStream(), (int) assetFileDescriptor.getLength());
			mAssetFileDescriptor = assetFileDescriptor;
		}

		@Override
		public int read(@NonNull byte[] buffer) throws IOException {
			SystemClock.sleep(mSleepTimeMillis);
			return super.read(buffer);
		}

		@Override
		public int read(@NonNull byte[] buffer, int off, int len) throws IOException {
			SystemClock.sleep(mSleepTimeMillis);
			return super.read(buffer, off, len);
		}

		@Override
		public int read() throws IOException {
			SystemClock.sleep(mSleepTimeMillis);
			return super.read();
		}

		@Override
		public synchronized void reset() throws IOException {
			super.reset();
			mSleepTimeMillis = 0;
		}

		@Override
		public void close() throws IOException {
			super.close();
			mAssetFileDescriptor.close();
		}
	}
}
