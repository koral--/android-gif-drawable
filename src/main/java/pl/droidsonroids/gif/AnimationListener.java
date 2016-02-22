package pl.droidsonroids.gif;

/**
 * Interface which can be used to run some code when particular animation event occurs.
 */
public interface AnimationListener {
	/**
	 * Called when a single loop of the animation is completed.
	 *
	 * @param loopNumber 0-based number of the completed loop, 0 for infinite animations
	 */
	void onAnimationCompleted(int loopNumber);
}
