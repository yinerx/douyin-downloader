package com.yiner.videodownloader.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XPostParserTest {
    @Test
    fun picksHighestBitrateMp4AndSkipsPlaylist() {
        val variants = listOf(
            XVariant("https://video.twimg.com/a/pl/video.m3u8?tag=12", "application/x-mpegURL", null),
            XVariant("https://video.twimg.com/a/vid/480x270/low.mp4?tag=12", "video/mp4", 256000),
            XVariant("https://video.twimg.com/a/vid/640x360/mid.mp4?tag=12", "video/mp4", 832000),
            XVariant("https://video.twimg.com/a/vid/1280x720/high.mp4?tag=12", "video/mp4", 2_176_000),
        )
        val best = XVariants.best(variants)
        assertEquals("https://video.twimg.com/a/vid/1280x720/high.mp4?tag=12", best?.url)
        assertEquals(2_176_000, best?.bitrate)
        assertEquals(3, XVariants.ranked(variants).size)
    }

    @Test
    fun keepsGifMp4WhenBitrateIsZero() {
        val best = XVariants.best(
            listOf(XVariant("https://video.twimg.com/tweet_video/HSbkx59W0AENuVq.mp4", "video/mp4", 0)),
        )
        assertEquals("https://video.twimg.com/tweet_video/HSbkx59W0AENuVq.mp4", best?.url)
    }

    @Test
    fun parsesSyndicationVideoLadderFromARealPost() {
        val post = XPostParser.parse(REAL_VIDEO)
        assertEquals("1577719286659006464", post.id)
        assertEquals("Ultima", post.author)
        assertEquals(1, post.videos.size)
        val video = post.videos.single()
        assertEquals(1, video.index)
        assertEquals("video", video.kind)
        assertEquals(4266L, video.durationMs)
        assertEquals(
            "https://pbs.twimg.com/ext_tw_video_thumb/1577719236209922048/pu/img/_3it9Ld7aLBKLEOF.jpg",
            video.thumbnailUrl,
        )
        assertEquals(
            "https://video.twimg.com/ext_tw_video/1577719236209922048/pu/vid/1280x720/-vn5a15qiFtZbg9q.mp4?tag=12",
            video.urls.first(),
        )
        assertEquals(2_176_000, video.bitrate)
        assertTrue(video.urls.none { ".m3u8" in it })
    }

    @Test
    fun parsesEveryVideoAndSkipsPhotos() {
        val post = XPostParser.parse(MULTI_AND_PHOTO)
        assertEquals(listOf(1, 2), post.videos.map { it.index })
        assertEquals(listOf("video", "gif"), post.videos.map { it.kind })
        assertEquals(2100L, post.videos[0].durationMs)
        assertNull(post.videos[1].durationMs)
        assertEquals("https://video.twimg.com/tweet_video/gif.mp4", post.videos[1].urls.single())
        assertEquals("https://pbs.twimg.com/tweet_video_thumb/gif.jpg", post.videos[1].thumbnailUrl)
    }

    @Test
    fun parsesAnimatedGifFromSyndication() {
        val post = XPostParser.parse(GIF)
        val video = post.videos.single()
        assertEquals("gif", video.kind)
        assertEquals("Chrome for Developers", post.author)
        assertEquals("https://video.twimg.com/tweet_video/HSbkx59W0AENuVq.mp4", video.urls.single())
        assertNull(video.durationMs)
    }

    @Test
    fun parsesMirrorWithDurationInSeconds() {
        val post = XPostParser.parse(MIRROR)
        assertEquals("2121715216597827756", post.id)
        assertEquals("SpaceX", post.author)
        assertEquals(92_720L, post.videos.single().durationMs)
        assertEquals(
            "https://video.twimg.com/amplify_video/high.mp4",
            post.videos.single().urls.first(),
        )
    }

    @Test
    fun reportsPostWithoutVideo() {
        val message = expectParseError {
            XPostParser.parse(
                """{"__typename":"Tweet","id_str":"20","text":"just setting up my twttr","user":{"name":"jack"}}""",
            )
        }
        assertTrue(message.contains("没有视频"))
    }

    @Test
    fun reportsDeletedAndProtectedPosts() {
        val deleted = expectParseError {
            XPostParser.parse(
                """{"__typename":"TweetTombstone","tombstone":{"text":{"text":"This Post was deleted by the Post author. Learn more"}}}""",
            )
        }
        assertTrue(deleted.contains("已删除"))
        val protectedPost = expectParseError {
            XPostParser.parse("""{"code":401,"message":"PRIVATE_TWEET","tweet":null}""")
        }
        assertTrue(protectedPost.contains("受保护"))
        val missing = expectParseError {
            XPostParser.parse("""{"code":404,"message":"NOT_FOUND","tweet":null}""")
        }
        assertTrue(missing.contains("已删除"))
        val emptyTombstone = expectParseError {
            XPostParser.parse("""{"__typename":"TweetTombstone","tombstone":{}}""")
        }
        assertTrue(emptyTombstone.contains("私密") || emptyTombstone.contains("已删除"))
    }

    @Test
    fun reportsRateLimitFromStatusOrBody() {
        val fromStatus = expectParseError { XPostParser.parse("{}", httpStatus = 429) }
        assertTrue(fromStatus.contains("太频繁"))
        val fromBody = expectParseError {
            XPostParser.parse("""{"code":429,"message":"RATE_LIMIT","tweet":null}""")
        }
        assertTrue(fromBody.contains("太频繁"))
    }
}

