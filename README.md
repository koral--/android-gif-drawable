![horizontal.png](https://cdn.steemitimages.com/DQmacEdVnEf1f2GDZ4uga1evN3FzujdR4zbkqmiV7NscPBs/horizontal.png)
====================
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pl.droidsonroids.gif/android-gif-drawable/badge.svg)](https://maven-badges.herokuapp.com/maven-central/pl.droidsonroids.gif/android-gif-drawable)
[![Build Status](https://app.bitrise.io/app/78fd40a5596e97e7/status.svg?token=SMUtlPklcIRBODd513ZdiQ)](https://app.bitrise.io/app/78fd40a5596e97e7)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-android--gif--drawable-brightgreen.svg?style=flat)](https://android-arsenal.com/details/1/1147)
[![Android-Libs](https://img.shields.io/badge/Android--Libs-android--gif--drawable-orange.svg?style=flat)](http://android-libs.com/lib/android-gif-drawable)
[![Android Weekly](http://img.shields.io/badge/Android%20Weekly-%2393-2CB3E5.svg?style=flat)](http://androidweekly.net/issues/issue-93)
[![API](https://img.shields.io/badge/API-17%2B-blue.svg?style=flat)](https://android-arsenal.com/api?level=17)
[![Javadocs](http://www.javadoc.io/badge/pl.droidsonroids.gif/android-gif-drawable.svg)](http://www.javadoc.io/doc/pl.droidsonroids.gif/android-gif-drawable)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/koral--/android-gif-drawable/badge)](https://api.securityscorecards.dev/projects/github.com/koral--/android-gif-drawable)

`View`s and `Drawable` for animated GIFs in Android.

## Overview
Bundled GIFLib via JNI is used to render frames. This way should be more efficient than `WebView` or `Movie` classes.

### [Javadoc](http://www.javadoc.io/doc/pl.droidsonroids.gif/android-gif-drawable)

### Setup

#### Gradle (Android Studio)
Insert the following dependency to `build.gradle` file of your project.
```groovy
dependencies {
    implementation 'pl.droidsonroids.gif:android-gif-drawable:1.2.28'
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
#### Gradle, snapshot repository
Current development builds (build from `dev` branch) are published to OSS snapshot repository. To use them, specify repository URL in `repositories` block:
```groovy
repositories {
    mavenCentral()
    maven { url "https://oss.sonatype.org/content/repositories/snapshots" }
}
dependencies {
    implementation 'pl.droidsonroids.gif:android-gif-drawable:1.2.+'
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
#### Eclipse
See [Sample eclipse project](https://github.com/koral--/android-gif-drawable-eclipse-sample) with setup instructions.

#### Download

**[Latest release downloads](https://github.com/koral--/android-gif-drawable/releases/latest)**

### Requirements
+ Android 4.2+ (API level 17+)
+ for `GifTextureView` hardware-accelerated rendering
+ for `GifTexImage2D` OpenGL ES 2.0+

#### Building from source
+ [Android NDK](http://developer.android.com/tools/sdk/ndk/index.html) needed to compile native sources

## Usage

### Sample project
See `sample` directory. Sample project is under construction. Not all features are covered yet.

### From XML
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

### From Java code
`GifImageView`, `GifImageButton` and `GifTextView` have also hooks for setters implemented. So animated GIFs can be set by calling `setImageResource(int resId)` and `setBackgroundResource(int resId)`

`GifDrawable` can be constructed directly from various sources:

```java
//asset file
GifDrawable gifFromAssets = new GifDrawable( getAssets(), "anim.gif" );
		
//resource (drawable or raw)
GifDrawable gifFromResource = new GifDrawable( getResources(), R.drawable.anim );
		
//Uri
ContentResolver contentResolver = ... //can be null for file:// Uris
GifDrawable gifFromUri = new GifDrawable( contentResolver, gifUri );

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
underlying input source. 

Note that all input sources need to have ability to rewind to the beginning. It is required to correctly play animated GIFs 
(where animation is repeatable) since subsequent frames are decoded on demand from source.

#### Animation control
`GifDrawable` implements an `Animatable` and `MediaPlayerControl` so you can use its methods and more:

+ `stop()` - stops the animation, can be called from any thread
+ `start()` - starts the animation, can be called from any thread
+ `isRunning()` - returns whether animation is currently running or not
+ `reset()` - rewinds the animation, does not restart stopped one
+ `setSpeed(float factor)` - sets new animation speed factor, eg. passing 2.0f will double the animation speed
+ `seekTo(int position)` - seeks animation (within current loop) to given `position` (in milliseconds)
+ `getDuration()` - returns duration of one loop of the animation
+ `getCurrentPosition()` - returns elapsed time from the beginning of a current loop of animation

##### Using [MediaPlayerControl](http://developer.android.com/reference/android/widget/MediaController.MediaPlayerControl.html)
Standard controls for a MediaPlayer (like in [VideoView](http://developer.android.com/reference/android/widget/VideoView.html)) can be used to control GIF animation and show its current progress.

Just set `GifDrawable` as MediaPlayer on your [MediaController](http://developer.android.com/reference/android/widget/MediaController.html) like this:
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GifImageButton gib = new GifImageButton(this);
    setContentView(gib);
    gib.setImageResource(R.drawable.sample);
    final MediaController mc = new MediaController(this);
    mc.setMediaPlayer((GifDrawable) gib.getDrawable());
    mc.setAnchorView(gib);
    gib.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
            mc.show();
        }
   });
}
```

#### Retrieving GIF metadata

+ `getLoopCount()` - returns a loop count as defined in `NETSCAPE 2.0` extension
+ `getNumberOfFrames()` - returns number of frames (at least 1)
+ `getComment()` - returns comment text (`null` if GIF has no comment)
+ `getFrameByteCount()` - returns minimum number of bytes that can be used to store pixels of the single frame
+ `getAllocationByteCount()` - returns size (in bytes) of the allocated memory used to store pixels of given GifDrawable
+ `getInputSourceByteCount()` - returns length (in bytes) of the backing input data
+ `toString()` - returns human readable information about image size and number of frames (intended for debugging purpose)

#### Associating single `GifDrawable` instance with multiple `View`s

Normally single `GifDrawable` instance associated with multiple `View`s will animate only on the last one.
To solve that create `MultiCallback` instance, add `View`s to it and set callback for given drawable, e.g.:
```java
MultiCallback multiCallback = new MultiCallback();

imageView.setImageDrawable(gifDrawable);
multiCallback.addView(imageView);

anotherImageView.setImageDrawable(gifDrawable);
multiCallback.addView(anotherImageView);

gifDrawable.setCallback(multiCallback);
```

Note that if you change a drawable of e.g. `ImageView`, the callback will be removed from the previous
drawable. Thereafter, you have to reassign callback or the same `GifDrawable` instance will stop animating. 
See [#480](https://github.com/koral--/android-gif-drawable/issues/480) for more information.

#### Advanced
 
+ `recycle()` - provided to speed up freeing memory (like in `android.graphics.Bitmap`)
+ `isRecycled()` - checks whether drawable is recycled
+ `getError()` - returns last error details

## Upgrading from 1.2.15
#### Minimum SDK version changed
Minimum API level is now 17 (Android 4.2).
`armeabi` (arm v5 and v6) is no longer supported. 

## Upgrading from 1.2.8
#### Minimum SDK version changed
Minimum API level is now 14 (Android 4.0).

## Upgrading from 1.2.3
Meaningful only if consumer proguard rules (bundled with library) are **not** used (they are used by default by Gradle).
+ Proguard rule has changed to `-keep public class pl.droidsonroids.gif.GifIOException{<init>(int, java.lang.String);}` 

## Upgrading from 1.1.17
1.1.17 is the last version supporting API level 8 (Froyo). Starting from 1.2.0 minimum API level is 9 (Gingerbread).

## Upgrading from 1.1.13
Handling of several edge cases has been changed:
+ `GifDrawable#getNumberOfFrames()` now returns 0 when `GifDrawable` is recycled
+ Information included in result of `GifDrawable#toString()` when `GifDrawable` is recycled now contains zeroes only

## Upgrading from 1.1.10
It is recommended (but not required) to call `LibraryLoader.initialize()` before using `GifDrawable`. `Context` is needed in some cases
when native libraries cannot be extracted normally. See [ReLinker](https://medium.com/keepsafe-engineering/the-perils-of-loading-native-libraries-on-android-befa49dce2db)
for more details. 
If `LibraryLoader.initialize()` was not called and normal library loading fails, `Context` will be tried to be retrieved in fall back way which may not always work.   

## Upgrading from 1.1.9
`int` parameter `loopNumber` has been added to `AnimationListener#onAnimationCompleted()`.

## Upgrading from 1.1.8
#### Proguard configuration not needed
Proguard configuration is now bundled with the library, you don't need to specify it yourself.

## Upgrading from 1.1.3
`src` XML attribute in `GifTextureView` has been renamed to `gifSource` to avoid possible conflicts with other libraries.

## Upgrading from 1.0.x
#### Proguard configuration update
Proguard configuration has changed to:
```
-keep public class pl.droidsonroids.gif.GifIOException{<init>(int);}
-keep class pl.droidsonroids.gif.GifInfoHandle{<init>(long,int,int,int);}
```

#### Drawable recycling behavior change
`GifDrawable` now uses `android.graphics.Bitmap` as frame buffer. Trying to access pixels (including drawing)
 of recycled `GifDrawable` will cause `IllegalStateException` like in `Bitmap`.

#### Minimum SDK version changed
Minimum API level is now 8 (Android 2.2).

#### Rendering moved to background thread
Rendering is performed in background thread running independently from main thread so animation is running
even if drawable is not drawn. However rendering is not running if drawable is not visible, see [#setVisible()](http://developer.android.com/reference/android/graphics/drawable/Drawable.html#setVisible(boolean, boolean)).
That method can be used to control drawable visibility in cases when it is not already handled by Android framework.

## References
This library uses code from [GIFLib](http://giflib.sourceforge.net/) 5.1.3 and [SKIA](https://code.google.com/p/skia/).

### Projects using android-gif-drawable
[ImageFactory](https://github.com/Doctoror/ImageFactory)

[NativeScript Plugin by Brad Martin](https://github.com/bradmartin/nativescript-gif) available on [NPM](https://www.npmjs.com/package/nativescript-gif)

[Sketch](https://github.com/xiaopansky/Sketch) Powerful and comprehensive image loader on Android, with support for GIF, gesture zooming, block display super large image.

Want to include your project here? [Fill an issue](https://github.com/koral--/android-gif-drawable/issues/new)

## License

MIT License<br>
See [LICENSE](LICENSE) file.
