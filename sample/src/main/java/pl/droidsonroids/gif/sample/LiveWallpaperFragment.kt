package pl.droidsonroids.gif.sample

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import pl.droidsonroids.gif.sample.wallpaper.GifWallpaperService

class LiveWallpaperFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.live_wallpaper, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.live_wallpaper_button).setOnClickListener {
            if (view.context.isLiveWallpaperSupported) {
                showWallpaperChooser()
            } else {
                Snackbar.make(view, R.string.live_wallpaper_not_supported, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showWallpaperChooser() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(requireContext(), GifWallpaperService::class.java))
        startActivity(intent)
    }

    private val Context.isLiveWallpaperSupported: Boolean
        get() {
            val wallpaperManager = getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
            val isSetWallpaperAllowed = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> wallpaperManager.isSetWallpaperAllowed
                else -> true
            }

            val isWallpaperSupportedForUser = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> wallpaperManager.isWallpaperSupported
                else -> true
            }
            val isLiveWallpaperSupportedBySystem = packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)

            return isLiveWallpaperSupportedBySystem && isWallpaperSupportedForUser && isSetWallpaperAllowed
        }
}

