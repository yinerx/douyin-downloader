package app.douyin.save.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XLinksTest {
    @Test
    fun readsStatusIdFromCommonForms() {
        assertEquals(
            "2121715216597827756",
            XLinks.statusId("https://x.com/SpaceX/status/2121715216597827756?s=20&t=abc"),
        )
        assertEquals(
            "1577719286659006464",
            XLinks.statusId("https://twitter.com/UltimaShadowX/status/1577719286659006464"),
        )
        assertEquals(
            "1600649710662213632",
            XLinks.statusId("https://mobile.twitter.com/user/status/1600649710662213632/video/2"),
        )
        assertEquals(
            "2100630863679529243",
            XLinks.statusId("https://x.com/i/web/status/2100630863679529243"),
        )
        assertEquals(
            "20",
            XLinks.statusId("https://twitter.com/jack/statuses/20"),
        )
        assertNull(XLinks.statusId("https://t.co/YJzmGUZb2B"))
        assertTrue(XLinks.isShortLink("https://t.co/YJzmGUZb2B"))
        assertTrue(XLinks.isHost("https://fxtwitter.com/SpaceX/status/1"))
    }

    @Test
    fun tokenMatchesTheEmbedScript() {
        // 2026-11-24 用 Node 的 Number#toString(36) 生成。
        assertEquals("6dq1a2xwd93", XLinks.token("20"))
        assertEquals("bhi2ay3f28n", XLinks.token("1"))
        assertEquals("555kc5qp2g", XLinks.token("2121715216597827756"))
        assertEquals("3tojuiek1l", XLinks.token("1577719286659006464"))
        assertEquals("53bbr4ojtmw", XLinks.token("2100630863679529243"))
        assertTrue(XLinks.syndicationUrl("2121715216597827756").contains("token=555kc5qp2g"))
        assertEquals(3, XLinks.endpoints("20").size)
    }
}
