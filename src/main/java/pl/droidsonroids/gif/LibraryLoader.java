package pl.droidsonroids.gif;

import android.content.Context;
import android.support.annotation.NonNull;

import java.lang.reflect.Method;

public class LibraryLoader {
    static final String SURFACE_LIBRARY_NAME = "pl_droidsonroids_gif_surface";
    static final String BASE_LIBRARY_NAME = "pl_droidsonroids_gif";
    private static Context sAppContext;

    public static void initialize(@NonNull final Context context) {
        sAppContext = context.getApplicationContext();
    }

    static Context getContext() {
        if (sAppContext == null) {
            try {
                final Class<?> activityThread = Class.forName("android.app.ActivityThread");
                final Method currentApplicationMethod = activityThread.getDeclaredMethod("currentApplication");
                sAppContext = (Context) currentApplicationMethod.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException("No context provided and ActivityThread workaround failed", e);
            }
        }
        return sAppContext;
    }

    static void loadLibrary(Context context, final String library) {
        try {
            System.loadLibrary(library);
        } catch (final UnsatisfiedLinkError e) {
            if (SURFACE_LIBRARY_NAME.equals(library)) {
                loadLibrary(context, BASE_LIBRARY_NAME);
            }
            if (context == null) {
                context = getContext();
            }
            ReLinker.loadLibrary(context, library);
        }
    }
}
