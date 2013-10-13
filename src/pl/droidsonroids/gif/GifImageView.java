package pl.droidsonroids.gif;

import java.io.IOException;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.util.AttributeSet;
import android.widget.ImageView;

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
	 * @param defStyle
	 */
	public GifImageView ( Context context, AttributeSet attrs )
	{
		super( context, attrs );
		trySetGifDrawable(attrs);
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
		trySetGifDrawable(attrs);
	}

	@Override
	public void setImageResource ( int resId )
	{
		setResource(true, resId, true );
	}
	@Override
	public void setBackgroundResource ( int resId )
	{
		setResource(false, resId, true );
	}
	
	private void trySetGifDrawable(AttributeSet attrs)
	{
		final Resources res=getResources();
		int resId=attrs.getAttributeResourceValue( ANDROID_NS, "src", -1 );
		if (resId>0&&"drawable".equals( res.getResourceTypeName( resId )  ))
			setResource(true, resId, false );
	
		resId=attrs.getAttributeResourceValue( ANDROID_NS, "background", -1 );
		if (resId>0&&"drawable".equals( res.getResourceTypeName( resId )  ))
			setResource(false, resId, false );		
	}
	
	@SuppressWarnings ( "deprecation" ) //new method not avalilable on older API levels
	private void setResource(boolean isSrc, int resId, boolean defaultToSuper)
	{
		try
		{
			GifDrawable d = new GifDrawable( getResources(), resId );
			if (isSrc)
				setImageDrawable(d);
			else
				setBackgroundDrawable( d );
			return;
		}
		catch ( IOException e )
		{
			//ignored
		}
		catch (NotFoundException e) 
		{
			//ignored
		}		
		if (defaultToSuper)
		{
			if (isSrc)
				super.setImageResource( resId );
			else
				super.setBackgroundResource( resId );
		}
	}
}
