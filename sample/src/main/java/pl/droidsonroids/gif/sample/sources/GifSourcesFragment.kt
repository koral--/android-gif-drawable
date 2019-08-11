package pl.droidsonroids.gif.sample.sources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Fragment with various GIF sources examples
 */
class GifSourcesFragment : Fragment() {

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val recyclerView = RecyclerView(inflater.context)
		recyclerView.layoutManager = LinearLayoutManager(inflater.context)
		recyclerView.adapter = GifSourcesAdapter(inflater.context)
		return recyclerView
	}
}