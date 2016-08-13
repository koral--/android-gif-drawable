package pl.droidsonroids.gif;

import android.content.Context;
import android.support.annotation.NonNull;

import java.lang.reflect.Method;

/**
 * Helper used to work around native libraries loading on some systems.
 * See <a href="https://medium.com/keepsafe-engineering/the-perils-of-loading-native-libraries-on-android-befa49dce2db">ReLinker</a> for more details.
 */
class LibraryLoader {
	static final String SURFACE_LIBRARY_NAME = "pl_droidsonroids_gif_surface";
	static final String BASE_LIBRARY_NAME = "pl_droidsonroids_gif";
	private static Context sAppContext;

	private LibraryLoader() {
	}

	/**
	 * Initializes loader with given `Context`. Subsequent calls should have no effect since application Context is retrieved.
	 * Libraries will not be loaded immediately but only when needed.
	 *
	 * @param context any Context except null
	 */
	public static void initialize(@NonNull final Context context) {
		sAppContext = context.getApplicationContext();
	}

	private static Context getContext() {
		if (sAppContext == null) {
			try {
				final Class<?> activityThread = Class.forName("android.app.ActivityThread");
				final Method currentApplicationMethod = activityThread.getDeclaredMethod("currentApplication");
				sAppContext = (Context) currentApplicationMethod.invoke(null);
			} catch (Exception e) {
				throw new IllegalStateException("LibraryLoader not initialized. Call LibraryLoader.initialize() before using library classes.", e);
			}
		}
		return sAppContext;
	}

	static void loadLibrary(Context context) {
		try {
			System.loadLibrary(BASE_LIBRARY_NAME);
		} catch (final UnsatisfiedLinkError e) {
			if (context == null) {
				context = getContext();
			}
			ReLinker.loadLibrary(context);
		}
	}
}
