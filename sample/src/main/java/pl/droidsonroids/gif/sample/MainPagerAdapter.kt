package pl.droidsonroids.gif.sample

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentStatePagerAdapter
import pl.droidsonroids.gif.sample.sources.GifSourcesFragment

internal class MainPagerAdapter(activity: FragmentActivity) : FragmentStatePagerAdapter(activity.supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
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
        TextureViewFragment(),
        LiveWallpaperFragment(),
        AboutFragment()
    )

    override fun getItem(position: Int) = fragments[position]

    override fun getCount() = pageTitles.size

    override fun getPageTitle(position: Int): CharSequence = pageTitles[position]
}
