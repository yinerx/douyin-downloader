package com.yiner.videodownloader.parser

/** 从分享页 HTML 里切出 window._ROUTER_DATA 的 JSON 对象。 */
object RouterJson {
    private const val MARKER = "window._ROUTER_DATA"

    fun fromHtml(html: String): String? {
        val markerAt = html.indexOf(MARKER)
        if (markerAt < 0) return null
        val start = html.indexOf('{', markerAt)
        if (start < 0) return null
        return sliceObject(html, start)
    }

    fun sliceObject(text: String, start: Int): String? {
        if (text.getOrNull(start) != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
