package pl.droidsonroids.gif;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView.ScaleType;

import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * <p>{@link TextureView} which can display animated GIFs. GifTextureView can only be used in a
 * hardware accelerated window. When rendered in software, GifTextureView will draw nothing.</p>
 * <p>GIF source can be specified in XML or by calling {@link #setInputSource(InputSource)}</p>
 * <pre> {@code
 *     <pl.droidsonroids.gif.GifTextureView
 *          xmlns:app="http://schemas.android.com/apk/res-auto"
 *          android:id="@+id/gif_texture_view"
 *          android:scaleType="fitEnd"
 *          app:gifSource="@drawable/animation"
 *          android:layout_width="match_parent"
 *          android:layout_height="match_parent"> }
 * </pre>
 * Note that <b>src</b> attribute comes from app namespace (you can call it whatever you want) not from
 * android one. Drawable, raw, mipmap resources and assets can be specified through XML. If value is a string
 * (referenced from resources or entered directly) it will be treated as an asset.
 * <p>Unlike {@link TextureView} GifTextureView is transparent by default, but it can be changed by
 * {@link #setOpaque(boolean)}.
 * You can use scale types the same way as in {@link android.widget.ImageView}.</p>
 */
public class GifTextureView extends TextureView {

	private static final ScaleType[] sScaleTypeArray = {
			ScaleType.MATRIX,
			ScaleType.FIT_XY,
			ScaleType.FIT_START,
			ScaleType.FIT_CENTER,
			ScaleType.FIT_END,
			ScaleType.CENTER,
			ScaleType.CENTER_CROP,
			ScaleType.CENTER_INSIDE
	};
	private ScaleType mScaleType = ScaleType.FIT_CENTER;
	private final Matrix mTransform = new Matrix();
	private InputSource mInputSource;
	private RenderThread mRenderThread;
	private float mSpeedFactor = 1f;
	private GifViewUtils.GifViewAttributes viewAttributes;

	public GifTextureView(Context context) {
		super(context);
		init(null, 0, 0);
	}

	public GifTextureView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs, 0, 0);
	}

	public GifTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(attrs, defStyleAttr, 0);
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public GifTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init(attrs, defStyleAttr, defStyleRes);
	}

	private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		if (attrs != null) {
			final int scaleTypeIndex = attrs.getAttributeIntValue(GifViewUtils.ANDROID_NS, "scaleType", -1);
			if (scaleTypeIndex >= 0 && scaleTypeIndex < sScaleTypeArray.length) {
				mScaleType = sScaleTypeArray[scaleTypeIndex];
			}
			final TypedArray textureViewAttributes = getContext().obtainStyledAttributes(attrs, R.styleable
					.GifTextureView, defStyleAttr, defStyleRes);
			mInputSource = findSource(textureViewAttributes);
			super.setOpaque(textureViewAttributes.getBoolean(R.styleable.GifTextureView_isOpaque, false));
			textureViewAttributes.recycle();
			viewAttributes = new GifViewUtils.GifViewAttributes(this, attrs, defStyleAttr, defStyleRes);
		} else {
			super.setOpaque(false);
			viewAttributes = new GifViewUtils.GifViewAttributes();
		}
		if (!isInEditMode()) {
			mRenderThread = new RenderThread(this);
			if (mInputSource != null) {
				mRenderThread.start();
			}
		}
	}

	/**
	 * Always throws {@link UnsupportedOperationException}. Changing {@link SurfaceTextureListener}
	 * is not supported.
	 *
	 * @param listener ignored
	 */
	@Override
	public void setSurfaceTextureListener(SurfaceTextureListener listener) {
		throw new UnsupportedOperationException("Changing SurfaceTextureListener is not supported");
	}

	/**
	 * Always returns null since changing {@link SurfaceTextureListener} is not supported.
	 *
	 * @return always null
	 */
	@Override
	public SurfaceTextureListener getSurfaceTextureListener() {
		return null;
	}

	/**
	 * Always throws {@link UnsupportedOperationException}. Changing {@link SurfaceTexture} is not
	 * supported.
	 *
	 * @param surfaceTexture ignored
	 */
	@Override
	public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
		throw new UnsupportedOperationException("Changing SurfaceTexture is not supported");
	}

	private static InputSource findSource(final TypedArray textureViewAttributes) {
		final TypedValue value = new TypedValue();
		if (!textureViewAttributes.getValue(R.styleable.GifTextureView_gifSource, value)) {
			return null;
		}

		if (value.resourceId != 0) {
			final String resourceTypeName = textureViewAttributes.getResources().getResourceTypeName(value.resourceId);
			if (GifViewUtils.SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName)) {
				return new InputSource.ResourcesSource(textureViewAttributes.getResources(), value.resourceId);
			} else if (!"string".equals(resourceTypeName)) {
				throw new IllegalArgumentException(
						"Expected string, drawable, mipmap or raw resource type. '" + resourceTypeName
								+ "' is not supported");
			}
		}
		return new InputSource.AssetSource(textureViewAttributes.getResources().getAssets(), value.string.toString());
	}

	private static class RenderThread extends Thread implements SurfaceTextureListener {

		final ConditionVariable isSurfaceValid = new ConditionVariable();
		private GifInfoHandle mGifInfoHandle = new GifInfoHandle();
		private IOException mIOException;
		long[] mSavedState;
		private final WeakReference<GifTextureView> mGifTextureViewReference;

		RenderThread(final GifTextureView gifTextureView) {
			super("GifRenderThread");
			mGifTextureViewReference = new WeakReference<>(gifTextureView);
		}

		@Override
		public void run() {
			try {
				final GifTextureView gifTextureView = mGifTextureViewReference.get();
				if (gifTextureView == null) {
					return;
				}
				mGifInfoHandle = gifTextureView.mInputSource.open();
				mGifInfoHandle.setOptions((char) 1, gifTextureView.isOpaque());
				if (gifTextureView.viewAttributes.mLoopCount >= 0) {
					mGifInfoHandle.setLoopCount(gifTextureView.viewAttributes.mLoopCount);
				}
			} catch (IOException ex) {
				mIOException = ex;
				return;
			}

			final GifTextureView gifTextureView = mGifTextureViewReference.get();
			if (gifTextureView == null) {
				mGifInfoHandle.recycle();
				return;
			}

			gifTextureView.setSuperSurfaceTextureListener(this);
			final boolean isSurfaceAvailable = gifTextureView.isAvailable();
			isSurfaceValid.set(isSurfaceAvailable);
			if (isSurfaceAvailable) {
				gifTextureView.post(new Runnable() {
					@Override
					public void run() {
						gifTextureView.updateTextureViewSize(mGifInfoHandle);
					}
				});
			}
			mGifInfoHandle.setSpeedFactor(gifTextureView.mSpeedFactor);

			while (!isInterrupted()) {
				try {
					isSurfaceValid.block();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				final GifTextureView currentGifTextureView = mGifTextureViewReference.get();
				if (currentGifTextureView == null) {
					break;
				}
				final SurfaceTexture surfaceTexture = currentGifTextureView.getSurfaceTexture();
				if (surfaceTexture == null) {
					continue;
				}
				final Surface surface = new Surface(surfaceTexture);
				try {
					mGifInfoHandle.bindSurface(surface, mSavedState);
				} finally {
					surface.release();
				}
			}
			mGifInfoHandle.recycle();
			mGifInfoHandle = new GifInfoHandle();
		}

		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			final GifTextureView gifTextureView = mGifTextureViewReference.get();
			if (gifTextureView != null) {
				gifTextureView.updateTextureViewSize(mGifInfoHandle);
			}
			isSurfaceValid.open();
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
			//no-op
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			isSurfaceValid.close();
			mGifInfoHandle.postUnbindSurface();
			interrupt();
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {
			//no-op
		}

		void dispose(@NonNull final GifTextureView gifTextureView, @Nullable final PlaceholderDrawListener drawer) {
			isSurfaceValid.close();
			final SurfaceTextureListener listener =
					drawer != null ? new PlaceholderDrawingSurfaceTextureListener(drawer) : null;
			gifTextureView.setSuperSurfaceTextureListener(listener);
			mGifInfoHandle.postUnbindSurface();
			interrupt();
		}
	}

	private void setSuperSurfaceTextureListener(SurfaceTextureListener listener) {
		super.setSurfaceTextureListener(listener);
	}

	/**
	 * Indicates whether the content of this GifTextureView is opaque. The
	 * content is assumed to be <b>non-opaque</b> by default (unlike {@link TextureView}.
	 * View that is known to be opaque can take a faster drawing case than non-opaque one.<br>
	 * Opacity change will cause animation to restart.
	 *
	 * @param opaque True if the content of this GifTextureView is opaque,
	 *               false otherwise
	 */
	@Override
	public void setOpaque(boolean opaque) {
		if (opaque != isOpaque()) {
			super.setOpaque(opaque);
			setInputSource(mInputSource);
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		mRenderThread.dispose(this, null);
		super.onDetachedFromWindow();
		final SurfaceTexture surfaceTexture = getSurfaceTexture();
		if (surfaceTexture != null) {
			surfaceTexture.release();
		}
	}

	/**
	 * Sets the source of the animation. Pass {@code null} to remove current source.
	 * Equivalent of {@code setInputSource(inputSource, null)}.
	 *
	 * @param inputSource new animation source, may be null
	 */
	public synchronized void setInputSource(@Nullable InputSource inputSource) {
		setInputSource(inputSource, null);
	}

	/**
	 * Sets the source of the animation and optionally placeholder drawer. Pass {@code null inputSource} to remove current source.
	 * {@code placeholderDrawListener} is overwritten on {@code setInputSource(inputSource)} call.
	 *
	 * @param inputSource             new animation source, may be null
	 * @param placeholderDrawListener placeholder draw listener, may be null
	 */
	public synchronized void setInputSource(@Nullable InputSource inputSource,
			@Nullable PlaceholderDrawListener placeholderDrawListener) {
		mRenderThread.dispose(this, placeholderDrawListener);
		try {
			mRenderThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mInputSource = inputSource;
		mRenderThread = new RenderThread(this);
		if (inputSource != null) {
			mRenderThread.start();
		} else {
			clearSurface();
		}
	}

	private void clearSurface() {
		final SurfaceTexture surfaceTexture = getSurfaceTexture();
		if (surfaceTexture != null) {
			final Surface surface = new Surface(surfaceTexture);
			try {
				surface.unlockCanvasAndPost(surface.lockCanvas(null));
			} finally {
				surface.release();
			}
		}
	}

	/**
	 * Equivalent of {@link GifDrawable#setSpeed(float)}.
	 *
	 * @param factor new speed factor, eg. 0.5f means half speed, 1.0f - normal, 2.0f - double speed
	 * @throws IllegalArgumentException if {@code factor <= 0}
	 * @see GifDrawable#setSpeed(float)
	 */
	public void setSpeed(@FloatRange(from = 0, fromInclusive = false) float factor) {
		mSpeedFactor = factor;
		mRenderThread.mGifInfoHandle.setSpeedFactor(factor);
	}

	/**
	 * Returns last {@link IOException} occurred during loading or playing GIF (in such case only {@link GifIOException}
	 * can be returned. Null is returned when source is not set, surface was not yet created or no error
	 * occurred.
	 *
	 * @return exception occurred during loading or playing GIF or null
	 */
	@Nullable
	public IOException getIOException() {
		if (mRenderThread.mIOException != null) {
			return mRenderThread.mIOException;
		} else {
			return GifIOException.fromCode(mRenderThread.mGifInfoHandle.getNativeErrorCode());
		}
	}

	/**
	 * Controls how the image should be resized or moved to match the size
	 * of this GifTextureView.
	 *
	 * @param scaleType The desired scaling mode.
	 */
	public void setScaleType(@NonNull ScaleType scaleType) {
		mScaleType = scaleType;
		updateTextureViewSize(mRenderThread.mGifInfoHandle);
	}

	/**
	 * @return the current scale type in use by this View.
	 * @see ScaleType
	 */
	public ScaleType getScaleType() {
		return mScaleType;
	}

	private void updateTextureViewSize(final GifInfoHandle gifInfoHandle) {
		final Matrix transform = new Matrix();
		final float viewWidth = getWidth();
		final float viewHeight = getHeight();
		final float scaleRef;
		final float scaleX = gifInfoHandle.getWidth() / viewWidth;
		final float scaleY = gifInfoHandle.getHeight() / viewHeight;
		RectF src = new RectF(0, 0, gifInfoHandle.getWidth(), gifInfoHandle.getHeight());
		RectF dst = new RectF(0, 0, viewWidth, viewHeight);
		switch (mScaleType) {
			case CENTER:
				transform.setScale(scaleX, scaleY, viewWidth / 2, viewHeight / 2);
				break;
			case CENTER_CROP:
				scaleRef = 1 / Math.min(scaleX, scaleY);
				transform.setScale(scaleRef * scaleX, scaleRef * scaleY, viewWidth / 2, viewHeight / 2);
				break;
			case CENTER_INSIDE:
				if (gifInfoHandle.getWidth() <= viewWidth && gifInfoHandle.getHeight() <= viewHeight) {
					scaleRef = 1.0f;
				} else {
					scaleRef = Math.min(1 / scaleX, 1 / scaleY);
				}
				transform.setScale(scaleRef * scaleX, scaleRef * scaleY, viewWidth / 2, viewHeight / 2);
				break;
			case FIT_CENTER:
				transform.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
				transform.preScale(scaleX, scaleY);
				break;
			case FIT_END:
				transform.setRectToRect(src, dst, Matrix.ScaleToFit.END);
				transform.preScale(scaleX, scaleY);
				break;
			case FIT_START:
				transform.setRectToRect(src, dst, Matrix.ScaleToFit.START);
				transform.preScale(scaleX, scaleY);
				break;
			case FIT_XY:
				return;
			case MATRIX:
				transform.set(mTransform);
				transform.preScale(scaleX, scaleY);
				break;
		}
		super.setTransform(transform);
	}

	/**
	 * Wrapper of {@link #setTransform(Matrix)}. Introduced to preserve the same API as in
	 * {@link GifImageView}.
	 *
	 * @param matrix The transform to apply to the content of this view.
	 */
	public void setImageMatrix(Matrix matrix) {
		setTransform(matrix);
	}

	/**
	 * Works like {@link TextureView#setTransform(Matrix)} but transform will take effect only if
	 * scale type is set to {@link ScaleType#MATRIX} through XML attribute or via {@link #setScaleType(ScaleType)}
	 *
	 * @param transform The transform to apply to the content of this view.
	 */
	@Override
	public void setTransform(Matrix transform) {
		mTransform.set(transform);
		updateTextureViewSize(mRenderThread.mGifInfoHandle);
	}

	/**
	 * Returns the transform associated with this texture view, either set explicitly by {@link #setTransform(Matrix)}
	 * or computed according to the current scale type.
	 *
	 * @param transform The {@link Matrix} in which to copy the current transform. Can be null.
	 * @return The specified matrix if not null or a new {@link Matrix} instance otherwise.
	 * @see #setTransform(android.graphics.Matrix)
	 * @see #setScaleType(ScaleType)
	 */
	@Override
	public Matrix getTransform(Matrix transform) {
		if (transform == null) {
			transform = new Matrix();
		}
		transform.set(mTransform);
		return transform;
	}

	@Override
	public Parcelable onSaveInstanceState() {
		mRenderThread.mSavedState = mRenderThread.mGifInfoHandle.getSavedState();
		return new GifViewSavedState(super.onSaveInstanceState(),
				viewAttributes.freezesAnimation ? mRenderThread.mSavedState : null);
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (!(state instanceof GifViewSavedState)) {
			super.onRestoreInstanceState(state);
			return;
		}
		GifViewSavedState ss = (GifViewSavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		mRenderThread.mSavedState = ss.mStates[0];
	}

	/**
	 * Sets whether animation position is saved in {@link #onSaveInstanceState()} and restored
	 * in {@link #onRestoreInstanceState(Parcelable)}
	 *
	 * @param freezesAnimation whether animation position is saved
	 */
	public void setFreezesAnimation(boolean freezesAnimation) {
		viewAttributes.freezesAnimation = freezesAnimation;
	}

	/**
	 * This listener can be used to be notified when the {@link GifTextureView} content placeholder can be drawn.
	 * Placeholder is displayed before proper input source is loaded and remains visible when input source loading fails.
	 */
	public interface PlaceholderDrawListener {
		/**
		 * Called when surface is ready and placeholder has to be drawn.
		 * It may occur more than once (eg. if {@code View} visibility is toggled before input source is loaded)
		 * or never (eg. when {@code View} is never visible).<br>
		 * Note that it is an error to use {@code canvas} after this method return.
		 *
		 * @param canvas canvas to draw into
		 */
		void onDrawPlaceholder(Canvas canvas);
	}
}
