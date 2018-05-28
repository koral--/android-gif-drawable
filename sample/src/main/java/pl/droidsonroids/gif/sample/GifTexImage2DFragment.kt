package pl.droidsonroids.gif.sample

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import pl.droidsonroids.gif.GifOptions
import pl.droidsonroids.gif.GifTexImage2D
import pl.droidsonroids.gif.InputSource
import pl.droidsonroids.gif.sample.opengl.GifTexImage2DDrawer
import pl.droidsonroids.gif.sample.opengl.Renderer
import pl.droidsonroids.gif.sample.opengl.isOpenGLES2Supported

class GifTexImage2DFragment : BaseFragment() {

    lateinit var gifTexImage2DDrawer: GifTexImage2DDrawer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = GifOptions()
        options.setInIsOpaque(true)
        val gifTexImage2D = GifTexImage2D(InputSource.ResourcesSource(resources, R.drawable.anim_flag_chile), options)
        gifTexImage2D.startDecoderThread()
        gifTexImage2DDrawer = GifTexImage2DDrawer(gifTexImage2D)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (!context.isOpenGLES2Supported) {
            Snackbar.make(container!!, R.string.gles2_not_supported, Snackbar.LENGTH_LONG).show()
            return null
        }

        val view = inflater.inflate(R.layout.opengl, container, false) as GLSurfaceView
        view.setEGLContextClientVersion(2)
        view.setRenderer(Renderer(gifTexImage2DDrawer))
        view.holder.setFixedSize(gifTexImage2DDrawer.width, gifTexImage2DDrawer.height)
        return view
    }

    override fun onDetach() {
        super.onDetach()
        gifTexImage2DDrawer.destroy()
    }
}