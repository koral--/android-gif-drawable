package pl.droidsonroids.gif.sample

import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentStatePagerAdapter
import pl.droidsonroids.gif.sample.sources.GifSourcesFragment

internal class MainPagerAdapter(activity: FragmentActivity) : FragmentStatePagerAdapter(activity.supportFragmentManager) {
	private val pageTitles = activity.resources.getStringArray(R.array.pages)

	override fun getItem(position: Int) =
			when (position) {
				0 -> GifSourcesFragment()
				1 -> GifTextViewFragment()
				2 -> GifTextureFragment()
				3 -> ImageSpanFragment()
				4 -> AnimationControlFragment()
				5 -> HttpFragment()
				6 -> TexturePlaceholderFragment()
				7 -> GifTexImage2DFragment()
				8 -> AnimatedSelectorFragment()
				9 -> GifDecoderFragment()
				10 -> AboutFragment()
				else -> throw IndexOutOfBoundsException("Invalid page index")
			}

	override fun getCount() = pageTitles.size

	override fun getPageTitle(position: Int): CharSequence = pageTitles[position]
}
