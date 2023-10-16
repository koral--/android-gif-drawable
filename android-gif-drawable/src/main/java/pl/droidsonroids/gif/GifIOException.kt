package pl.droidsonroids.gif

import java.io.IOException

/**
 * Exception encapsulating [GifError]s.
 *
 * @author koral--
 */
class GifIOException internal constructor(errorCode: Int, private val mErrnoMessage: String?) :
    IOException() {
    /**
     * Reason which caused an exception
     */
    val reason: GifError
    override val message: String
        get() = if (mErrnoMessage == null) {
            reason.formattedDescription
        } else "${reason.formattedDescription}: $mErrnoMessage"

    init {
        reason = GifError.fromCode(errorCode)
    }

    companion object {
        private const val serialVersionUID = 13038402904505L
        fun fromCode(nativeErrorCode: Int?): GifIOException? {
            return if (nativeErrorCode != null && nativeErrorCode == GifError.NO_ERROR.errorCode) {
                null
            } else GifIOException(nativeErrorCode!!, null)
        }
    }
}