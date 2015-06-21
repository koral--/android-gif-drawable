### 1.1.7
- fixed warning about attaching nameless thread on ART
- support annotations version updated to 22.1.1
- Android gradle plugin updated to 1.2.3
- fixed NPE when `GifTextureView` is constructed without attributes
- fixed background artifacts - (https://github.com/koral--/android-gif-drawable/issues/167)[#167]
- single drawable assigned to multiple views support added (`MultiCallback`)
- NDK version updated to r10e
- `GifDrawable#setLoopCount()` added
- fixed firing `AnimationListener#onAnimationCompleted()`
- gradle wrapper version updated to 2.4

### 1.1.8
- toolchain changed to clang
- `InputStream` source reading optimization
- fixed support for API level 8 - (https://github.com/koral--/android-gif-drawable/issues/173)[#173]
- fixed seeking in paused state - (https://github.com/koral--/android-gif-drawable/issues/180)[#180]

Also contains documentation updates, typofixes, and trivial code clean-ups.