android-gif-drawable
====================

## Overview
This is a `Drawable` subclass designed to support animated GIFs. It uses bundled GIFLib via JNI to render frames. Should be more efficient than `WebView` or `Movie` classes.<br>
Animation starts automatically and run only if `View` with attached `GifDrawable` is visible. `NETSCAPE 2.0` extension (loop counter) is respected as well.

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

If drawables declared by `android:src` and/or `android:background` are GIF files then they will be automatically recognized as `GifDrawable`s and animated.

###From Java code
`GifImageView` and `GifImageButton` have also hooks for setters implemented. So animated GIFs can be set by calling `setImageResource(int resId)` and `setBackgroundResource(int resId)`

`GifDrawable` can be constructed directly from various sources:

+ resource (file located in `res/drawable` and `res/raw` folders) eg. `new GifDrawable(getResources(), R.raw.mygif)`
+ asset (file located in `assets` folder) eg. `new GifDrawable(getAssets(), "myfile.gif")`
+ file (by path) eg.  `new GifDrawable(Environment.getExternalStorageDirectory().getPath()+ "/myfile.gif" ) )`
+ `InputStream` if it support marking
+ `FileDescriptor`
+ `AssetFileDescriptor`

###Advanced
`GifDrawable` provides several methods to control animation and retrieve GIF specific metadata. It is `Animatable` so you can use:

+ `stop()` (from any thread)
+ `start()` (from any thread)
+ `isRunning()`

GIF metadata can be obtained by:

+ `getLoopCount()`
+ `getNumberOfFrames()`
+ `getComment()`

If something went wrong reason can be investigated by calling `getError()`


###References
This library uses code from [GIFLIB](http://giflib.sourceforge.net/) 5.0.5 and [SKIA](https://code.google.com/p/skia/).

##License

MIT License<br>
See [LICENSE](LICENSE) file.
