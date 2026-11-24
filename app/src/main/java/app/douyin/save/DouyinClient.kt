package app.douyin.save

import app.douyin.save.parser.AwemeIds
import app.douyin.save.parser.DouyinRequest
import app.douyin.save.parser.ParseException
import app.douyin.save.parser.RouterJson
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream

/** Tracks the in-flight OkHttp call so 「取消」 can abort it and drop a half-written file. */
internal class HttpCalls {
    private val lock = Any()
    private var active: Call? = null

    fun <T> execute(
        permit: () -> Boolean,
        client: OkHttpClient,
        request: Request,
        block: (Response) -> T,
    ): T {
        if (!permit()) throw CancellationException("已取消")
        val call = client.newCall(request)
        synchronized(lock) {
            if (!permit()) {
                call.cancel()
                throw CancellationException("已取消")
            }
            active = call
        }
        try {
            return call.execute().use { response ->
                if (!permit()) throw CancellationException("已取消")
                block(response)
            }
        } catch (error: IOException) {
            if (call.isCanceled() || !permit()) throw CancellationException("已取消", error)
            throw error
        } finally {
            synchronized(lock) { if (active === call) active = null }
        }
    }

    fun cancel() {
        synchronized(lock) { active?.cancel() }
    }
}

internal class LinkResolver(
    private val client: OkHttpClient,
    private val http: HttpCalls,
) {
    fun resolve(url: String, permit: () -> Boolean = { true }): String {
        if (AwemeIds.fromUrl(url) != null) return url
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DouyinRequest.USER_AGENT)
            .build()
        return http.execute(permit, client, request) { response ->
            val finalUrl = response.request.url.toString()
            if (!response.isSuccessful && AwemeIds.fromUrl(finalUrl) == null) {
                throw ParseException("短链接打不开（HTTP ${response.code}）。请换一条分享链接。")
            }
            finalUrl
        }
    }
}

internal class HttpSharePage(
    private val client: OkHttpClient,
    private val http: HttpCalls,
) {
    fun fetch(awemeId: String, permit: () -> Boolean = { true }): String? {
        val request = Request.Builder()
            .url(DouyinRequest.iesSharePage(awemeId))
            .header("User-Agent", DouyinRequest.USER_AGENT)
            .header("Referer", DouyinRequest.REFERER)
            .build()
        return http.execute(permit, client, request) { response ->
            if (!response.isSuccessful) return@execute null
            val html = response.body?.string().orEmpty()
            RouterJson.fromHtml(html)
        }
    }
}

internal data class SelectedVideo(val url: String, val totalBytes: Long)

internal class VideoDownloader(
    private val client: OkHttpClient,
    private val http: HttpCalls,
) {
    fun select(
        urls: List<String>,
        userAgent: String = DouyinRequest.USER_AGENT,
        referer: String = DouyinRequest.REFERER,
        emptyMessage: String = "无水印地址无法下载。抖音 CDN 拒绝了请求，或播放接口已变更。",
        minBytes: Long = 16 * 1024,
        permit: () -> Boolean = { true },
    ): SelectedVideo {
        val probes = urls.mapNotNull { url ->
            try {
                probe(url, userAgent, referer, minBytes, permit)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        return probes.maxByOrNull { it.totalBytes } ?: throw ParseException(emptyMessage)
    }

    fun write(
        url: String,
        output: OutputStream,
        totalHint: Long,
        onProgress: (Long, Long) -> Unit,
        userAgent: String = DouyinRequest.USER_AGENT,
        referer: String = DouyinRequest.REFERER,
        minBytes: Long = 16 * 1024,
        permit: () -> Boolean = { true },
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", referer)
            .build()
        http.execute(permit, client, request) { response ->
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
                if (readTotal < minBytes) {
                    throw ParseException("下载到的文件太小，可能不是完整视频。")
                }
            }
        }
    }

    private fun probe(
        url: String,
        userAgent: String,
        referer: String,
        minBytes: Long,
        permit: () -> Boolean,
    ): SelectedVideo? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", referer)
            .header("Range", "bytes=0-31")
            .build()
        return http.execute(permit, client, request) { response ->
            if (response.code != 200 && response.code != 206) return@execute null
            val bytes = response.body?.bytes() ?: return@execute null
            if (!isFtyp(bytes, bytes.size)) return@execute null
            val total = totalBytes(response) ?: return@execute null
            if (total < minBytes) return@execute null
            SelectedVideo(url, total)
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
