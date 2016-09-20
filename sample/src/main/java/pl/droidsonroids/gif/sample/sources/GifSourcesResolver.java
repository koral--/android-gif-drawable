package pl.droidsonroids.gif.sample.sources;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import pl.droidsonroids.gif.GifDrawableBuilder;
import pl.droidsonroids.gif.sample.R;

class GifSourcesResolver {
    private final AssetManager mAssetManager;
    private final Resources mResources;
    private final ContentResolver mContentResolver;
    private final File mFileForUri;
    private final File mFile;
    private final String mFilePath;
    private final byte[] mByteArray;
    private final ByteBuffer mByteBuffer;

    GifSourcesResolver(Context context) {
        mResources = context.getResources();
        mContentResolver = context.getContentResolver();
        mAssetManager = mResources.getAssets();
        mFileForUri = getFileFromAssets(context, "Animated-Flag-Uruguay.gif");
        mFile = getFileFromAssets(context, "Animated-Flag-Virgin_Islands.gif");
        mFilePath = getFileFromAssets(context, "Animated-Flag-Estonia.gif").getPath();
        mByteArray = getBytesFromAssets(mAssetManager, "Animated-Flag-France.gif");
        byte[] gifBytes = getBytesFromAssets(mAssetManager, "Animated-Flag-Georgia.gif");
        mByteBuffer = ByteBuffer.allocateDirect(gifBytes.length);
        mByteBuffer.put(gifBytes);
    }

    void bindSource(final int position, final GifDrawableBuilder builder) throws IOException {
        switch (position) {
            case 0: //asset
                builder.from(mAssetManager, "Animated-Flag-Finland.gif");
                break;
            case 1: //resource
                builder.from(mResources, R.drawable.anim_flag_england);
                break;
            case 2: //byte[]
                builder.from(mByteArray);
                break;
            case 3: //FileDescriptor
                builder.from(mAssetManager.openFd("Animated-Flag-Greece.gif"));
                break;
            case 4: //file path
                builder.from(mFilePath);
                break;
            case 5: //File
                builder.from(mFile);
                break;
            case 6: //AssetFileDescriptor
                builder.from(mResources.openRawResourceFd(R.raw.anim_flag_hungary));
                break;
            case 7: //ByteBuffer
                builder.from(mByteBuffer);
                break;
            case 8: //Uri
                builder.from(mContentResolver, Uri.parse("file:///" + mFileForUri.getAbsolutePath()));
                break;
            case 9: //InputStream
                builder.from(mAssetManager.open("Animated-Flag-Delaware.gif", AssetManager.ACCESS_RANDOM));
                break;
            default:
                throw new IndexOutOfBoundsException("Invalid source index");
        }
    }

    private File getFileFromAssets(Context context, String filename) {
        try {
            File file = new File(context.getCacheDir(), filename);
            final AssetFileDescriptor assetFileDescriptor = context.getResources().getAssets().openFd(filename);
            FileInputStream input = assetFileDescriptor.createInputStream();
            byte[] buf = new byte[(int) assetFileDescriptor.getDeclaredLength()];
            int bytesRead = input.read(buf);
            input.close();
            if (bytesRead != buf.length) {
                throw new IOException("Asset read failed");
            }
            FileOutputStream output = new FileOutputStream(file);
            output.write(buf, 0, bytesRead);
            output.close();
            return file;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] getBytesFromAssets(AssetManager assetManager, String filename) {
        try {
            final AssetFileDescriptor assetFileDescriptor = assetManager.openFd(filename);
            FileInputStream input = assetFileDescriptor.createInputStream();
            byte[] buf = new byte[(int) assetFileDescriptor.getDeclaredLength()];
            final int readBytes = input.read(buf);
            input.close();
            if (readBytes != buf.length) {
                throw new IOException("Incorrect asset length");
            }
            return buf;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
