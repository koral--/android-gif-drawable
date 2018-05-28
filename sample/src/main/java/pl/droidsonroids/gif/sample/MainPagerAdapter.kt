package pl.droidsonroids.gif.sample

import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentStatePagerAdapter
import pl.droidsonroids.gif.sample.sources.GifSourcesFragment

internal class MainPagerAdapter(activity: FragmentActivity) : FragmentStatePagerAdapter(activity.supportFragmentManager) {
    private val pageTitles = activity.resources.getStringArray(R.array.pages)

    private val fragments = arrayOf(
        GifSourcesFragment(),
        GifTextViewFragment(),
        GifTextureFragment(),
        ImageSpanFragment(),
        AnimationControlFragment(),
        HttpFragment(),
        TexturePlaceholderFragment(),
        GifTexImage2DFragment(),
        AnimatedSelectorFragment(),
        GifDecoderFragment(),
        LiveWallpaperFragment(),
        AboutFragment()
    )

    override fun getItem(position: Int) = fragments[position]

    override fun getCount() = pageTitles.size

    override fun getPageTitle(position: Int): CharSequence = pageTitles[position]
}
