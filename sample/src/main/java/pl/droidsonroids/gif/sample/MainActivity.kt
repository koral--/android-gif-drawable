package pl.droidsonroids.gif.sample

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager

import com.squareup.leakcanary.LeakCanary
import com.squareup.leakcanary.RefWatcher

/**
 * Main activity, hosts the pager

 * @author koral--
 */
class MainActivity : FragmentActivity() {

	internal lateinit var refWatcher: RefWatcher

	override fun onCreate(savedInstanceState: Bundle?) {
		refWatcher = LeakCanary.install(application)
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		findViewById<ViewPager>(R.id.main_pager).adapter = MainPagerAdapter(this)
	}

}
