package pl.droidsonroids.gif;

/**
 * Runnable for {@link java.util.concurrent.Executor} which propagates exceptions to default uncaught
 * exception handler.
 */
abstract class SafeRunnable implements Runnable {
	final GifDrawable mGifDrawable;

	SafeRunnable(GifDrawable gifDrawable) {
		mGifDrawable = gifDrawable;
	}

	@Override
	public final void run() {
		try {
			if (!mGifDrawable.isRecycled()) {
				doWork();
			}
		} catch (Throwable throwable) {
			final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
			if (uncaughtExceptionHandler != null) {
				uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);
			}
			throw throwable;
		}
	}

	abstract void doWork();
}
