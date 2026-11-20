package app.douyin.save

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.douyin.save.parser.DownloadNames
import app.douyin.save.parser.ParseException
import app.douyin.save.parser.ResolvedVideo
import java.io.File

internal class GallerySaver(
    private val context: Context,
    private val downloader: VideoDownloader,
) {
    fun save(video: ResolvedVideo, source: SelectedVideo, onProgress: (Long, Long) -> Unit): String {
        val name = DownloadNames.fileName(video.title, video.awemeId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(name, source, onProgress)
        } else {
            saveLegacy(name, source, onProgress)
        }
    }

    private fun saveWithMediaStore(name: String, source: SelectedVideo, onProgress: (Long, Long) -> Unit): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Douyin")
            put(MediaStore.Video.Media.IS_PENDING, 1)
            val now = System.currentTimeMillis() / 1000
            put(MediaStore.Video.Media.DATE_ADDED, now)
            put(MediaStore.Video.Media.DATE_MODIFIED, now)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw ParseException("无法写入相册。")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                downloader.write(source.url, output, source.totalBytes, onProgress)
            } ?: throw ParseException("无法写入相册。")
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveLegacy(name: String, source: SelectedVideo, onProgress: (Long, Long) -> Unit): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Douyin")
        if (!dir.exists() && !dir.mkdirs()) {
            throw ParseException("无法创建 DCIM/Douyin 目录。")
        }
        val file = File(dir, name)
        try {
            file.outputStream().use { output ->
                downloader.write(source.url, output, source.totalBytes, onProgress)
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
            return Uri.fromFile(file).toString()
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
