package pl.droidsonroids.gif;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Default executor for rendering tasks - {@link java.util.concurrent.ScheduledThreadPoolExecutor}
 * with 1 worker thread and {@link java.util.concurrent.ThreadPoolExecutor.DiscardPolicy}.
 */
final class GifRenderingExecutor extends ScheduledThreadPoolExecutor {

    private GifRenderingExecutor() {
        super(1, new DiscardPolicy());
    }

    @SuppressWarnings("StaticNonFinalField") //double-checked singleton initialization
    private static volatile GifRenderingExecutor instance = null;

    public static GifRenderingExecutor getInstance() {
        if (instance == null) {
            synchronized (GifRenderingExecutor.class) {
                if (instance == null) {
                    instance = new GifRenderingExecutor();
                }
            }
        }
        return instance;
    }
}
