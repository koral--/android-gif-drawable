package pl.droidsonroids.gif

import android.annotation.SuppressLint
import android.content.Context
import com.getkeepsafe.relinker.ReLinker

/**
 * Helper used to work around native libraries loading on some systems.
 * See [ReLinker](https://medium.com/keepsafe-engineering/the-perils-of-loading-native-libraries-on-android-befa49dce2db) for more details.
 */
object LibraryLoader {
    private const val BASE_LIBRARY_NAME = "pl_droidsonroids_gif"

    @SuppressLint("StaticFieldLeak") //workaround for Android bug

    private var sAppContext: Context? = null

    /**
     * Initializes loader with given `Context`. Subsequent calls should have no effect since application Context is retrieved.
     * Libraries will not be loaded immediately but only when needed.
     *
     * @param context any Context except null
     */
    fun initialize(context: Context) {
        sAppContext = context.applicationContext
    }

    private val context: Context?
        get() {
            if (sAppContext == null) {
                try {
                    @SuppressLint("PrivateApi") val activityThread =
                        Class.forName("android.app.ActivityThread")
                    @SuppressLint("DiscouragedPrivateApi") val currentApplicationMethod =
                        activityThread.getDeclaredMethod("currentApplication")
                    sAppContext = currentApplicationMethod.invoke(null) as Context
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "LibraryLoader not initialized. Call LibraryLoader.initialize() before using library classes.",
                        e
                    )
                }
            }
            return sAppContext
        }

    @JvmStatic
    fun loadLibrary() {
        try {
            System.loadLibrary(BASE_LIBRARY_NAME)
        } catch (e: UnsatisfiedLinkError) {
            ReLinker.loadLibrary(context, BASE_LIBRARY_NAME)
        }
    }
}