private const val REAL_VIDEO = """
{
  "__typename": "Tweet",
  "id_str": "1577719286659006464",
  "text": "Test https://t.co/Y3KEZD7Dad",
  "user": {"name": "Ultima", "screen_name": "UltimaShadowX"},
  "mediaDetails": [
    {
      "type": "video",
      "media_url_https": "https://pbs.twimg.com/ext_tw_video_thumb/1577719236209922048/pu/img/_3it9Ld7aLBKLEOF.jpg",
      "video_info": {
        "duration_millis": 4266,
        "variants": [
          {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/ext_tw_video/1577719236209922048/pu/pl/pdjnI7aX8FPr2U7I.m3u8?tag=12"},
          {"bitrate": 256000, "content_type": "video/mp4", "url": "https://video.twimg.com/ext_tw_video/1577719236209922048/pu/vid/480x270/yfq7UU_fWpZREmGM.mp4?tag=12"},
          {"bitrate": 832000, "content_type": "video/mp4", "url": "https://video.twimg.com/ext_tw_video/1577719236209922048/pu/vid/640x360/QWIQHVYmWE767xhu.mp4?tag=12"},
          {"bitrate": 2176000, "content_type": "video/mp4", "url": "https://video.twimg.com/ext_tw_video/1577719236209922048/pu/vid/1280x720/-vn5a15qiFtZbg9q.mp4?tag=12"}
        ]
      }
    }
  ],
  "video": {
    "variants": [
      {"type": "video/mp4", "src": "https://video.twimg.com/should-not-win.mp4"}
    ]
  }
}
"""

private const val MULTI_AND_PHOTO = """
{
  "__typename": "Tweet",
  "id_str": "100",
  "text": "photo and clips",
  "user": {"name": "甲"},
  "mediaDetails": [
    {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/photo.jpg"},
    {
      "type": "video",
      "media_url_https": "https://pbs.twimg.com/ext_tw_video_thumb/second.jpg",
      "video_info": {
        "duration_millis": 2100,
        "variants": [
          {"bitrate": 832000, "content_type": "video/mp4", "url": "https://video.twimg.com/ext_tw_video/low.mp4"},
          {"bitrate": 2176000, "content_type": "video/mp4", "url": "https://video.twimg.com/ext_tw_video/high.mp4"}
        ]
      }
    },
    {
      "type": "animated_gif",
      "media_url_https": "https://pbs.twimg.com/tweet_video_thumb/gif.jpg",
      "video_info": {
        "variants": [
          {"bitrate": 0, "content_type": "video/mp4", "url": "https://video.twimg.com/tweet_video/gif.mp4"}
        ]
      }
    }
  ]
}
"""

private const val GIF = """
{
  "__typename": "Tweet",
  "id_str": "2100630863679529243",
  "text": "Modern Web Guidance",
  "user": {"name": "Chrome for Developers", "screen_name": "ChromiumDev"},
  "mediaDetails": [
    {
      "type": "animated_gif",
      "media_url_https": "https://pbs.twimg.com/tweet_video_thumb/HSbkx59W0AENuVq.jpg",
      "video_info": {
        "variants": [
          {"bitrate": 0, "content_type": "video/mp4", "url": "https://video.twimg.com/tweet_video/HSbkx59W0AENuVq.mp4"}
        ]
      }
    }
  ]
}
"""

private const val MIRROR = """
{
  "code": 200,
  "message": "OK",
  "tweet": {
    "id": "2121715216597827756",
    "text": "Mechazilla catches Starship!",
    "author": {"name": "SpaceX", "screen_name": "SpaceX"},
    "media": {
      "videos": [
        {
          "type": "video",
          "url": "https://video.twimg.com/amplify_video/high.mp4?tag=29",
          "thumbnail_url": "https://pbs.twimg.com/amplify_video_thumb/poster.jpg",
          "duration": 92.72,
          "variants": [
            {"url": "https://video.twimg.com/amplify_video/video.m3u8", "bitrate": 0, "content_type": "application/x-mpegURL"},
            {"url": "https://video.twimg.com/amplify_video/low.mp4", "bitrate": 256000, "content_type": "video/mp4"},
            {"url": "https://video.twimg.com/amplify_video/high.mp4", "bitrate": 25128000, "content_type": "video/mp4"}
          ]
        }
      ]
    }
  }
}
"""
