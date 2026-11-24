package com.yiner.videodownloader.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoInfoParserTest {
    @Test
    fun parsesNestedRouterAndPicksTheRequestedVideo() {
        val payload = """
            {
              "loaderData": {
                "video_(id)/page": {
                  "videoInfoRes": {
                    "item_list": [
                      {
                        "aweme_id": "1111111111111111111",
                        "desc": "其他",
                        "author": {"nickname": "别人"},
                        "video": {
                          "play_addr": {
                            "uri": "v0200aaaaaaaa",
                            "url_list": ["https://aweme.snssdk.com/aweme/v1/playwm/?video_id=v0200aaaaaaaa&ratio=720p&line=0"]
                          }
                        }
                      },
                      {
                        "aweme_id": "7624728488368732900",
                        "desc": "爱情公寓｜第三季｜第257集",
                        "author": {"nickname": "韭韭不能平静"},
                        "video": {
                          "play_addr": {
                            "uri": "v0d00fg10000d787jq7og65p3g4j12r0",
                            "url_list": ["https://aweme.snssdk.com/aweme/v1/playwm/?line=0&ratio=720p&video_id=v0d00fg10000d787jq7og65p3g4j12r0"]
                          }
                        }
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        val video = VideoInfoParser.parse(payload, "7624728488368732900")
        assertEquals("7624728488368732900", video.awemeId)
        assertEquals("爱情公寓｜第三季｜第257集", video.title)
        assertEquals("韭韭不能平静", video.author)
        assertEquals("v0d00fg10000d787jq7og65p3g4j12r0", video.videoUri)
        assertTrue(video.downloadUrls.none { "playwm" in it })
        assertTrue(video.downloadUrls.first().contains("ratio=1080p"))
    }

    @Test
    fun readsNumericAwemeIdAndVideoIdFromUrl() {
        val payload = """
            {"aweme_id":7624728488368732900,"desc":"只有地址","author":{"nickname":"甲"},
             "video":{"play_addr":{"url_list":["https://aweme.snssdk.com/aweme/v1/playwm/?video_id=v0d00fg10000d787jq7og65p3g4j12r0&ratio=720p&line=0"]}}}
        """.trimIndent()
        val video = VideoInfoParser.parse(payload)
        assertEquals("7624728488368732900", video.awemeId)
        assertEquals("v0d00fg10000d787jq7og65p3g4j12r0", video.videoUri)
    }

    @Test
    fun emptyRouterExplainsTheFailure() {
        val message = expectParseError {
            VideoInfoParser.parse("""{"loaderData":{"video_(id)/page":{"videoInfoRes":null}}}""")
        }
        assertTrue(message.contains("没有视频数据"))
    }

    @Test
    fun imagePostIsRejected() {
        val message = expectParseError {
            VideoInfoParser.parse(
                """
                {"aweme_id":"1234567890123456789","desc":"图文","images":[{"url_list":["https://example.com/a.webp"]}],"video":{}}
                """.trimIndent(),
            )
        }
        assertTrue(message.contains("图文"))
    }

    @Test
    fun invalidJsonAndBlankPayloadHaveClearErrors() {
        assertTrue(expectParseError { VideoInfoParser.parse("  ") }.contains("没有视频数据"))
        assertTrue(expectParseError { VideoInfoParser.parse("{") }.contains("不是合法的 JSON"))
    }

    @Test
    fun mismatchedIdIsReported() {
        val message = expectParseError {
            VideoInfoParser.parse(
                """{"aweme_id":"1234567890123456789","video":{"play_addr":{"uri":"v0d00fg10000d787jq7og65p3g4j12r0"}}}""",
                expectedAwemeId = "9999999999999999999",
            )
        }
        assertTrue(message.contains("不一致"))
    }
}
