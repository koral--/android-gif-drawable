package pl.droidsonroids.gif.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import pl.droidsonroids.gif.AnimationListener
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

class AnimationControlFragment : Fragment(), AnimationListener {

	private lateinit var gifDrawable: GifDrawable

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return inflater.inflate(R.layout.animation_control, container, false)
	}

	private fun resetAnimation(buttonToggle: ToggleButton) {
		gifDrawable.stop()
		gifDrawable.loopCount = 4
		gifDrawable.seekToFrameAndGet(5)
		buttonToggle.isChecked = false
	}

	private fun toggleAnimation() = when {
		gifDrawable.isPlaying -> gifDrawable.stop()
		else -> gifDrawable.start()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val buttonToggle = view.findViewById<ToggleButton>(R.id.buttonToggle)
		view.findViewById<View>(R.id.buttonReset).setOnClickListener { resetAnimation(buttonToggle) }
		buttonToggle.setOnClickListener { toggleAnimation() }
		gifDrawable = view.findViewById<GifImageView>(R.id.gifImageView).drawable as GifDrawable

		resetAnimation(view.findViewById(R.id.buttonToggle))
		gifDrawable.addAnimationListener(this)
	}

	override fun onDestroyView() {
		gifDrawable.removeAnimationListener(this)
		super.onDestroyView()
	}

	override fun onAnimationCompleted(loopNumber: Int) {
		val view = view
		if (view != null) {
			Snackbar.make(view, getString(R.string.animation_loop_completed, loopNumber), Snackbar.LENGTH_SHORT).show()
		}
	}
}
