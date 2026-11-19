package app.douyin.save.parser

/**
 * 2026-11-19 在真实分享链路上核对过的请求参数。
 * 分享页必须用手机 UA，否则会落到没有视频数据的桌面壳。
 */
object DouyinRequest {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; 2201123C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36"

    const val REFERER = "https://m.douyin.com/"

    fun mobileSharePage(awemeId: String): String = "https://m.douyin.com/share/video/$awemeId/"

    /** 旧的服务端渲染页。2026-11 起 videoInfoRes 为空，仍先试一次，恢复后不用改 App。 */
    fun iesSharePage(awemeId: String): String = "https://www.iesdouyin.com/share/video/$awemeId/"
}
