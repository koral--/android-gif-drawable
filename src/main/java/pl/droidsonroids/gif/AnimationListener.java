package pl.droidsonroids.gif;

/**
 * Interface which can be used to run some code when particular animation event occurs.
 */
public interface AnimationListener {
    /**
     * Called when a single loop of the animation is completed.
     */
    void onAnimationCompleted();
}
