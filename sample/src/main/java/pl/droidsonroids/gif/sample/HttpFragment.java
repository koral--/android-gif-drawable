package pl.droidsonroids.gif.sample;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pl.droidsonroids.gif.GifTextureView;
import pl.droidsonroids.gif.InputSource;

public class HttpFragment extends BaseFragment implements View.OnClickListener {

	private GifTextureView mGifTextureView;
	private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			if (container != null) {
				Snackbar.make(container, R.string.gif_texture_view_stub_api_level, Snackbar.LENGTH_LONG).show();
			}
			return null;
		} else {
			mGifTextureView = (GifTextureView) inflater.inflate(R.layout.http, container, false);
			downloadGif();
			return mGifTextureView;
		}
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !mGifTextureView.isHardwareAccelerated()) {
			Snackbar.make(mGifTextureView, R.string.gif_texture_view_stub_acceleration, Snackbar.LENGTH_LONG).show();
		}
	}

	@Override
	public void onDestroy() {
		mExecutorService.shutdownNow();
		super.onDestroy();
	}

	@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	void onGifDownloaded(ByteBuffer buffer) {
		mGifTextureView.setInputSource(new InputSource.DirectByteBufferSource(buffer));
	}

	@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	void onDownloadFailed(Exception e) {
		mGifTextureView.setOnClickListener(HttpFragment.this);
		if (isDetached()) {
			return;
		}
		final String message = getString(R.string.gif_texture_view_loading_failed, e.getMessage());
		Snackbar.make(mGifTextureView, message, Snackbar.LENGTH_LONG).show();

	}

	@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void downloadGif() {
		mExecutorService.submit(new GifLoadTask(this));
	}

	@Override
	@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	public void onClick(View v) {
		downloadGif();
	}
}
