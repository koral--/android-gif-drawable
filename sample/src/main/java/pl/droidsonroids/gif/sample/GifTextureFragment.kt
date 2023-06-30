package pl.droidsonroids.gif.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import pl.droidsonroids.gif.GifTextureView

class GifTextureFragment : Fragment() {

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
			inflater.inflate(R.layout.texture, container, false)

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		if (!view.findViewById<GifTextureView>(R.id.gifTextureView).isHardwareAccelerated) {
			view.findViewById<View>(R.id.textTextureViewStub).visibility = View.VISIBLE
		}
	}
}
