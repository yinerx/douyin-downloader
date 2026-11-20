package app.douyin.save.parser

/** 从跳转后的链接里取作品 id。短链 v.douyin.com 本身不含 id。 */
object AwemeIds {
    private val pathId = Regex("""/(?:share/)?(?:video|note|slides)/(\d{10,})""")
    private val queryId = Regex("""[?&](?:modal_id|aweme_id|item_ids?)=(\d{10,})""")
    private val noteOrSlides = Regex("""/(?:share/)?(?:note|slides)/\d""")

    fun fromUrl(url: String): String? {
        pathId.find(url)?.groupValues?.get(1)?.let { return it }
        return queryId.find(url)?.groupValues?.get(1)
    }

    fun isNoteOrSlides(url: String): Boolean = noteOrSlides.containsMatchIn(url)
}
