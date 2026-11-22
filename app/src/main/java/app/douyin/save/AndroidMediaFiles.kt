package app.douyin.save

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

/** A system dialog backed by an [IntentSender]; MainActivity launches it for a result. */
internal class IntentSenderPrompt(val sender: IntentSender) : ConsentPrompt

/**
 * Deletes videos that [GallerySaver] wrote.
 *
 * - Android 8–9 (API 26–28): history holds `file://` URIs under DCIM/Douyin. Delete the file
 *   (needs WRITE_EXTERNAL_STORAGE, which the activity requests) and drop its MediaStore row.
 * - Android 10 (API 29): `content://` URIs. Files we inserted can be deleted directly; anything
 *   else throws [RecoverableSecurityException], whose dialog grants access to that one item.
 * - Android 11+ (API 30+): same direct delete for our own files; others go through
 *   [MediaStore.createDeleteRequest], one dialog for the whole batch.
 */
internal class AndroidMediaFiles(
    context: Context,
    override val sdkInt: Int = Build.VERSION.SDK_INT,
) : MediaFiles {
    private val resolver: ContentResolver = context.contentResolver

    override fun delete(uri: String): FileDelete {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return FileDelete.Failed("链接无效")
        return when (parsed.scheme) {
            ContentResolver.SCHEME_FILE -> deleteFile(parsed)
            ContentResolver.SCHEME_CONTENT -> deleteContent(parsed)
            else -> FileDelete.Failed("不认识的文件地址")
        }
    }

    override fun stillExists(uri: String): Boolean {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        return when (parsed.scheme) {
            ContentResolver.SCHEME_FILE -> parsed.path?.let { File(it).exists() } ?: false
            ContentResolver.SCHEME_CONTENT -> contentExists(parsed) != false
            else -> false
        }
    }

    override fun batchConsent(uris: List<String>): ConsentPrompt? {
        if (sdkInt < Build.VERSION_CODES.R || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val request = MediaStore.createDeleteRequest(resolver, uris.map(Uri::parse))
            IntentSenderPrompt(request.intentSender)
        } catch (error: RuntimeException) {
            Log.w(TAG, "createDeleteRequest failed", error)
            null
        }
    }

    private fun deleteFile(uri: Uri): FileDelete {
        val path = uri.path ?: return FileDelete.Failed("链接无效")
        val file = File(path)
        if (!file.exists()) {
            forgetMediaRow(path)
            return FileDelete.Missing
        }
        return try {
            if (file.delete()) {
                forgetMediaRow(path)
                FileDelete.Deleted
            } else {
                FileDelete.Failed("没有权限删除这个文件")
            }
        } catch (error: SecurityException) {
            FileDelete.Failed("没有权限删除这个文件")
        }
    }

    /** Best effort: stop the gallery from showing a row for a file that no longer exists. */
    @Suppress("DEPRECATION")
    private fun forgetMediaRow(path: String) {
        runCatching {
            resolver.delete(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DATA}=?",
                arrayOf(path),
            )
        }.onFailure { Log.w(TAG, "media row cleanup skipped", it) }
    }

    private fun deleteContent(uri: Uri): FileDelete {
        return try {
            val count = resolver.delete(uri, null, null)
            when {
                count > 0 -> FileDelete.Deleted
                // Zero rows: either gone, or hidden from us because we no longer own it.
                contentExists(uri) == true -> consentFor(uri, null)
                else -> FileDelete.Missing
            }
        } catch (error: SecurityException) {
            consentFor(uri, error)
        } catch (error: IllegalArgumentException) {
            // Some providers reject URIs that point at rows that no longer exist.
            if (contentExists(uri) == false) FileDelete.Missing else FileDelete.Failed("删除失败：${error.message}")
        } catch (error: UnsupportedOperationException) {
            FileDelete.Failed("删除失败：${error.message}")
        }
    }

    private fun consentFor(uri: Uri, error: SecurityException?): FileDelete {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && sdkInt >= Build.VERSION_CODES.R) {
            return FileDelete.NeedsConsent(null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is RecoverableSecurityException) {
            return FileDelete.NeedsConsent(IntentSenderPrompt(error.userAction.actionIntent.intentSender))
        }
        Log.w(TAG, "no permission to delete $uri", error)
        return FileDelete.Failed("没有权限删除这个视频")
    }

    /** true = still there, false = definitely gone, null = cannot tell. */
    private fun contentExists(uri: Uri): Boolean? = try {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: FileNotFoundException) {
        false
    } catch (_: SecurityException) {
        // It exists, we just may not read it.
        true
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: IllegalStateException) {
        null
    }

    companion object {
        private const val TAG = "DouyinSave"
    }
}
