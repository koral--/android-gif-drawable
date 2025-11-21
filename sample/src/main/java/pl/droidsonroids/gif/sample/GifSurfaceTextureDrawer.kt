package pl.droidsonroids.gif.sample

import android.graphics.SurfaceTexture
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifTexImage2D
import pl.droidsonroids.gif.InputSource
import pl.droidsonroids.gif.sample.opengl.GifTexImage2DProgram
import pl.droidsonroids.gif.sample.wallpaper.OffscreenEGLConnection
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class GifSurfaceTextureDrawer(private val source: InputSource) : CoroutineScope {

    private val renderContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainJob = Job()
    override val coroutineContext: CoroutineContext
        get() = renderContext + mainJob

    fun onSurfaceTextureDestroyed(surface: SurfaceTexture) {
        launch {
            mainJob.cancelAndJoin()
            surface.release()
            renderContext.close()
        }
    }

    fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        launch(Dispatchers.IO) {
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
                delay(gifTexImage2DProgram.currentFrameDuration.toLong())
            }
            gifTexImage2DProgram.destroy()
            eglConnection.destroy()
        }
    }
}