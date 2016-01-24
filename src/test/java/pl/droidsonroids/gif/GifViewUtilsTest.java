package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.test.mock.MockResources;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import org.junit.Test;

import static android.util.DisplayMetrics.DENSITY_DEFAULT;
import static android.util.DisplayMetrics.DENSITY_HIGH;
import static android.util.DisplayMetrics.DENSITY_LOW;
import static android.util.DisplayMetrics.DENSITY_MEDIUM;
import static android.util.DisplayMetrics.DENSITY_TV;
import static android.util.DisplayMetrics.DENSITY_XHIGH;
import static android.util.DisplayMetrics.DENSITY_XXHIGH;
import static android.util.DisplayMetrics.DENSITY_XXXHIGH;
import static org.junit.Assert.assertEquals;
import static pl.droidsonroids.gif.GifViewUtils.getDensityScale;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GifViewUtilsTest {

    private static final double DELTA = 0.00001;

    private static Resources getMockedResources(final int resourceDensity, final int displayDensity) {
        return new MockResources() {
            @Override
            public void getValue(int id, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
                outValue.density = resourceDensity;
            }

            @Override
            public DisplayMetrics getDisplayMetrics() {
                final DisplayMetrics displayMetrics = new DisplayMetrics();
                displayMetrics.densityDpi = displayDensity;
                return displayMetrics;
            }
        };
    }

    @Test
    public void testHighResourceDensities() throws Exception {
        assertEquals(1, getDensityScale(getMockedResources(DENSITY_HIGH, DENSITY_HIGH), 0), DELTA);
        assertEquals(0.5, getDensityScale(getMockedResources(DENSITY_HIGH, DENSITY_LOW), 0), DELTA);
        assertEquals(2.0 / 3, getDensityScale(getMockedResources(DENSITY_HIGH, DENSITY_MEDIUM), 0), DELTA);
        assertEquals(4.0 / 3, getDensityScale(getMockedResources(DENSITY_HIGH, DENSITY_XHIGH), 0), DELTA);
        assertEquals(2, getDensityScale(getMockedResources(DENSITY_HIGH, DENSITY_XXHIGH), 0), DELTA);
        assertEquals(8.0 / 3, getDensityScale(getMockedResources(DENSITY_HIGH, DENSITY_XXXHIGH), 0), DELTA);
        assertEquals(213.0 / 240, getDensityScale(getMockedResources(DENSITY_HIGH, DENSITY_TV), 0), DELTA);
    }

    @Test
    public void testLowHighDensity() throws Exception {
        assertEquals(2, getDensityScale(getMockedResources(DENSITY_LOW, DENSITY_HIGH), 0), DELTA);
    }

    @Test
    public void testNoneResourceDensities() throws Exception {
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_NONE, DENSITY_LOW), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_NONE, DENSITY_MEDIUM), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_NONE, DENSITY_DEFAULT), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_NONE, DENSITY_TV), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_NONE, DENSITY_HIGH), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_NONE, DENSITY_XXHIGH), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_NONE, DENSITY_XXXHIGH), 0), DELTA);
    }

    @Test
    public void testNoneDisplayDensities() throws Exception {
        assertEquals(1, getDensityScale(getMockedResources(DENSITY_LOW, Bitmap.DENSITY_NONE), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(DENSITY_MEDIUM, Bitmap.DENSITY_NONE), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(TypedValue.DENSITY_DEFAULT, Bitmap.DENSITY_NONE), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(DENSITY_HIGH, Bitmap.DENSITY_NONE), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(DENSITY_XXHIGH, Bitmap.DENSITY_NONE), 0), DELTA);
    }

    @Test
    public void testInvalidDensities() throws Exception {
        assertEquals(1, getDensityScale(getMockedResources(DENSITY_HIGH, -1), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(-1, DENSITY_HIGH), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(-1, -1), 0), DELTA);
        assertEquals(1, getDensityScale(getMockedResources(DENSITY_HIGH, 0), 0), DELTA);
    }
}