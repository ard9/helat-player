package io.github.ard9.linguaplayer.ui

import android.content.Context
import io.github.ard9.linguaplayer.R
import io.github.ard9.linguaplayer.data.CardBackup
import io.github.ard9.linguaplayer.roleplay.MicrophoneException
import io.github.ard9.linguaplayer.subtitle.SubtitleParser
import io.github.ard9.linguaplayer.subtitle.mkv.RangeNotSupportedException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** An error whose message is already written for the user, in the app's language. */
class UserMessageException(message: String) : IOException(message)

/**
 * A short explanation of [error] for the user, in the app's language. Technical messages
 * (English, for the log) are never shown.
 */
fun Context.describe(error: Throwable): String = getString(
    when (error) {
        is UserMessageException -> return error.message.orEmpty()
        is CardBackup.InvalidBackupException -> when (error.reason) {
            CardBackup.Reason.NotABackup -> R.string.err_not_backup
            CardBackup.Reason.Damaged -> R.string.err_backup_damaged
            CardBackup.Reason.Newer -> R.string.err_backup_newer
            CardBackup.Reason.TooLarge -> R.string.err_backup_too_large
        }
        is SubtitleParser.TooLargeException -> R.string.err_subtitle_too_large
        is RangeNotSupportedException -> R.string.err_no_range
        is MicrophoneException -> R.string.err_microphone
        is UnknownHostException, is ConnectException, is SocketTimeoutException, is SSLException -> R.string.err_network
        is FileNotFoundException, is SecurityException -> R.string.err_no_access
        is IOException -> R.string.err_io
        else -> R.string.err_unexpected
    }
)
