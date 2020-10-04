package pl.droidsonroids.gif.sample.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifOptions
import pl.droidsonroids.gif.GifTexImage2D
import pl.droidsonroids.gif.InputSource
import pl.droidsonroids.gif.sample.R
import pl.droidsonroids.gif.sample.opengl.GifTexImage2DProgram
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class GifWallpaperService : WallpaperService() {
    override fun onCreateEngine(): GifWallpaperEngine {
        val options = GifOptions()
        options.setInIsOpaque(true)
        val gifTexImage2D = GifTexImage2D(InputSource.ResourcesSource(resources, R.drawable.led7), options)
        return GifWallpaperEngine(gifTexImage2D)
    }

    @Suppress("RedundantInnerClassModifier")
    inner class GifWallpaperEngine(private val gifTexImage2D: GifTexImage2D) : Engine(),
        CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = renderContext + mainJob

        private val mainJob = Job()
        private val renderContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        private val eglConnection = OffscreenEGLConnection()
        private val gifTexImage2DDrawer = GifTexImage2DProgram(gifTexImage2D)
        private var renderJob: Job = Job()
        private var frameIndex = 0

        override fun getDesiredMinimumWidth() = gifTexImage2DDrawer.width

        override fun getDesiredMinimumHeight() = gifTexImage2DDrawer.height

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            launch {
                mainJob.cancelAndJoin()
                gifTexImage2DDrawer.destroy()
                eglConnection.destroy()
                renderContext.close()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) = Unit

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                renderJob = launch {
                    while (isActive) {
                        gifTexImage2D.seekToFrame(frameIndex)
                        gifTexImage2DDrawer.draw()
                        eglConnection.draw()
                        delay(gifTexImage2D.getFrameDuration(frameIndex).toLong())
                        frameIndex++
                        frameIndex %= gifTexImage2D.numberOfFrames
                    }
                }
            } else {
                renderJob.cancel()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            launch {
                eglConnection.destroy()
                eglConnection.initialize(holder)
                gifTexImage2DDrawer.initialize()
                gifTexImage2DDrawer.setDimensions(width, height)
            }
        }
    }
}

