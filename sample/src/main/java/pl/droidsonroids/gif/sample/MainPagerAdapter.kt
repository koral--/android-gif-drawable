package pl.droidsonroids.gif.sample

import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import pl.droidsonroids.gif.sample.sources.GifSourcesFragment

internal class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
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

    fun getPageTitle(position: Int): CharSequence = pageTitles[position]
    override fun getItemCount() = pageTitles.size

    override fun createFragment(position: Int) = fragments[position]
}
