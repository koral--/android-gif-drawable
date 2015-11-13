package pl.droidsonroids.gif;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.getkeepsafe.relinker.ReLinker;

public class WorkaroundLibraryLoader {
    static boolean areLibrariesLoaded;

    public static void loadLibraries(@NonNull final Context context) {
        ReLinker.loadLibrary(context, "pl_droidsonroids_gif");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
            ReLinker.loadLibrary(context, "pl_droidsonroids_gif_surface");
        }
        areLibrariesLoaded = true;
    }
}
