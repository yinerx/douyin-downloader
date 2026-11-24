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
    fun parsesThePublishedLatestJson() {
        val update = UpdateManifests.parse(
            """
            {
              "versionCode": 5,
              "versionName": "1.1.1",
              "changelog": "- 下载进度、完成和失败改成对话框",
              "apkUrl": "https://github.com/yinerx/douyin-downloader/releases/download/1.1.1/douyin-save.apk"
            }
            """.trimIndent(),
        )
        assertEquals(5, update.versionCode)
        assertEquals("1.1.1", update.versionName)
        assertTrue(update.apkUrl.endsWith("/douyin-save.apk"))
        assertTrue(UpdateManifests.isNewerThan(update, 4, "1.1.0"))
        assertFalse(UpdateManifests.isNewerThan(update, 5, "1.1.1"))
    }

    @Test
    fun githubAssetUrlIsPrimaryAndApiQuotaHasAMessage() {
        assertTrue(UpdateSources.usesGitHub(""))
        assertTrue(UpdateSources.usesGitHub(UpdateSources.ASSET))
        assertTrue(UpdateSources.usesGitHub(UpdateSources.API + "/"))
        assertFalse(UpdateSources.usesGitHub("https://example.com/latest.json"))
        assertEquals(
            "GitHub 限制了当前网络的访问次数，换个节点或稍后再试。",
            UpdateSources.limitMessage(403),
        )
        assertEquals(UpdateSources.limitMessage(403), UpdateSources.limitMessage(429))
        assertEquals(null, UpdateSources.limitMessage(404))
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
