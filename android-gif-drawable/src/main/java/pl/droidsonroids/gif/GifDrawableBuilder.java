package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import pl.droidsonroids.gif.annotations.Beta;

import static pl.droidsonroids.gif.InputSource.AssetFileDescriptorSource;
import static pl.droidsonroids.gif.InputSource.AssetSource;
import static pl.droidsonroids.gif.InputSource.ByteArraySource;
import static pl.droidsonroids.gif.InputSource.DirectByteBufferSource;
import static pl.droidsonroids.gif.InputSource.FileDescriptorSource;
import static pl.droidsonroids.gif.InputSource.FileSource;
import static pl.droidsonroids.gif.InputSource.InputStreamSource;
import static pl.droidsonroids.gif.InputSource.ResourcesSource;
import static pl.droidsonroids.gif.InputSource.UriSource;

/**
 * Builder for {@link pl.droidsonroids.gif.GifDrawable} which can be used to construct new drawables
 * by reusing old ones.
 */
public class GifDrawableBuilder extends  GifDrawableInit<GifDrawableBuilder>{

	@Override
	protected GifDrawableBuilder self() {
		return this;
	}
}
