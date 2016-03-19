package pl.droidsonroids.gif.transforms;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.annotation.FloatRange;

/**
 * {@link Transform} which adds rounded corners.
 */
public class CornerRadiusTransform implements Transform {

	private float mCornerRadius;
	private Shader mShader;
	private final RectF mDstRectF = new RectF();

	/**
	 * @param cornerRadius corner radius, may be 0.
	 */
	public CornerRadiusTransform(@FloatRange(from = 0) float cornerRadius) {
		setCornerRadius(cornerRadius);
	}

	/**
	 * Sets the corner radius to be applied when drawing the bitmap.
	 *
	 * @param cornerRadius corner radius or 0 to remove rounding
	 */
	public void setCornerRadius(@FloatRange(from = 0) float cornerRadius) {
		cornerRadius = Math.max(0, cornerRadius);
		if (cornerRadius == mCornerRadius) {
			return;
		}
		mCornerRadius = cornerRadius;
		mShader = null;
	}

	/**
	 * @return The corner radius applied when drawing this drawable. 0 when drawable is not rounded.
	 */
	@FloatRange(from = 0)
	public float getCornerRadius() {
		return mCornerRadius;
	}

	@Override
	public void onBoundsChange(Rect bounds) {
		mDstRectF.set(bounds);
		mShader = null;
	}

	@Override
	public void onDraw(Canvas canvas, Paint paint, Bitmap buffer) {
		if (mCornerRadius == 0) {
			canvas.drawBitmap(buffer, null, mDstRectF, paint);
			return;
		}
		if (mShader == null) {
			mShader = new BitmapShader(buffer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
			final Matrix shaderMatrix = new Matrix();
			shaderMatrix.setTranslate(mDstRectF.left, mDstRectF.top);
			shaderMatrix.preScale(mDstRectF.width() / buffer.getWidth(), mDstRectF.height() / buffer.getHeight());
			mShader.setLocalMatrix(shaderMatrix);
		}
		paint.setShader(mShader);
		canvas.drawRoundRect(mDstRectF, mCornerRadius, mCornerRadius, paint);
	}
}
