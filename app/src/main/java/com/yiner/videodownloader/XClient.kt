package com.yiner.videodownloader

import com.yiner.videodownloader.parser.ParseException
import com.yiner.videodownloader.parser.XLinks
import com.yiner.videodownloader.parser.XPost
import com.yiner.videodownloader.parser.XPostParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 公开帖子不登录。先请求 X 的嵌入接口，被限流或失败时再试 fxtwitter、vxtwitter。
 * 帖子已删除、受保护，或确实没有视频时不再换接口。
 */
internal class XClient(
    private val client: OkHttpClient,
    private val http: HttpCalls,
) {
    /**
     * 按顺序问每个接口。拿到帖子后就可以先下载；这条帖子的视频地址如果被拒绝，再 [Sources.next] 换下一个接口的地址。
     * 帖子已删除、没有视频、需要登录时，第一个接口就会直接说明原因，不再往下换。
     */
    fun open(pageUrl: String, permit: () -> Boolean = { true }): Sources {
        return Sources(resolve(pageUrl, permit), permit)
    }

    inner class Sources internal constructor(
        private val id: String,
        private val permit: () -> Boolean,
    ) {
        private val endpoints = XLinks.endpoints(id).iterator()
        private var sawPost = false
        private var rateLimited = false
        private var fallback: Exception? = null

        fun next(): XPost? {
            while (endpoints.hasNext()) {
                val endpoint = endpoints.next()
                try {
                    val (code, body) = get(endpoint, permit)
                    if (code == 429) {
                        rateLimited = true
                        continue
                    }
                    if (code >= 500) {
                        fallback = ParseException("X 暂时没有响应，请稍后再试。", definitive = false)
                        continue
                    }
                    val post = XPostParser.parse(body, code)
                    sawPost = true
                    return post
                } catch (error: ParseException) {
                    if (error.definitive && !sawPost) throw error
                    fallback = error
                } catch (error: IOException) {
                    fallback = error
                }
            }
            if (sawPost) return null
            if (rateLimited && fallback == null) throw ParseException("访问 X 太频繁，请稍后再试。")
            throw fallback ?: ParseException("连接 X 失败，请检查网络后重试。")
        }
    }

    private fun resolve(pageUrl: String, permit: () -> Boolean): String {
        XLinks.statusId(pageUrl)?.let { return it }
        if (!XLinks.isShortLink(pageUrl)) {
            throw ParseException("这不是一条 X 帖子链接。请打开具体的帖子再分享。")
        }
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", XLinks.USER_AGENT)
            .build()
        return http.execute(permit, client, request) { response ->
            val finalUrl = response.request.url.toString()
            val id = XLinks.statusId(finalUrl)
            if (id != null) return@execute id
            if (!response.isSuccessful) {
                throw ParseException("短链接打不开（HTTP ${response.code}）。请换一条分享链接。")
            }
            throw ParseException("短链接没有指向一条 X 帖子。")
        }
    }

    private fun get(url: String, permit: () -> Boolean): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", XLinks.USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return http.execute(permit, client, request) { response ->
            response.code to response.body?.string().orEmpty()
        }
    }
}
