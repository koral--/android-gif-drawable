package pl.droidsonroids.gif.sample

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import kotlinx.android.synthetic.main.activity_main.*

/**
 * Main activity, hosts the pager

 * @author koral--
 */
class MainActivity : FragmentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
        mainViewPager.adapter = MainPagerAdapter(this)
	}
}
