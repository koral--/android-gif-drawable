package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.TextView;

import java.io.IOException;

/**
 * A {@link TextView} which handles GIFs as compound drawables. NOTE:
 * {@code android:drawableStart} and {@code android:drawableEnd} from XML are
 * not supported but can be set using
 * {@link #setCompoundDrawablesRelativeWithIntrinsicBounds(int, int, int, int)}
 * 
 * @author koral--
 * 
 */
public class GifTextView extends TextView
{

	/**
	 * @param context
	 */
	public GifTextView ( Context context )
	{
		super( context );
	}

	/**
	 * @param context
	 * @param attrs
	 */
	public GifTextView ( Context context, AttributeSet attrs )
	{
		super( context, attrs );
		parseAttrs( attrs );
	}

	/**
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public GifTextView ( Context context, AttributeSet attrs, int defStyle )
	{
		super( context, attrs, defStyle );
		parseAttrs( attrs );
	}

	@TargetApi ( Build.VERSION_CODES.JELLY_BEAN_MR1 )
	private void parseAttrs ( AttributeSet attrs )
	{
		if ( attrs != null )
		{
			Drawable left = getGifOrDefaultDrawable( attrs.getAttributeResourceValue( GifImageView.ANDROID_NS, "drawableLeft", 0 ) ), right = getGifOrDefaultDrawable( attrs
					.getAttributeResourceValue( GifImageView.ANDROID_NS, "drawableRight", 0 ) ), top = getGifOrDefaultDrawable( attrs
					.getAttributeResourceValue( GifImageView.ANDROID_NS, "drawableTop", 0 ) ), bottom = getGifOrDefaultDrawable( attrs
					.getAttributeResourceValue( GifImageView.ANDROID_NS, "drawableBottom", 0 ) ), start = getGifOrDefaultDrawable( attrs
					.getAttributeResourceValue( GifImageView.ANDROID_NS, "drawableStart", 0 ) ), end = getGifOrDefaultDrawable( attrs
					.getAttributeResourceValue( GifImageView.ANDROID_NS, "drawableEnd", 0 ) );
			setCompoundDrawablesWithIntrinsicBounds( left, top, right, bottom );

			if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 )
				setCompoundDrawablesRelativeWithIntrinsicBounds( start, top, end, bottom );
			setBackgroundInternal( getGifOrDefaultDrawable( attrs.getAttributeResourceValue( GifImageView.ANDROID_NS, "background", 0 ) ) );
		}
	}

	@TargetApi ( Build.VERSION_CODES.JELLY_BEAN )
	// setBackground
	@SuppressWarnings ( "deprecation" )
	// setBackgroundDrawable
	private void setBackgroundInternal ( Drawable bg )
	{
		if ( Build.VERSION.SDK_INT >= 16 )
			setBackground( bg );
		else
			setBackgroundDrawable( bg );
	}

	@Override
	public void setBackgroundResource ( int resid )
	{
		setBackgroundInternal( getGifOrDefaultDrawable( resid ) );
	}

	private Drawable getGifOrDefaultDrawable ( int resId )
	{
		if ( resId == 0 )
			return null;
		final Resources resources = getResources();
		if ( !isInEditMode() && "drawable".equals( resources.getResourceTypeName( resId ) ) )
			try
			{
				return new GifDrawable( resources, resId );
			}
			catch ( IOException ignored )
			{
				// ignored
			}
			catch ( NotFoundException ignored )
			{
				//ignored
			}
		return resources.getDrawable( resId );
	}

	@TargetApi ( Build.VERSION_CODES.JELLY_BEAN_MR1 )
	@Override
	public void setCompoundDrawablesRelativeWithIntrinsicBounds ( int start, int top, int end, int bottom )
	{
		setCompoundDrawablesRelativeWithIntrinsicBounds( getGifOrDefaultDrawable( start ), getGifOrDefaultDrawable( top ),
				getGifOrDefaultDrawable( end ), getGifOrDefaultDrawable( bottom ) );
	}

	@Override
	public void setCompoundDrawablesWithIntrinsicBounds ( int left, int top, int right, int bottom )
	{
		setCompoundDrawablesWithIntrinsicBounds( getGifOrDefaultDrawable( left ), getGifOrDefaultDrawable( top ),
				getGifOrDefaultDrawable( right ), getGifOrDefaultDrawable( bottom ) );
	}
}
