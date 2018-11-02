-keep class pl.droidsonroids.gif.sample.GifSelectorDrawable { *; }

-keepattributes Signature, LineNumberTable

#leakcanary
-keep class org.eclipse.mat.** { *; } 
-dontwarn com.squareup.haha.guava.** 
-dontwarn com.squareup.haha.perflib.** 
-dontwarn com.squareup.haha.trove.** 
-dontwarn com.squareup.leakcanary.** 
-keep class com.squareup.haha.** { *; }

#coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}