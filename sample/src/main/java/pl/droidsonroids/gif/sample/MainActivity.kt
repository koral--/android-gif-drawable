package pl.droidsonroids.gif.sample

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Main activity, hosts the pager

 * @author koral--
 */
class MainActivity : FragmentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
		val pager = findViewById<ViewPager2>(R.id.mainViewPager)
		val mainPagerAdapter = MainPagerAdapter(this)

		pager.adapter = mainPagerAdapter
		TabLayoutMediator(tabLayout, pager) { tab, position ->
			tab.text = mainPagerAdapter.getPageTitle(position)
		}.attach()
	}
}
