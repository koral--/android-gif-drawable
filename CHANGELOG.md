### 1.1.16
- Saved state which is not instance of `GifViewSavedState` allowed by all the Gif*Views - [#303](https://github.com/koral--/android-gif-drawable/issues/303)
- `GifOptions` added introducing subsampling and opacity controlling in `GifDrawable`, `GifTexImage2D` and `GifDecoder`
- Fixed segmentation fault when decoding oversized frames - [#290](https://github.com/koral--/android-gif-drawable/pull/290)
- Native window redraw narrowed to dirty region only - [#287](https://github.com/koral--/android-gif-drawable/issues/287#issuecomment-215517405)  
- `View#invalidate()` support added to `MultiCallback` - [#260](https://github.com/koral--/android-gif-drawable/issues/260#issuecomment-201949696)
- `glTexSubImage2D()` support added to `GifTexImage2D` - [#288](https://github.com/koral--/android-gif-drawable/pull/288)
- Support library dependency version updated to 23.4.0
- Build tools version updated to 23.0.3
- Android gradle plugin updated to 2.1.0
- Gradle wrapper version updated to 2.13

### 1.1.15
- Fixed possible infinite surface binding
- Added beta OpenGL support - `GifTexImage2D`
- Added ability to specify a custom transformation to apply to the current Bitmap - [#259](https://github.com/koral--/android-gif-drawable/pull/259)
- Gradle wrapper version updated to 2.12
- Support library dependency version updated to 23.2.1
- remainder accounting in `GifDrawable#getCurrentPosition()` fixed

### 1.1.14
- Gradle wrapper version updated to 2.11
- Duplicated frame offset correction removed
- Subsampling added - [#239](https://github.com/koral--/android-gif-drawable/issues/239)
- Width, height and number of frames storage in recycled objects removed
- Native code cleanup
- Support library dependency version updated to 23.2.0

### 1.1.13
- Fixed regression (heap corruption if frame size is lower than canvas size) - [#250](https://github.com/koral--/android-gif-drawable/issues/250)

### 1.1.12
- Added `mipmap` resources support in XML attributes
- ReLinker code cleanup
- Gradle wrapper version updated to 2.10
- Fixed ANR on disposing `GifTextureView` - [#240](https://github.com/koral--/android-gif-drawable/issues/240)
- Added `GifDecoder` for access GIF frames without `Drawable` or `View` - [#206](https://github.com/koral--/android-gif-drawable/issues/206)
- Upstream changes from GIFLib 5.1.2 integrated
- Fixed `ZipFile` closing on API level < 19 - [#244](https://github.com/koral--/android-gif-drawable/issues/244)
- Invalid offsets handling fixed to prevent OOMEs - [#243](https://github.com/koral--/android-gif-drawable/issues/243) 

### 1.1.11
- `MultiCallback` now accepts `Drawable.Callback`s, not only `View`s
- `UnsatisfiedLinkError` worked around - [#51](https://github.com/koral--/android-gif-drawable/issues/51)
- Support library dependency version updated to 23.1.1
- Build tools version updated to 23.0.2
- Android gradle plugin updated to 1.5.0
- Gradle wrapper version updated to 2.9

### 1.1.10
- Gradle wrapper version updated to 2.7
- Build tools updated to 23.0.1
- Android gradle plugin updated to 1.3.1
- Support library dependency version updated to 23.0.1
- compileSdkVersion updated to 23
- density is now taken into consideration when constructing `GifDrawable` from Resources - [#181](https://github.com/koral--/android-gif-drawable/issues/181)
- animation restarting fixed - [#208](https://github.com/koral--/android-gif-drawable/issues/208), [#209](https://github.com/koral--/android-gif-drawable/issues/209)
- `GifTextureView` memory usage optimizations
- native code cleaning
- loopNumber parameter added to `AnimationListener#onAnimationCompleted()` - fixes [#204](https://github.com/koral--/android-gif-drawable/issues/204)
- rounded corners support added - [#202](https://github.com/koral--/android-gif-drawable/issues/202)

### 1.1.9
- Proguard configuration is now bundled with the library - [#193](https://github.com/koral--/android-gif-drawable/pull/193)
- Android gradle plugin updated to 1.3.0
- fixed segfault when frame is not confined to canvas - [#196](https://github.com/koral--/android-gif-drawable/issues/196), [#194](https://github.com/koral--/android-gif-drawable/issues/194)
- fixed relationship between drawable visibility and animation state - [#195](https://github.com/koral--/android-gif-drawable/issues/195)

### 1.1.8
- toolchain changed to clang
- `InputStream` source reading optimization
- fixed support for API level 8 - [#173](https://github.com/koral--/android-gif-drawable/issues/173)
- fixed seeking in paused state - [#180](https://github.com/koral--/android-gif-drawable/issues/180)
- added missing default frame duration - [#186](https://github.com/koral--/android-gif-drawable/issues/186)
- Gradle wrapper version updated to 2.5
- support annotations version updated to 22.2.1

### 1.1.7
- fixed warning about attaching nameless thread on ART
- support annotations version updated to 22.1.1
- Android gradle plugin updated to 1.2.3
- fixed NPE when `GifTextureView` is constructed without attributes
- fixed background artifacts - [#167](https://github.com/koral--/android-gif-drawable/issues/167)
- single drawable assigned to multiple views support added (`MultiCallback`)
- NDK version updated to r10e
- `GifDrawable#setLoopCount()` added
- fixed firing `AnimationListener#onAnimationCompleted()`
- Gradle wrapper version updated to 2.4

Updates also contain documentation updates, typofixes, and trivial code clean-ups.