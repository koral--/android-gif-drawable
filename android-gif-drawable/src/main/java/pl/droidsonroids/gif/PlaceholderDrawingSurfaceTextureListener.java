package pl.droidsonroids.gif;

import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

class PlaceholderDrawingSurfaceTextureListener implements TextureView.SurfaceTextureListener {
	private final GifTextureView.PlaceholderDrawListener mDrawer;

	PlaceholderDrawingSurfaceTextureListener(GifTextureView.PlaceholderDrawListener drawer) {
		mDrawer = drawer;
	}

	@Override
	public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
		final Surface surface = new Surface(surfaceTexture);
		final Canvas canvas = surface.lockCanvas(null);
		mDrawer.onDrawPlaceholder(canvas);
		surface.unlockCanvasAndPost(canvas);
		surface.release();
	}

	@Override
	public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
		//no-op
	}

	@Override
	public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
		return false;
	}

	@Override
	public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
		//no-op
	}
}
