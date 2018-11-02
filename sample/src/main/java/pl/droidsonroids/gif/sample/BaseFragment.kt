package pl.droidsonroids.gif.sample

import androidx.fragment.app.Fragment

abstract class BaseFragment : Fragment() {
	override fun onDestroy() {
		super.onDestroy()
		(activity as MainActivity).refWatcher.watch(this)
	}
}