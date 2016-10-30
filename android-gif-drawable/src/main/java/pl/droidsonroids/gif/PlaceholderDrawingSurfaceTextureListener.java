package pl.droidsonroids.gif;

import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import android.view.TextureView;

@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class PlaceholderDrawingSurfaceTextureListener implements TextureView.SurfaceTextureListener {
	private final GifTextureView.PlaceholderDrawListener mDrawer;

	PlaceholderDrawingSurfaceTextureListener(GifTextureView.PlaceholderDrawListener drawer) {
		mDrawer = drawer;
	}

	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
		final Surface surface = new Surface(surfaceTexture);
		final Canvas canvas = surface.lockCanvas(null);
		mDrawer.onDrawPlaceholder(canvas);
		surface.unlockCanvasAndPost(canvas);
		surface.release();
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
		//no-op
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
		return false;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
		//no-op
	}
}
