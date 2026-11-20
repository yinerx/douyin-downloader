package app.douyin.save.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextParserTest {
    @Test
    fun extractsUrlFromShareText() {
        val text = "7.92 :9pm TLJ:/ u@F.Hi 04/03 爱情公寓｜第三季｜第257集  " +
            "https://v.douyin.com/J4IQiholWbo/ 复制此链接，打开Dou音搜索，直接观看视频！"
        assertEquals("https://v.douyin.com/J4IQiholWbo/", ShareTextParser.extractUrl(text))
    }

    @Test
    fun keepsUnderscoreInShortCode() {
        val text = "6.97 不上班  https://v.douyin.com/PkQJMi_Sb00/ 复制此链接，打开Dou音搜索，直接观看视频！"
        assertEquals("https://v.douyin.com/PkQJMi_Sb00/", ShareTextParser.extractUrl(text))
    }

    @Test
    fun acceptsShortLinkWithoutScheme() {
        assertEquals(
            "https://v.douyin.com/PkQJMi_Sb00/",
            ShareTextParser.extractUrl("复制打开抖音 v.douyin.com/PkQJMi_Sb00/ 看看"),
        )
    }

    @Test
    fun acceptsDirectVideoUrlAndModalId() {
        assertEquals(
            "https://www.douyin.com/video/7445285776618482979",
            ShareTextParser.extractUrl("看看 https://www.douyin.com/video/7445285776618482979"),
        )
        assertEquals(
            "https://www.douyin.com/discover?modal_id=7445285776618482979",
            ShareTextParser.extractUrl("https://www.douyin.com/discover?modal_id=7445285776618482979"),
        )
    }

    @Test
    fun prefersDouyinUrlWhenAnotherLinkComesFirst() {
        val text = "来源 https://example.com/a 作品 https://www.iesdouyin.com/share/video/7624728488368732900/"
        assertEquals(
            "https://www.iesdouyin.com/share/video/7624728488368732900/",
            ShareTextParser.extractUrl(text),
        )
    }

    @Test
    fun stripsTrailingChinesePunctuation() {
        assertEquals(
            "https://v.douyin.com/J4IQiholWbo/",
            ShareTextParser.extractUrl("https://v.douyin.com/J4IQiholWbo/复制此链接"),
        )
        assertEquals(
            "https://v.douyin.com/J4IQiholWbo/",
            ShareTextParser.extractUrl("（https://v.douyin.com/J4IQiholWbo/）"),
        )
    }

    @Test
    fun reportsEmptyText() {
        val message = expectParseError { ShareTextParser.extractUrl("   ") }
        assertTrue(message.contains("没有内容"))
    }

    @Test
    fun reportsMissingLink() {
        val message = expectParseError { ShareTextParser.extractUrl("复制打开抖音，但是没有地址") }
        assertTrue(message.contains("没有找到链接"))
    }

    @Test
    fun reportsNonDouyinLink() {
        val message = expectParseError { ShareTextParser.extractUrl("看这个 https://example.com/video") }
        assertTrue(message.contains("不是抖音"))
    }
}
