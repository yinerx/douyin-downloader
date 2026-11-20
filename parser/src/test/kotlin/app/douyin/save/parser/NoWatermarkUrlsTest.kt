package app.douyin.save.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoWatermarkUrlsTest {
    private val uri = "v0d00fg10000d787jq7og65p3g4j12r0"

    @Test
    fun buildsPlayUrlsAndDropsWatermarkEndpoint() {
        val watermarked =
            "https://aweme.snssdk.com/aweme/v1/playwm/?line=0&logo_name=aweme_diversion_search&ratio=720p&video_id=$uri"
        val urls = NoWatermarkUrls.candidates(uri, watermarked)
        assertTrue(urls.none { "playwm" in it })
        assertEquals(
            "https://aweme.snssdk.com/aweme/v1/play/?video_id=$uri&ratio=1080p&line=0",
            urls.first(),
        )
        assertTrue(urls.any { "ratio=720p" in it && "/aweme/v1/play/" in it })
        assertTrue(urls.any { "watermark=0" in it })
        assertTrue(urls.any { "logo_name=aweme_diversion_search" in it })
    }

    @Test
    fun rejectsMalformedUri() {
        val message = expectParseError { NoWatermarkUrls.candidates("https://evil.example/x") }
        assertTrue(message.contains("播放编号"))
    }
}
