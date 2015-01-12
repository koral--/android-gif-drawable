package pl.droidsonroids.gif;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Default executor for rendering tasks - {@link java.util.concurrent.ScheduledThreadPoolExecutor}
 * with 1 worker thread and {@link java.util.concurrent.ThreadPoolExecutor.DiscardPolicy}.
 */
final class GifRenderingExecutor extends ScheduledThreadPoolExecutor {
    private static final ThreadPoolExecutor.DiscardPolicy DISCARD_POLICY = new ThreadPoolExecutor.DiscardPolicy();

    GifRenderingExecutor() {
        super(1, DISCARD_POLICY);
    }
}
