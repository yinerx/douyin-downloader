package app.douyin.save.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamesTest {
    @Test
    fun stripsIllegalCharactersAndKeepsId() {
        assertEquals(
            "爱情公寓 第三季_7624728488368732900.mp4",
            DownloadNames.fileName("爱情公寓/第三季", "7624728488368732900"),
        )
    }

    @Test
    fun usesIdWhenTitleIsBlank() {
        assertEquals("7624728488368732900.mp4", DownloadNames.fileName("   \n", "7624728488368732900"))
    }
}
