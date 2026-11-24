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

    @Test
    fun namesXFilesByAuthorAndAddsIndexFromTheSecondVideo() {
        assertEquals(
            "SpaceX_2121715216597827756.mp4",
            DownloadNames.xFileName("SpaceX", "2121715216597827756", index = 1, videoCount = 1),
        )
        assertEquals(
            "Ultima Shadow_1577719286659006464.mp4",
            DownloadNames.xFileName("Ultima/Shadow", "1577719286659006464", index = 1, videoCount = 4),
        )
        assertEquals(
            "Ultima_1577719286659006464_2.mp4",
            DownloadNames.xFileName("Ultima", "1577719286659006464", index = 2, videoCount = 4),
        )
        assertEquals(
            "1577719286659006464_3.mp4",
            DownloadNames.xFileName("   ", "1577719286659006464", index = 3, videoCount = 4),
        )
    }
}
