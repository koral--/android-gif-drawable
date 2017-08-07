package pl.droidsonroids.gif.sample.sources

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import pl.droidsonroids.gif.sample.BaseFragment

/**
 * Fragment with various GIF sources examples
 */
class GifSourcesFragment : BaseFragment() {

	override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val recyclerView = RecyclerView(context)
		recyclerView.layoutManager = LinearLayoutManager(context)
		recyclerView.adapter = GifSourcesAdapter(context)
		return recyclerView
	}
}