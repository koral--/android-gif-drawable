android-gif-drawable [![Build Status](https://travis-ci.org/koral--/android-gif-drawable.png?branch=master)](https://travis-ci.org/koral--/android-gif-drawable)
====================
`View`s and `Drawable` for animated GIFs in Android.

## Overview
Bundled GIFLib via JNI is used to render frames. This way should be more efficient than `WebView` or `Movie` classes.<br>
Animation starts automatically and run only if `View` with attached `GifDrawable` is visible.

## Download

**[Latest release downloads](https://github.com/koral--/android-gif-drawable/releases/latest)**

### Setup

#### Gradle (Android Studio)
Insert the following dependency to `build.gradle` file of your project.
```groovy
dependencies {
    compile 'pl.droidsonroids.gif:android-gif-drawable:1.0.+'
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

SDK with API level 19 is needed. If you don't have it in your local repository, download [maven-android-sdk-deployer](https://github.com/mosabua/maven-android-sdk-deployer)
and install SDK level 19: `mvn install -P 4.4` (from maven-android-sdk-deployer directory). Then add dependency in `pom.xml` of your project:

```xml
<dependency>
	<groupId>pl.droidsonroids.gif</groupId>
	<artifactId>android-gif-drawable</artifactId>
	<version>insert latest version here</version>
	<type>aar</type>
</dependency>
```

###Requirements
+ Android 1.6+ (API level 4+)

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
 
+ `recycle()` - provided to speed up freeing memory (like in `android.graphics.Bitmap`).
+ `getError()` - returns last error details


###References
This library uses code from [GIFLIB](http://giflib.sourceforge.net/) 5.0.5 and [SKIA](https://code.google.com/p/skia/).

##License

MIT License<br>
See [LICENSE](LICENSE) file.
