package pl.droidsonroids.gif.sample

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentStatePagerAdapter

import pl.droidsonroids.gif.sample.sources.GifSourcesFragment

internal class MainPagerAdapter(act: FragmentActivity) : FragmentStatePagerAdapter(act.supportFragmentManager) {
	private val pageTitles = act.resources.getStringArray(R.array.pages)

	override fun getItem(position: Int): Fragment {
		when (position) {
			0 -> return GifSourcesFragment()
			1 -> return GifTextViewFragment()
			2 -> return GifTextureFragment()
			3 -> return ImageSpanFragment()
			4 -> return AnimationControlFragment()
			5 -> return HttpFragment()
			6 -> return TexturePlaceholderFragment()
			7 -> return GifTexImage2DFragment()
			8 -> return AnimatedSelectorFragment()
			9 -> return AboutFragment()
			else -> throw IndexOutOfBoundsException("Invalid page index")
		}
	}

	override fun getCount() = pageTitles.size

	override fun getPageTitle(position: Int): CharSequence = pageTitles[position]
}
