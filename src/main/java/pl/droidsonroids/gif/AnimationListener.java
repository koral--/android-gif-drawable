package pl.droidsonroids.gif;

/**
 * Interface which can be used to run some code when particular animation event occurs.
 */
public interface AnimationListener {
    /**
     * Called when animation is played once. If loop count is infinite it will be called after each loop.
     */
    public void onAnimationCompleted();
}
