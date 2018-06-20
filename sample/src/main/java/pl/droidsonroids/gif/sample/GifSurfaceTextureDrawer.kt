package pl.droidsonroids.gif.sample

import android.graphics.SurfaceTexture
import kotlinx.coroutines.experimental.*
import pl.droidsonroids.gif.GifTexImage2D
import pl.droidsonroids.gif.InputSource
import pl.droidsonroids.gif.sample.opengl.GifTexImage2DProgram
import pl.droidsonroids.gif.sample.wallpaper.OffscreenEGLConnection

class GifSurfaceTextureDrawer(private val source: InputSource) {

    private val renderContext = newSingleThreadContext("TextureViewFragmentGifRenderThread")
    private val mainJob = Job()

    fun onSurfaceTextureDestroyed(surface: SurfaceTexture) {
        launch {
            mainJob.cancelAndJoin()
            surface.release()
        }
    }

    fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        launch(context = renderContext, parent = mainJob) {
            val gifTexImage2D = GifTexImage2D(source, null)
            gifTexImage2D.startDecoderThread()

            val gifTexImage2DProgram = GifTexImage2DProgram(gifTexImage2D)
            val eglConnection = OffscreenEGLConnection()

            eglConnection.initialize(surface)
            gifTexImage2DProgram.initialize()
            gifTexImage2DProgram.setDimensions(width, height)

            while (isActive) {
                gifTexImage2DProgram.draw()
                eglConnection.draw()
                delay(gifTexImage2DProgram.currentFrameDuration)
            }
            gifTexImage2DProgram.destroy()
            eglConnection.destroy()
        }
    }
}