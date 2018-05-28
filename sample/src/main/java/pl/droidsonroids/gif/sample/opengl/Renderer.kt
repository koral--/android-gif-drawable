package pl.droidsonroids.gif.sample.opengl

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Renderer(private val gifTexImage2DDrawer: GifTexImage2DDrawer) : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        gifTexImage2DDrawer.initialize()
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        gifTexImage2DDrawer.setDimensions(width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        gifTexImage2DDrawer.draw()
    }
}