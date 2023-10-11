package pl.droidsonroids.gif

import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * Default executor for rendering tasks - [java.util.concurrent.ScheduledThreadPoolExecutor]
 * with 1 worker thread and [java.util.concurrent.ThreadPoolExecutor.DiscardPolicy].
 */
object GifRenderingExecutor : ScheduledThreadPoolExecutor(1, DiscardPolicy())