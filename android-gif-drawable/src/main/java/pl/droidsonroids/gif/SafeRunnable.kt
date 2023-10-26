package pl.droidsonroids.gif

/**
 * Runnable for [java.util.concurrent.Executor] which propagates exceptions to default uncaught
 * exception handler.
 */
internal abstract class SafeRunnable(@JvmField val mGifDrawable: GifDrawable) : Runnable {
    override fun run() {
        try {
            if (!mGifDrawable.isRecycled) {
                doWork()
            }
        } catch (throwable: Throwable) {
            val uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), throwable)
            throw throwable
        }
    }

    abstract fun doWork()
}