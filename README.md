android-gif-drawable
====================
`View`s and `Drawable` for animated GIFs in Android.

## Overview
Bundled GIFLib via JNI is used to render frames. This way should be more efficient than `WebView` or `Movie` classes.<br>
Animation starts automatically and run only if `View` with attached `GifDrawable` is visible.

## Download
**[gif-drawable-1.0.0.jar](https://github.com/koral--/android-gif-drawable/releases/download/v1.0.0/gif-drawable-1.0.0.jar)**

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
    android:drawableLeft="@drawable/left_anim"
    android:drawableStart="@drawable/left_anim"
    android:background="@drawable/bg_anim"
    />
```

###From Java code
`GifImageView` and `GifImageButton` have also hooks for setters implemented. So animated GIFs can be set by calling `setImageResource(int resId)` and `setBackgroundResource(int resId)`

`GifDrawable` can be constructed directly from various sources:

+ resource (file located in `res/drawable` and `res/raw` folders) eg. `new GifDrawable(getResources(), R.drawable.mygif)`
+ asset (file located in `assets` folder) eg. `new GifDrawable(getAssets(), "myfile.gif")`
+ file (by path) eg.  `new GifDrawable(Environment.getExternalStorageDirectory().getPath()+ "/myfile.gif" ) )`
+ `InputStream` if it supports marking
+ `FileDescriptor`
+ `AssetFileDescriptor`

####Animation control
`GifDrawable` is an `Animatable` so you can use:

+ `stop()` from any thread
+ `start()` from any thread
+ `isRunning()`

####Retrieving GIF metadata

+ `getLoopCount()` `NETSCAPE 2.0` extension
+ `getNumberOfFrames()`
+ `getComment()`

####Advanced 
`recycle()` method is provided to speed up freeing memory (like in `android.graphics.Bitmap`).
If something went wrong, the reason can be investigated by calling `getError()`.


###References
This library uses code from [GIFLIB](http://giflib.sourceforge.net/) 5.0.5 and [SKIA](https://code.google.com/p/skia/).

##License

MIT License<br>
See [LICENSE](LICENSE) file.
