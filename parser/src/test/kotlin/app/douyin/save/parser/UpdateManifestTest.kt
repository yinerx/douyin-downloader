package app.douyin.save.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun parsesGitHubReleaseAndHidesVersionCodeLine() {
        val body = """
            {
              "tag_name": "v1.2.0",
              "body": "修复白屏\n\nversionCode: 5\n",
              "assets": [
                {"name": "latest.json", "browser_download_url": "https://example.com/latest.json"},
                {"name": "douyin-save.apk", "browser_download_url": "https://github.com/yinerx/douyin-downloader/releases/download/v1.2.0/douyin-save.apk"}
              ]
            }
        """.trimIndent()
        val update = UpdateManifests.parse(body)
        assertEquals(5, update.versionCode)
        assertEquals("1.2.0", update.versionName)
        assertEquals("修复白屏", update.changelog)
        assertTrue(update.apkUrl.endsWith("douyin-save.apk"))
        assertTrue(UpdateManifests.isNewerThan(update, 2, "1.0.1"))
        assertFalse(UpdateManifests.isNewerThan(update, 5, "1.2.0"))
    }

    @Test
    fun parsesPlainManifest() {
        val update = UpdateManifests.parse(
            """
            {"versionCode":3,"versionName":"1.0.2","changelog":"说明","apkUrl":"https://example.com/a.apk"}
            """.trimIndent(),
        )
        assertEquals(3, update.versionCode)
        assertEquals("1.0.2", update.versionName)
        assertEquals("说明", update.changelog)
        assertTrue(UpdateManifests.isNewerThan(update, 2, "1.0.1"))
        assertFalse(UpdateManifests.isNewerThan(update, 3, "1.0.2"))
    }

    @Test
    fun fallsBackToVersionNameWhenCodeIsMissing() {
        val update = UpdateManifests.parse(
            """
            {"tag_name":"1.0.10","body":"没有编号","assets":[
              {"name":"app.apk","browser_download_url":"https://example.com/app.apk"}
            ]}
            """.trimIndent(),
        )
        assertEquals(0, update.versionCode)
        assertTrue(UpdateManifests.isNewerThan(update, 99, "1.0.2"))
        assertFalse(UpdateManifests.isNewerThan(update, 1, "1.0.10"))
        assertTrue(UpdateManifests.compareNames("1.0.10", "1.0.2") > 0)
    }

    @Test
    fun rejectsUnknownJsonAndMissingApk() {
        assertTrue(expectParseError { UpdateManifests.parse("""{"message":"Not Found"}""") }.contains("Not Found"))
        assertTrue(
            expectParseError {
                UpdateManifests.parse("""{"tag_name":"1.0.1","body":"versionCode: 2","assets":[]}""")
            }.contains("没有 APK"),
        )
        assertTrue(expectParseError { UpdateManifests.parse("not-json") }.contains("不是 JSON"))
    }
}
