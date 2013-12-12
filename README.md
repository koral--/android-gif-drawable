android-gif-drawable
====================
`View`s and `Drawable` for animated GIFs in Android.

## Overview
Bundled GIFLib via JNI is used to render frames. This way should be more efficient than `WebView` or `Movie` classes.<br>
Animation starts automatically and run only if `View` with attached `GifDrawable` is visible.

## Download

**[android-gif-drawable-1.0.3.jar](https://github.com/koral--/android-gif-drawable/releases/download/v1.0.3/android-gif-drawable-1.0.3.jar)**

**[android-gif-drawable-sources-1.0.3.jar](https://github.com/koral--/android-gif-drawable/releases/download/v1.0.3/android-gif-drawable-1.0.3-sources.jar)**

**[android-gif-drawable-javadoc-1.0.3.jar](https://github.com/koral--/android-gif-drawable/releases/download/v1.0.3/android-gif-drawable-javadoc-1.0.3.jar)**

###Requirements
+ Android 1.6+ (API level 4+)

####Using JAR in Eclipse
+ following option **must be unchecked** Window>Preferences>Android>Build>**Force error when external jars contains native libraries**

####Building from source
+ [Android NDK](http://developer.android.com/tools/sdk/ndk/index.html) needed to compile native sources

##Usage

###From XML
The simplest way is to use `GifImageView` (or `GifImageButton`) like a normal `ImageView`:
```xml
<pl.droidsonroids.gif.GifImageView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:src="@drawable/src_anim"
    android:background="@drawable/bg_anim"
    />
```

If drawables declared by `android:src` and/or `android:background` are GIF files then they 
will be automatically recognized as `GifDrawable`s and animated. If given drawable is not a GIF then
mentioned Views work like plain `ImageView` and `ImageButton`.

`GifTextView` allows you to use GIFs as compound drawables and background.
```xml
<pl.droidsonroids.gif.GifTextView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:drawableTop="@drawable/left_anim"
    android:drawableStart="@drawable/left_anim"
    android:background="@drawable/bg_anim"
    />
```

###From Java code
`GifImageView`, `GifImageButton` and `GifTextView` have also hooks for setters implemented. So animated GIFs can be set by calling `setImageResource(int resId)` and `setBackgroundResource(int resId)`

`GifDrawable` can be constructed directly from various sources:

```java
		//asset file
		GifDrawable gifFromAssets = new GifDrawable( getAssets(), "anim.gif" );
		
		//resource (drawable or raw)
		GifDrawable gifFromResource = new GifDrawable( getResources(), R.drawable.anim );

		//byte array
		byte[] rawGifBytes = ...
		GifDrawable gifFromBytes = new GifDrawable( rawGifBytes );
		
		//FileDescriptor
		FileDescriptor fd = new RandomAccessFile( "/path/anim.gif", "r" ).getFD();
		GifDrawable gifFromFd = new GifDrawable( fd );
		
		//file path
		GifDrawable gifFromPath = new GifDrawable( "/path/anim.gif" );
		
		//file
		File gifFile = new File(getFilesDir(),"anim.gif");
		GifDrawable gifFromFile = new GifDrawable(gifFile);
		
		//AssetFileDescriptor
		AssetFileDescriptor afd = getAssets().openFd( "anim.gif" );
		GifDrawable gifFromAfd = new GifDrawable( afd );
				
		//InputStream (it must support marking)
		InputStream sourceIs = ...
		BufferedInputStream bis = new BufferedInputStream( sourceIs, GIF_LENGTH );
		GifDrawable gifFromStream = new GifDrawable( bis );
		
		//direct ByteBuffer
		ByteBuffer rawGifBytes = ...
		GifDrawable gifFromBytes = new GifDrawable( rawGifBytes );
		
````
Note that all input sources has ability to rewind to the begining. It is required to correctly play animated GIFs 
(where animation is repeatable) since subsequent frames are decoded on demand from source.

####Animation control
`GifDrawable` is an `Animatable` so you can use its methods and more:

+ `stop()` - stops the animation, can be called from any thread
+ `start()` - starts the animation, can be called from any thread
+ `isRunning()` - returns whether animation is currently running or not
+ `reset()` - rewinds the animation, does not restart stopped one

####Retrieving GIF metadata

+ `getLoopCount()` - returns a loop count as defined in `NETSCAPE 2.0` extension
+ `getNumberOfFrames()` - returns number of frames (at least 1)
+ `getComment()` - returns comment text (`null` if GIF has no comment)
+ `toString()` - returns human readable information about image size and number of frames (intended for debugging purpose)

####Advanced 
`recycle()` method is provided to speed up freeing memory (like in `android.graphics.Bitmap`).
If something went wrong, the reason can be investigated by calling `getError()`.


###References
This library uses code from [GIFLIB](http://giflib.sourceforge.net/) 5.0.5 and [SKIA](https://code.google.com/p/skia/).

##License

MIT License<br>
See [LICENSE](LICENSE) file.
