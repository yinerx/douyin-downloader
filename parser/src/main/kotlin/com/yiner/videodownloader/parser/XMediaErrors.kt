package com.yiner.videodownloader.parser

/**
 * 把下载 video.twimg.com 时拿到的状态码说成人话。
 * 调用方在主源的地址都失败后，还会换备用接口再试，所以这些失败都不是最终结论。
 */
object XMediaErrors {
    fun message(httpCodes: List<Int>, network: Boolean, notVideo: Boolean): String {
        val codes = httpCodes.distinct()
        return when {
            codes.any { it == 401 } -> "这条视频需要登录 X 才能看，应用不支持登录。"
            codes.isNotEmpty() && codes.all { it == 403 } ->
                "X 的视频服务器拒绝了下载。请换个网络，或稍后再试。"
            codes.isNotEmpty() && codes.all { it == 404 } -> "视频文件已经不存在了。"
            codes.any { it == 429 } -> "访问 X 太频繁，请稍后再试。"
            notVideo -> "下载到的不是视频文件。播放接口可能已经变更。"
            network || codes.isEmpty() -> "下载视频时连不上 X 的服务器，请检查网络后再试。"
            else -> "下载视频时出了问题（HTTP ${codes.joinToString("/")}），请稍后再试。"
        }
    }
}
