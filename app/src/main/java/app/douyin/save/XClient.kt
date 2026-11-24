package app.douyin.save

import app.douyin.save.parser.ParseException
import app.douyin.save.parser.XLinks
import app.douyin.save.parser.XPost
import app.douyin.save.parser.XPostParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 公开帖子不登录。先请求 X 的嵌入接口，被限流或失败时再试 fxtwitter、vxtwitter。
 * 帖子已删除、受保护，或确实没有视频时不再换接口。
 */
internal class XClient(private val client: OkHttpClient) {
    fun fetch(pageUrl: String): XPost {
        val id = resolve(pageUrl)
        var rateLimited = false
        var fallback: Exception? = null
        for (endpoint in XLinks.endpoints(id)) {
            try {
                val (code, body) = get(endpoint)
                if (code == 429) {
                    rateLimited = true
                    continue
                }
                if (code >= 500) {
                    fallback = ParseException("X 暂时没有响应，请稍后再试。", definitive = false)
                    continue
                }
                return XPostParser.parse(body, code)
            } catch (error: ParseException) {
                if (error.definitive) throw error
                fallback = error
            } catch (error: IOException) {
                fallback = error
            }
        }
        if (rateLimited) throw ParseException("访问 X 太频繁，请稍后再试。")
        throw fallback ?: ParseException("连接 X 失败，请检查网络后重试。")
    }

    private fun resolve(pageUrl: String): String {
        XLinks.statusId(pageUrl)?.let { return it }
        if (!XLinks.isShortLink(pageUrl)) {
            throw ParseException("这不是一条 X 帖子链接。请打开具体的帖子再分享。")
        }
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", XLinks.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val id = XLinks.statusId(finalUrl)
            if (id != null) return id
            if (!response.isSuccessful) {
                throw ParseException("短链接打不开（HTTP ${response.code}）。请换一条分享链接。")
            }
            throw ParseException("短链接没有指向一条 X 帖子。")
        }
    }

    private fun get(url: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", XLinks.USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }
}
