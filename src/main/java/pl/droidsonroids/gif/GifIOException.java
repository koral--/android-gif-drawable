package pl.droidsonroids.gif;

import android.support.annotation.NonNull;

import java.io.IOException;

/**
 * Exception encapsulating {@link GifError}s.
 *
 * @author koral--
 */
public class GifIOException extends IOException {
    private static final long serialVersionUID = 13038402904505L;
    /**
     * Reason which caused an exception
     */
    @NonNull
    public final GifError reason;

    private GifIOException(@NonNull GifError reason) {
        super(reason.getFormattedDescription());
        this.reason = reason;
    }

    @SuppressWarnings("WeakerAccess") //invoked from native code
    GifIOException(int errorCode) {
        this(GifError.fromCode(errorCode));
    }

    static GifIOException fromCode(final int nativeErrorCode) {
        if (nativeErrorCode == GifError.NO_ERROR.errorCode)
            return null;
        return new GifIOException(nativeErrorCode);
    }
}
