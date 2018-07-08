package pl.droidsonroids.gif.sample.opengl

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GifTexImage2DRenderer(private val gifTexImage2DProgram: GifTexImage2DProgram) : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        gifTexImage2DProgram.initialize()
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        gifTexImage2DProgram.setDimensions(width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        gifTexImage2DProgram.draw()
    }
}