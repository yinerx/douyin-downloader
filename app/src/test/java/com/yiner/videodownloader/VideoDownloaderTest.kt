package com.yiner.videodownloader

import com.yiner.videodownloader.parser.ParseException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDownloaderTest {
    @Test
    fun dropsTheRefererAfterTheServerRejectsIt() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("Unauthorized. "))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 0-31/20000")
                .setBody(ftyp(32)),
        )
        server.start()
        try {
            val selected = downloader().select(
                urls = listOf(server.url("/video.mp4").toString()),
                referer = "https://www.douyin.com/",
                alternateReferers = listOf(null),
                statusAware = true,
                minBytes = 1024,
            )
            assertNull(selected.referer)
            assertEquals(20_000L, selected.totalBytes)
            val rejected = server.takeRequest()
            assertEquals("https://www.douyin.com/", rejected.getHeader("Referer"))
            val accepted = server.takeRequest()
            assertNull(accepted.getHeader("Referer"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun saysTheVideoServerRefusedInsteadOfADeadLink() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("Unauthorized. "))
        server.start()
        try {
            val error = runCatching {
                downloader().select(
                    urls = listOf(server.url("/video.mp4").toString()),
                    referer = "https://x.com/",
                    statusAware = true,
                    minBytes = 1024,
                )
            }.exceptionOrNull()
            assertTrue(error is ParseException)
            val parse = error as ParseException
            assertTrue(parse.message!!.contains("拒绝了下载"))
            assertTrue(!parse.message!!.contains("链接已失效"))
            assertTrue(!parse.definitive)
        } finally {
            server.shutdown()
        }
    }

    private fun downloader() = VideoDownloader(
        OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
        HttpCalls(),
    )

    private fun ftyp(size: Int): okio.Buffer {
        val bytes = ByteArray(size)
        val marker = "ftypisom".toByteArray(Charsets.ISO_8859_1)
        marker.copyInto(bytes, destinationOffset = 4)
        return okio.Buffer().write(bytes)
    }
}
