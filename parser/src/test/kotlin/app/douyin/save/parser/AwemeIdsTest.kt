package app.douyin.save.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwemeIdsTest {
    @Test
    fun readsIdFromShareAndVideoPaths() {
        assertEquals(
            "7624728488368732900",
            AwemeIds.fromUrl("https://www.iesdouyin.com/share/video/7624728488368732900/?region=CN"),
        )
        assertEquals(
            "7662968410262736166",
            AwemeIds.fromUrl("https://m.douyin.com/share/video/7662968410262736166/"),
        )
        assertEquals(
            "7445285776618482979",
            AwemeIds.fromUrl("https://www.douyin.com/video/7445285776618482979"),
        )
    }

    @Test
    fun readsModalId() {
        assertEquals(
            "7445285776618482979",
            AwemeIds.fromUrl("https://www.douyin.com/user/abc?modal_id=7445285776618482979"),
        )
    }

    @Test
    fun shortLinkHasNoId() {
        assertNull(AwemeIds.fromUrl("https://v.douyin.com/J4IQiholWbo/"))
        assertNull(AwemeIds.fromUrl("https://www.douyin.com/jingxuan?previous_page=web_video_404_link"))
    }

    @Test
    fun detectsNotesAndSlides() {
        assertTrue(AwemeIds.isNoteOrSlides("https://www.douyin.com/note/7445285776618482979"))
        assertTrue(AwemeIds.isNoteOrSlides("https://m.douyin.com/share/slides/7445285776618482979/"))
        assertFalse(AwemeIds.isNoteOrSlides("https://www.douyin.com/video/7445285776618482979"))
    }
}
