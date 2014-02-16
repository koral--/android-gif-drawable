package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.io.IOException;

/**
 * An {@link ImageView} which tries treating background and src as {@link GifDrawable}
 * @author koral--
 */
public class GifImageView extends ImageView
{
	static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

	/**
	 * A corresponding superclass constructor wrapper.
	 * @see ImageView#ImageView(Context)
	 * @param context
	 */
	public GifImageView ( Context context )
	{
		super( context );
	}

	/**
	 * Like eqivalent from superclass but also try to interpret src and background
	 * attributes as {@link GifDrawable}.
	 * @see ImageView#ImageView(Context, AttributeSet)
	 * @param context
	 * @param attrs
	 */
	public GifImageView ( Context context, AttributeSet attrs )
	{
		super( context, attrs );
		trySetGifDrawable( attrs, getResources() );
	}

	/**
	 * Like eqivalent from superclass but also try to interpret src and background
	 * attributes as GIFs.
	 * @see ImageView#ImageView(Context, AttributeSet, int)
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public GifImageView ( Context context, AttributeSet attrs, int defStyle )
	{
		super( context, attrs, defStyle );
		trySetGifDrawable( attrs, getResources() );
	}

	@Override
	public void setImageResource ( int resId )
	{
		setResource( true, resId, getResources() );
	}

	@Override
	public void setBackgroundResource ( int resId )
	{
		setResource( false, resId, getResources() );
	}

	void trySetGifDrawable ( AttributeSet attrs, Resources res )
	{
		if ( attrs != null && res != null && !isInEditMode() )
		{
			int resId = attrs.getAttributeResourceValue( ANDROID_NS, "src", -1 );
			if ( resId > 0 && "drawable".equals( res.getResourceTypeName( resId ) ) )
				setResource( true, resId, res );

			resId = attrs.getAttributeResourceValue( ANDROID_NS, "background", -1 );
			if ( resId > 0 && "drawable".equals( res.getResourceTypeName( resId ) ) )
				setResource( false, resId, res );
		}
	}

	@TargetApi ( Build.VERSION_CODES.JELLY_BEAN )
	@SuppressWarnings ( "deprecation" )
	//new method not avalilable on older API levels
	void setResource ( boolean isSrc, int resId, Resources res )
	{
		try
		{
			GifDrawable d = new GifDrawable( res, resId );
			if ( isSrc )
				setImageDrawable( d );
			else if ( Build.VERSION.SDK_INT >= 16 )
				setBackground( d );
			else
				setBackgroundDrawable( d );
			return;
		}
		catch ( IOException e )
		{
			//ignored
		}
		catch ( NotFoundException e )
		{
			//ignored
		}
		if ( isSrc )
			super.setImageResource( resId );
		else
			super.setBackgroundResource( resId );
	}
}
