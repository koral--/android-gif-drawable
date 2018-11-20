package pl.droidsonroids.gif;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Encapsulation of decoding errors occurring in native code.
 * Three digit codes are equal to GIFLib error codes.
 *
 * @author koral--
 */
@SuppressWarnings("MagicNumber") //error code constants matching native ones
public enum GifError {
	/**
	 * Special value indicating lack of errors.
	 */
	NO_ERROR(0, "No error"),
	/**
	 * Failed to open given input.
	 */
	OPEN_FAILED(101, "Failed to open given input"),
	/**
	 * Failed to read from given input.
	 */
	READ_FAILED(102, "Failed to read from given input"),
	/**
	 * Data is not in GIF format.
	 */
	NOT_GIF_FILE(103, "Data is not in GIF format"),
	/**
	 * No screen descriptor detected.
	 */
	NO_SCRN_DSCR(104, "No screen descriptor detected"),
	/**
	 * No image descriptor detected.
	 */
	NO_IMAG_DSCR(105, "No image descriptor detected"),
	/**
	 * Neither global nor local color map found.
	 */
	NO_COLOR_MAP(106, "Neither global nor local color map found"),
	/**
	 * Wrong record type detected.
	 */
	WRONG_RECORD(107, "Wrong record type detected"),
	/**
	 * Number of pixels bigger than width * height.
	 */
	DATA_TOO_BIG(108, "Number of pixels bigger than width * height"),
	/**
	 * Failed to allocate required memory.
	 */
	NOT_ENOUGH_MEM(109, "Failed to allocate required memory"),
	/**
	 * Failed to close given input.
	 */
	CLOSE_FAILED(110, "Failed to close given input"),
	/**
	 * Given file was not opened for read.
	 */
	NOT_READABLE(111, "Given file was not opened for read"),
	/**
	 * Image is defective, decoding aborted.
	 */
	IMAGE_DEFECT(112, "Image is defective, decoding aborted"),
	/**
	 * Image EOF detected before image complete.
	 * EOF means GIF terminator, not the end of input source.
	 */
	EOF_TOO_SOON(113, "Image EOF detected before image complete"),
	/**
	 * No frames found, at least one frame required.
	 */
	NO_FRAMES(1000, "No frames found, at least one frame required"),
	/**
	 * Invalid screen size, dimensions must be positive.
	 */
	INVALID_SCR_DIMS(1001, "Invalid screen size, dimensions must be positive"),
	/**
	 * Invalid image size, dimensions must be positive.
	 *
	 * @deprecated This error is no longer thrown.
	 */
	@Deprecated
	INVALID_IMG_DIMS(1002, "Invalid image size, dimensions must be positive"),
	/**
	 * Image size exceeds screen size. Occurs only if input source changes after opening.
	 * Otherwise canvas is extended.
	 */
	IMG_NOT_CONFINED(1003, "Image size exceeds screen size"),
	/**
	 * Input source rewind has failed, animation is stopped.
	 */
	REWIND_FAILED(1004, "Input source rewind failed, animation stopped"),
	/**
	 * Invalid and/or indirect byte buffer specified.
	 */
	INVALID_BYTE_BUFFER(1005, "Invalid and/or indirect byte buffer specified"),
	/**
	 * Unknown error, should never appear
	 */
	UNKNOWN(-1, "Unknown error");
	/**
	 * Human readable description of the error.
	 */
	@NonNull
	public final String description;
	int errorCode;

	GifError(int code, @NonNull String description) {
		errorCode = code;
		this.description = description;
	}

	static GifError fromCode(int code) {
		for (GifError err : GifError.values())
			if (err.errorCode == code) {
				return err;
			}
		GifError unk = UNKNOWN;
		unk.errorCode = code;
		return unk;
	}

	/**
	 * @return error code
	 */
	public int getErrorCode() {
		return errorCode;
	}

	String getFormattedDescription() {
		return String.format(Locale.ENGLISH, "GifError %d: %s", errorCode, description);
	}
}