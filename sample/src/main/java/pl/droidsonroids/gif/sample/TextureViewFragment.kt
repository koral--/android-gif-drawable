package pl.droidsonroids.gif.sample

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.BounceInterpolator
import android.view.animation.ScaleAnimation
import androidx.fragment.app.Fragment
import pl.droidsonroids.gif.InputSource

class TextureViewFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val textureView = TextureView(inflater.context)
        textureView.isOpaque = false
        val gifSurfaceTextureDrawer = GifSurfaceTextureDrawer(InputSource.ResourcesSource(resources, R.drawable.anim_flag_chile))
        textureView.surfaceTextureListener = GifSurfaceTextureListener(gifSurfaceTextureDrawer)

        val animation = ScaleAnimation(1f, 0.5f, 1f, 0.5f)
        animation.duration = 1000
        animation.repeatMode = Animation.REVERSE
        animation.repeatCount = Animation.INFINITE
        animation.interpolator = BounceInterpolator()

        textureView.startAnimation(animation)
        return textureView
    }
}

class GifSurfaceTextureListener(private val gifSurfaceTextureDrawer: GifSurfaceTextureDrawer) : TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        gifSurfaceTextureDrawer.onSurfaceTextureAvailable(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        gifSurfaceTextureDrawer.onSurfaceTextureDestroyed(surface)
        return false
    }
}