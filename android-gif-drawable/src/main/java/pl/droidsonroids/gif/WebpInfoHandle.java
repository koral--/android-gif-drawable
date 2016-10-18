package pl.droidsonroids.gif;

import android.content.res.AssetFileDescriptor;

import java.io.FileDescriptor;
import java.io.IOException;

import pl.droidsonroids.gif.annotations.Beta;

@Beta
public final class WebpInfoHandle {
	static {
		LibraryLoader.loadLibrary(null);
	}

	private volatile long infoPtr;

	WebpInfoHandle(FileDescriptor fd) throws GifIOException {
		infoPtr = openFd(fd, 0);
	}

	public WebpInfoHandle(AssetFileDescriptor afd) throws IOException {
		try {
			infoPtr = openFd(afd.getFileDescriptor(), afd.getStartOffset());
		} finally {
			try {
				afd.close();
			} catch (IOException ignored) {
				//no-op
			}
		}
	}

	private static native long openFd(FileDescriptor fd, long offset) throws GifIOException;

	private static native void free(long infoPtr);

	private static native int getWidth(long infoPtr);

	private static native int getHeight(long infoPtr);

	private static native void glTexSubImage2D(long gifInfoPtr, int target, int level);

	public void recycle() {
		free(infoPtr);
		infoPtr = 0L;
	}

	public int getWidth() {
		return getWidth(infoPtr);
	}

	public int getHeight() {
		return getHeight(infoPtr);
	}

	public void glTexSubImage2D(int target, int level) {
		glTexSubImage2D(infoPtr, target, level);
	}
}
