package pl.droidsonroids.gif.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.animation_control.*
import pl.droidsonroids.gif.AnimationListener
import pl.droidsonroids.gif.GifDrawable

class AnimationControlFragment : Fragment(), AnimationListener {

	private lateinit var gifDrawable: GifDrawable

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return inflater.inflate(R.layout.animation_control, container, false)
	}

	private fun resetAnimation() {
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
		buttonReset.setOnClickListener { resetAnimation() }
		buttonToggle.setOnClickListener { toggleAnimation() }
		gifDrawable = gifImageView.drawable as GifDrawable

		resetAnimation()
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
