package pl.droidsonroids.gif.sample

import android.support.v4.app.Fragment

abstract class BaseFragment : Fragment() {
	override fun onDestroy() {
		super.onDestroy()
		(context as MainActivity).refWatcher.watch(this)
	}
}
