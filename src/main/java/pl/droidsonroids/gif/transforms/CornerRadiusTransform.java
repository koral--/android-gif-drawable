package pl.droidsonroids.gif.transforms;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import pl.droidsonroids.gif.GifDrawable.Transform;

public class CornerRadiusTransform implements Transform {

    private int mCornerRadius;
    private Shader mShader;
    private final Rect mDstRect = new Rect();
    private final RectF mDstRectF = new RectF();

    public CornerRadiusTransform(int cornerRadius) {
        setCornerRadius(cornerRadius);
    }

    public void setCornerRadius(int cornerRadius) {
        cornerRadius = Math.max(0, cornerRadius);
        if (cornerRadius == mCornerRadius) {
            return;
        }
        mCornerRadius = cornerRadius;
        mShader = null;
    }

    public int getCornerRadius() {
        return mCornerRadius;
    }

    @Override
    public void onBoundsChange(Rect bounds) {
        mDstRect.set(bounds);
        mDstRectF.set(bounds);
        mShader = null;
    }

    @Override
    public void onDraw(Canvas canvas, Paint paint, Bitmap buffer) {
        if (mCornerRadius == 0) {
            canvas.drawBitmap(buffer, null, mDstRect, paint);
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
