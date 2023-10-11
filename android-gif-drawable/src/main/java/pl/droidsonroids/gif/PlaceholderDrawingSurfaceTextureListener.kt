package pl.droidsonroids.gif

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener

internal class PlaceholderDrawingSurfaceTextureListener(private val mDrawer: GifTextureView.PlaceholderDrawListener) :
    SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        val surface = Surface(surfaceTexture)
        val canvas = surface.lockCanvas(null)
        mDrawer.onDrawPlaceholder(canvas)
        surface.unlockCanvasAndPost(canvas)
        surface.release()
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        //no-op
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        return false
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        //no-op
    }
}