package pl.droidsonroids.gif.sample

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager

/**
 * Main activity, hosts the pager

 * @author koral--
 */
class MainActivity : FragmentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		findViewById<ViewPager>(R.id.main_pager).adapter = MainPagerAdapter(this)
	}
}
