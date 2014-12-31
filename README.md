android-gif-drawable
====================
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pl.droidsonroids.gif/android-gif-drawable/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/pl.droidsonroids.gif/android-gif-drawable)
[![Build Status](https://travis-ci.org/koral--/android-gif-drawable.png?branch=master)](https://travis-ci.org/koral--/android-gif-drawable)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-android--gif--drawable-brightgreen.svg?style=flat)](https://android-arsenal.com/details/1/1147)

`View`s and `Drawable` for animated GIFs in Android.

## Overview
Bundled GIFLib via JNI is used to render frames. This way should be more efficient than `WebView` or `Movie` classes.

## Download

**[Latest release downloads](https://github.com/koral--/android-gif-drawable/releases/latest)**

### Setup

#### Gradle (Android Studio)
Insert the following dependency to `build.gradle` file of your project.
```groovy
dependencies {
    compile 'pl.droidsonroids.gif:android-gif-drawable:1.1.+'
}
```
Note that Maven central repository should be defined eg. in top-level `build.gradle` like this:
```groovy 
buildscript {
    repositories {
        mavenCentral()
    }
}
allprojects {
    repositories {
        mavenCentral()
    }
}
```

#### Maven dependency

```xml
<dependency>
	<groupId>pl.droidsonroids.gif</groupId>
	<artifactId>android-gif-drawable</artifactId>
	<version>insert latest version here</version>
	<type>aar</type>
</dependency>
```

####<a name="proguard"></a> Proguard configuration
Add following line to proguard configuration file (usually `proguard-rules.txt` or `proguard-project.txt`):
```
-keep public class pl.droidsonroids.gif.GifIOException{<init>(int);}
-keep class pl.droidsonroids.gif.GifInfoHandle{<init>(long,int,int,int);}
```

###Requirements
+ Android 2.2+ (API level 8+)

####Building from source
+ [Android NDK](http://developer.android.com/tools/sdk/ndk/index.html) needed to compile native sources

##Usage

###[Sample project](https://github.com/koral--/android-gif-drawable-sample)
Sample project is under construction. Not all features are covered yet.

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
InputStreams are closed automatically in finalizer if GifDrawable is no longer needed 
so you don't need to explicitly close them. Calling `recycle()` will also close 
underlaying input source. 

Note that all input sources need to have ability to rewind to the begining. It is required to correctly play animated GIFs 
(where animation is repeatable) since subsequent frames are decoded on demand from source.

####Animation control
`GifDrawable` implements an `Animatable` and `MediaPlayerControl` so you can use its methods and more:

+ `stop()` - stops the animation, can be called from any thread
+ `start()` - starts the animation, can be called from any thread
+ `isRunning()` - returns whether animation is currently running or not
+ `reset()` - rewinds the animation, does not restart stopped one
+ `setSpeed(float factor)` - sets new animation speed factor, eg. passing 2.0f will double the animation speed
+ `seekTo(int position)` - seeks animation (within current loop) to given `position` (in milliseconds) __Only seeking forward is supported__
+ `getDuration()` - returns duration of one loop of the animation
+ `getCurrentPosition()` - returns elapsed time from the beginning of a current loop of animation

#####Using [MediaPlayerControl](http://developer.android.com/reference/android/widget/MediaController.MediaPlayerControl.html)
Standard controls for a MediaPlayer (like in [VideoView](http://developer.android.com/reference/android/widget/VideoView.html)) can be used to control GIF animation and show its current progress.

Just set `GifDrawable` as MediaPlayer on your [MediaController](http://developer.android.com/reference/android/widget/MediaController.html) like this:
```java
	@Override
	protected void onCreate ( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		GifImageButton gib = new GifImageButton( this );
		setContentView( gib );
		gib.setImageResource( R.drawable.sample );
		final MediaController mc = new MediaController( this );
		mc.setMediaPlayer( ( GifDrawable ) gib.getDrawable() );
		mc.setAnchorView( gib );
		gib.setOnClickListener( new OnClickListener()
		{
			@Override
			public void onClick ( View v )
			{
				mc.show();
			}
		} );
	}
```

####Retrieving GIF metadata

+ `getLoopCount()` - returns a loop count as defined in `NETSCAPE 2.0` extension
+ `getNumberOfFrames()` - returns number of frames (at least 1)
+ `getComment()` - returns comment text (`null` if GIF has no comment)
+ `getFrameByteCount()` - returns minimum number of bytes that can be used to store pixels of the single frame
+ `getAllocationByteCount()` - returns size (in bytes) of the allocated memory used to store pixels of given GifDrawable
+ `getInputSourceByteCount()` - returns length (in bytes) of the backing input data
+ `toString()` - returns human readable information about image size and number of frames (intended for debugging purpose)

####Advanced
 
+ `recycle()` - provided to speed up freeing memory (like in `android.graphics.Bitmap`)
+ `isRecycled()` - checks whether drawable is recycled
+ `getError()` - returns last error details

##Migration from 1.0.x
####Proguard configuration update
Proguard configuration has changed. See [Proguard configuration](#proguard) section.

####Drawable recycling behavior change
`GifDrawable` now uses `android.graphics.Bitmap` as frame buffer. Trying to access pixels (including drawing)
 of recycled `GifDrawable` will cause `IllegalStateException` like in `Bitmap`.

####Minimum SDK version changed
Minimum API level is now 8 (Android 2.2).

####Rendering moved to background thread
Rendering is performed in background thread running independently from main thread so animation is running
even if drawable is not drawn. However rendering is not running if drawable is not visible, see [#setVisible()](http://developer.android.com/reference/android/graphics/drawable/Drawable.html#setVisible(boolean, boolean)).
That method can be used to control drawable visibility in cases when it is not already handled by Android framework.

##References
This library uses code from [GIFLib](http://giflib.sourceforge.net/) 5.1.0 and [SKIA](https://code.google.com/p/skia/).

##License

MIT License<br>
See [LICENSE](LICENSE) file.
