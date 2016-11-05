-keepattributes Signature, LineNumberTable

#leakcanary
-keep class org.eclipse.mat.** { *; } 
-dontwarn com.squareup.haha.guava.** 
-dontwarn com.squareup.haha.perflib.** 
-dontwarn com.squareup.haha.trove.** 
-dontwarn com.squareup.leakcanary.** 
-keep class com.squareup.haha.** { *; }
