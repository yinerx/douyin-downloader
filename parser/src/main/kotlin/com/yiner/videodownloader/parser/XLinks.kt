package com.yiner.videodownloader.parser

import java.net.URI
import kotlin.math.floor

/**
 * X 帖子链接、帖子编号，以及公开嵌入接口要用的 token。
 *
 * token 与嵌入页里的 JavaScript 一致：`(Number(id) / 1e15 * Math.PI).toString(36)`，
 * 再去掉 `0` 和小数点。小数部分按 V8 的 `DoubleToRadixString` 舍入，
 * 2026-11-24 与 Node 对一组真实帖子编号逐个对过。
 * 不带 token 时接口会返回空对象。
 */
object XLinks {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    const val REFERER = "https://x.com/"

    private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"
    private val statusId = Regex("""/status(?:es)?/(\d{1,25})(?!\d)""")
    private val hosts = setOf(
        "x.com",
        "www.x.com",
        "mobile.x.com",
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
        "t.co",
        "fxtwitter.com",
        "www.fxtwitter.com",
        "vxtwitter.com",
        "www.vxtwitter.com",
        "fixupx.com",
        "www.fixupx.com",
        "fixvx.com",
        "www.fixvx.com",
    )

    fun isHost(url: String): Boolean = host(url) in hosts

    fun isShortLink(url: String): Boolean = host(url) == "t.co"

    fun statusId(url: String): String? = statusId.find(url)?.groupValues?.get(1)

    fun token(id: String): String = toString36((id.toDouble() / 1e15) * Math.PI)
        .replace(Regex("(0+|\\.)"), "")

    fun syndicationUrl(id: String): String =
        "https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=en&token=${token(id)}"

    /** 先走 X 自己的公开嵌入接口，失败再试两个不需要登录的镜像。 */
    fun endpoints(id: String): List<String> = listOf(
        syndicationUrl(id),
        "https://api.fxtwitter.com/status/$id",
        "https://api.vxtwitter.com/status/$id",
    )

    private fun host(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()

    internal fun toString36(value: Double): String {
        if (value == 0.0) return "0"
        val negative = value < 0
        var number = if (negative) -value else value
        var integer = floor(number)
        var fraction = number - integer
        val buffer = CharArray(2200)
        val midpoint = buffer.size / 2
        var integerCursor = midpoint
        var fractionCursor = midpoint
        var delta = 0.5 * (Math.nextUp(number) - number)
        delta = maxOf(Math.nextUp(0.0), delta)
        if (fraction >= delta) {
            buffer[fractionCursor++] = '.'
            while (true) {
                fraction *= 36.0
                delta *= 36.0
                var digit = fraction.toInt()
                if (digit < 0) digit = 0
                if (digit > 35) digit = 35
                buffer[fractionCursor++] = DIGITS[digit]
                fraction -= digit
                if (fraction > 0.5 || (fraction == 0.5 && (digit and 1) != 0)) {
                    if (fraction + delta > 1) {
                        while (true) {
                            fractionCursor--
                            if (fractionCursor == midpoint) {
                                integer += 1
                                break
                            }
                            val c = buffer[fractionCursor]
                            val current = if (c > '9') c - 'a' + 10 else c - '0'
                            if (current + 1 < 36) {
                                buffer[fractionCursor++] = DIGITS[current + 1]
                                break
                            }
                        }
                        break
                    }
                }
                if (fraction < delta) break
            }
        }
        while (true) {
            val remainder = integer % 36.0
            buffer[--integerCursor] = DIGITS[remainder.toInt()]
            integer = (integer - remainder) / 36.0
            if (integer <= 0.0) break
        }
        if (negative) buffer[--integerCursor] = '-'
        return String(buffer, integerCursor, fractionCursor - integerCursor)
    }
}
