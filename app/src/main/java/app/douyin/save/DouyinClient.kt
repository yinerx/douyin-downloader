package app.douyin.save

import app.douyin.save.parser.AwemeIds
import app.douyin.save.parser.DouyinRequest
import app.douyin.save.parser.ParseException
import app.douyin.save.parser.RouterJson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.OutputStream

internal class LinkResolver(private val client: OkHttpClient) {
    fun resolve(url: String): String {
        if (AwemeIds.fromUrl(url) != null) return url
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DouyinRequest.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            if (!response.isSuccessful && AwemeIds.fromUrl(finalUrl) == null) {
                throw ParseException("短链接打不开（HTTP ${response.code}）。请换一条分享链接。")
            }
            return finalUrl
        }
    }
}

internal class HttpSharePage(private val client: OkHttpClient) {
    fun fetch(awemeId: String): String? {
        val request = Request.Builder()
            .url(DouyinRequest.iesSharePage(awemeId))
            .header("User-Agent", DouyinRequest.USER_AGENT)
            .header("Referer", DouyinRequest.REFERER)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string().orEmpty()
            return RouterJson.fromHtml(html)
        }
    }
}

internal data class SelectedVideo(val url: String, val totalBytes: Long)

internal class VideoDownloader(private val client: OkHttpClient) {
    fun select(urls: List<String>): SelectedVideo {
        val probes = urls.mapNotNull { url ->
            runCatching { probe(url) }.getOrNull()
        }
        return probes.maxByOrNull { it.totalBytes }
            ?: throw ParseException("无水印地址无法下载。抖音 CDN 拒绝了请求，或播放接口已变更。")
    }

    fun write(url: String, output: OutputStream, totalHint: Long, onProgress: (Long, Long) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DouyinRequest.USER_AGENT)
            .header("Referer", DouyinRequest.REFERER)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 206) {
                throw ParseException("下载被拒绝（HTTP ${response.code}）。")
            }
            val body = response.body ?: throw ParseException("下载内容为空。")
            val total = body.contentLength().takeIf { it > 0 } ?: totalHint
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var readTotal = 0L
                var checked = false
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (!checked) {
                        if (!isFtyp(buffer, count)) {
                            throw ParseException("下载到的不是视频文件。播放接口可能已经变更。")
                        }
                        checked = true
                    }
                    output.write(buffer, 0, count)
                    readTotal += count
                    onProgress(readTotal, total)
                }
                if (readTotal < 16 * 1024) {
                    throw ParseException("下载到的文件太小，可能不是完整视频。")
                }
            }
        }
    }

    private fun probe(url: String): SelectedVideo? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DouyinRequest.USER_AGENT)
            .header("Referer", DouyinRequest.REFERER)
            .header("Range", "bytes=0-31")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 206) return null
            val bytes = response.body?.bytes() ?: return null
            if (!isFtyp(bytes, bytes.size)) return null
            val total = totalBytes(response) ?: return null
            if (total < 16 * 1024) return null
            return SelectedVideo(url, total)
        }
    }

    private fun totalBytes(response: Response): Long? {
        val range = response.header("Content-Range")
        val fromRange = range?.substringAfterLast('/')?.toLongOrNull()
        if (fromRange != null && fromRange > 0) return fromRange
        if (response.code == 200) {
            val length = response.header("Content-Length")?.toLongOrNull()
            if (length != null && length > 0) return length
        }
        return null
    }
}

internal fun isFtyp(bytes: ByteArray, length: Int): Boolean {
    val head = bytes.copyOfRange(0, minOf(length, bytes.size, 32))
    return "ftyp" in head.toString(Charsets.ISO_8859_1)
}
