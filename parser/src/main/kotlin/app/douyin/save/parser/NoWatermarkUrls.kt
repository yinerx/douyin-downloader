package app.douyin.save.parser

/**
 * 由 video.play_addr.uri 拼出无水印播放地址。
 *
 * 水印地址的路径是 /aweme/v1/playwm/。把 playwm 换成 play 就是无水印流。
 * 不要把 playwm 放进候选：它的文件有时比无水印更大（多了角标和片尾），
 * 按体积挑选时会被误选。
 *
 * 2026-11-19 实测（同一 uri，Referer 为 https://m.douyin.com/）：
 * - ratio=1080p、720p、default 都会 302 到 video/mp4，文件头是 ftyp。
 * - 抽样成片没有抖音角标；playwm 成片有角标和「来抖音」片尾。
 */
object NoWatermarkUrls {
    private val uriPattern = Regex("^[A-Za-z0-9]{8,}$")

    fun candidates(videoUri: String, watermarkedUrl: String? = null): List<String> {
        if (!uriPattern.matches(videoUri)) {
            throw ParseException("播放编号格式异常，无法拼出无水印地址。")
        }
        val built = listOf(
            play(videoUri, "1080p"),
            play(videoUri, "720p"),
            "https://aweme.snssdk.com/aweme/v1/play/?video_id=$videoUri&ratio=default&line=0&watermark=0&improve_bitrate=1",
        )
        val fromPage = watermarkedUrl
            ?.replace("playwm", "play")
            ?.takeIf { it.startsWith("https://") && "playwm" !in it && it != watermarkedUrl }
        return (built + listOfNotNull(fromPage)).distinct()
    }

    private fun play(videoUri: String, ratio: String): String =
        "https://aweme.snssdk.com/aweme/v1/play/?video_id=$videoUri&ratio=$ratio&line=0"
}
