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
import pl.droidsonroids.gif.sample.opengl.GifTexImage2DProgram
import pl.droidsonroids.gif.sample.opengl.Renderer
import pl.droidsonroids.gif.sample.opengl.isOpenGLES2Supported

class GifTexImage2DFragment : BaseFragment() {

    lateinit var gifTexImage2DProgram: GifTexImage2DProgram

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = GifOptions()
        options.setInIsOpaque(true)
        val gifTexImage2D = GifTexImage2D(InputSource.ResourcesSource(resources, R.drawable.anim_flag_chile), options)
        gifTexImage2D.startDecoderThread()
        gifTexImage2DProgram = GifTexImage2DProgram(gifTexImage2D)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (!context.isOpenGLES2Supported) {
            Snackbar.make(container!!, R.string.gles2_not_supported, Snackbar.LENGTH_LONG).show()
            return null
        }

        val view = inflater.inflate(R.layout.opengl, container, false) as GLSurfaceView
        view.setEGLContextClientVersion(2)
        view.setRenderer(Renderer(gifTexImage2DProgram))
        view.holder.setFixedSize(gifTexImage2DProgram.width, gifTexImage2DProgram.height)
        return view
    }

    override fun onDetach() {
        super.onDetach()
        gifTexImage2DProgram.destroy()
    }
}