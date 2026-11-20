package app.douyin.save.parser

import java.net.URI

/** 从抖音分享文案里取出第一条抖音链接。短链、长链、前后夹杂文字都可以。 */
object ShareTextParser {
    // 只吃 URL 里合法的 ASCII，避免把紧跟在链接后面的中文一起吞进去。
    private val httpUrl = Regex(
        """https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""",
        RegexOption.IGNORE_CASE,
    )
    private val bareShort = Regex(
        """(?<![A-Za-z0-9./])v\.douyin\.com/[A-Za-z0-9_\-]+/?""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingJunk = setOf(
        ')', ']', '}', '>', '，', '。', '！', '？', '、', '】', '）', '」',
        ',', '.', ';', ':', '!', '?',
    )

    fun extractUrl(text: String): String {
        val raw = text.trim()
        if (raw.isEmpty()) {
            throw ParseException("没有内容。请粘贴抖音分享文案，或从抖音里点分享。")
        }
        val found = httpUrl.findAll(raw).map { clean(it.value) }.filter { it.isNotEmpty() }.toList()
        val douyin = found.firstOrNull { isDouyinHost(it) }
        if (douyin != null) return douyin
        val bare = bareShort.find(raw)?.value
        if (bare != null) return clean("https://$bare")
        if (found.isEmpty()) {
            throw ParseException("没有找到链接。请确认文案里包含 https://v.douyin.com/ 开头的地址。")
        }
        throw ParseException("找到了链接，但不是抖音地址。请粘贴抖音分享文案。")
    }

    internal fun isDouyinHost(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "v.douyin.com" ||
            host == "www.douyin.com" ||
            host == "douyin.com" ||
            host == "m.douyin.com" ||
            host == "www.iesdouyin.com" ||
            host == "iesdouyin.com"
    }

    private fun clean(url: String): String {
        var value = url.trim()
        while (value.isNotEmpty() && value.last() in trailingJunk) {
            if (value.last() == ')' && value.count { it == '(' } >= value.count { it == ')' }) break
            value = value.dropLast(1)
        }
        return value
    }
}
