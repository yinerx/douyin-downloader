package app.douyin.save

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.douyin.save.parser.DouyinRequest
import app.douyin.save.parser.ParseException
import java.io.File

internal class GallerySaver(
    private val context: Context,
    private val downloader: VideoDownloader,
) {
    fun save(
        name: String,
        source: SelectedVideo,
        album: String,
        onProgress: (Long, Long) -> Unit,
        userAgent: String = DouyinRequest.USER_AGENT,
        referer: String = DouyinRequest.REFERER,
        minBytes: Long = 16 * 1024,
        permit: () -> Boolean = { true },
    ): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveWithMediaStore(name, source, album, onProgress, userAgent, referer, minBytes, permit)
    } else {
        saveLegacy(name, source, album, onProgress, userAgent, referer, minBytes, permit)
    }

    private fun saveWithMediaStore(
        name: String,
        source: SelectedVideo,
        album: String,
        onProgress: (Long, Long) -> Unit,
        userAgent: String,
        referer: String,
        minBytes: Long,
        permit: () -> Boolean,
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$album")
            put(MediaStore.Video.Media.IS_PENDING, 1)
            val now = System.currentTimeMillis() / 1000
            put(MediaStore.Video.Media.DATE_ADDED, now)
            put(MediaStore.Video.Media.DATE_MODIFIED, now)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw ParseException("无法写入相册。")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                downloader.write(source.url, output, source.totalBytes, onProgress, userAgent, referer, minBytes, permit)
            } ?: throw ParseException("无法写入相册。")
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveLegacy(
        name: String,
        source: SelectedVideo,
        album: String,
        onProgress: (Long, Long) -> Unit,
        userAgent: String,
        referer: String,
        minBytes: Long,
        permit: () -> Boolean,
    ): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), album)
        if (!dir.exists() && !dir.mkdirs()) {
            throw ParseException("无法创建 DCIM/$album 目录。")
        }
        val file = File(dir, name)
        try {
            file.outputStream().use { output ->
                downloader.write(source.url, output, source.totalBytes, onProgress, userAgent, referer, minBytes, permit)
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
            return Uri.fromFile(file).toString()
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
