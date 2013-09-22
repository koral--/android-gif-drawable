package pl.droidsonroids.gif;

import java.io.IOException;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * An {@link ImageButton} which tries treating background and src as {@link GifDrawable}
 * @author koral--
 */
public class GifImageButton extends ImageButton
{
	/**
	 * A corresponding superclass constructor wrapper.
	 * @see ImageView#ImageView(Context)
	 * @param context
	 */
	public GifImageButton ( Context context )
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
	public GifImageButton ( Context context, AttributeSet attrs )
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
	public GifImageButton ( Context context, AttributeSet attrs, int defStyle )
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
		int resId=attrs.getAttributeResourceValue( "http://schemas.android.com/apk/res/android", "src", -1 );
		if (resId>0&&"drawable".equals( getResources().getResourceTypeName( resId )  ))
			setResource(true, resId, false );
	
		resId=attrs.getAttributeResourceValue( "http://schemas.android.com/apk/res/android", "background", -1 );
		if (resId>0&&"drawable".equals( getResources().getResourceTypeName( resId )  ))
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
		if (defaultToSuper)
		{
			if (isSrc)
				super.setImageResource( resId );
			else
				super.setBackgroundResource( resId );
		}
	}
}
