package com.yiner.videodownloader.parser

import kotlin.math.round
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class XVariant(
    val url: String,
    val contentType: String?,
    val bitrate: Int?,
)

data class XVideo(
    val index: Int,
    val kind: String,
    val thumbnailUrl: String?,
    val durationMs: Long?,
    val bitrate: Int?,
    val urls: List<String>,
)

data class XPost(
    val id: String,
    val author: String,
    val text: String,
    val videos: List<XVideo>,
)

/** 从候选里挑出 MP4，按码率从高到低。跳过 m3u8。动图通常只有一条码率为 0 的 MP4。 */
object XVariants {
    private val resolution = Regex("""(\d{2,5})x(\d{2,5})""")

    fun ranked(variants: List<XVariant>): List<XVariant> = variants
        .filter { isMp4(it) }
        .sortedWith(
            compareByDescending<XVariant> { it.bitrate ?: 0 }
                .thenByDescending { pixels(it.url) },
        )

    fun best(variants: List<XVariant>): XVariant? = ranked(variants).firstOrNull()

    private fun isMp4(variant: XVariant): Boolean {
        val type = variant.contentType?.lowercase().orEmpty()
        val url = variant.url.lowercase()
        if ("mpegurl" in type || ".m3u8" in url) return false
        if (!variant.url.startsWith("https://")) return false
        return type == "video/mp4" || url.substringBefore('?').endsWith(".mp4")
    }

    private fun pixels(url: String): Int {
        val match = resolution.find(url) ?: return 0
        val width = match.groupValues[1].toIntOrNull() ?: return 0
        val height = match.groupValues[2].toIntOrNull() ?: return 0
        return width * height
    }
}

/**
 * 解析 X 公开嵌入接口（cdn.syndication.twimg.com）或 fxtwitter / vxtwitter 的 JSON。
 * 只收集这条帖子自己的视频和动图，不收集被引用帖子里的媒体。
 */
object XPostParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, httpStatus: Int = 200): XPost {
        if (httpStatus == 429) throw ParseException("访问 X 太频繁，请稍后再试。")
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed == "{}") {
            val missing = httpStatus == 404
            throw ParseException(
                if (missing) "这条帖子已删除或不存在。" else "X 没有返回帖子数据。",
                definitive = missing,
            )
        }
        if (!trimmed.startsWith("{")) {
            if (httpStatus == 404) throw ParseException("这条帖子已删除或不存在。")
            if (httpStatus == 401 || httpStatus == 403) {
                throw ParseException("X 的接口拒绝了访问，请换个网络或稍后再试。", definitive = false)
            }
            throw ParseException("X 返回了无法识别的内容。", definitive = false)
        }
        val root = try {
            json.parseToJsonElement(trimmed) as? JsonObject
                ?: throw ParseException("X 返回了无法识别的内容。", definitive = false)
        } catch (error: ParseException) {
            throw error
        } catch (_: Exception) {
            throw ParseException("X 返回了无法识别的内容。", definitive = false)
        }
        if ("tweet" in root || (root.int("code") != null && root.text("__typename") == null)) {
            return parseMirror(root)
        }
        if (root.text("__typename") == "TweetTombstone" || "tombstone" in root) {
            throw ParseException(tombstoneMessage(root))
        }
        if (root.text("__typename") == "Tweet" || "id_str" in root || "mediaDetails" in root) {
            return parseSyndication(root)
        }
        throw ParseException("X 返回了无法识别的内容。", definitive = false)
    }

    private fun parseSyndication(root: JsonObject): XPost {
        val id = root.text("id_str") ?: throw ParseException("X 没有返回帖子编号。", definitive = false)
        val user = root["user"] as? JsonObject
        val author = (user?.text("name") ?: user?.text("screen_name")).orEmpty()
        val media = root.objects("mediaDetails")
        val videos = videosFrom(media).ifEmpty {
            val top = root["video"] as? JsonObject
            if (top == null) emptyList() else listOfNotNull(videoFromLoose(top, "video"))
        }
        if (videos.isEmpty()) throw ParseException("这条帖子没有视频。")
        return XPost(id, unescape(author).trim(), unescape(root.text("text").orEmpty()).trim(), number(videos))
    }

    private fun parseMirror(root: JsonObject): XPost {
        val code = root.int("code") ?: 200
        val message = root.text("message").orEmpty()
        val tweet = root["tweet"] as? JsonObject
        if (tweet == null) {
            when {
                code == 401 || message.contains("PRIVATE", ignoreCase = true) ->
                    throw ParseException("这是受保护的帖子，无法下载。")
                message.contains("LOGIN", ignoreCase = true) ||
                    message.contains("SENSITIVE", ignoreCase = true) ||
                    message.contains("AGE_RESTRICTED", ignoreCase = true) ||
                    message.contains("NSFW", ignoreCase = true) ->
                    throw ParseException("这条内容需要登录 X 才能看，应用不支持登录。")
                code == 404 || message.contains("NOT_FOUND", ignoreCase = true) ->
                    throw ParseException("这条帖子已删除或不存在。")
                code == 429 || message.contains("RATE", ignoreCase = true) ->
                    throw ParseException("访问 X 太频繁，请稍后再试。")
                else -> throw ParseException("X 没有返回帖子数据。", definitive = false)
            }
        }
        val id = tweet.text("id") ?: throw ParseException("X 没有返回帖子编号。", definitive = false)
        val authorObject = tweet["author"] as? JsonObject
        val author = (authorObject?.text("name") ?: authorObject?.text("screen_name")).orEmpty()
        val media = tweet["media"] as? JsonObject
        val direct = media?.objects("videos").orEmpty()
        val videos = videosFrom(direct.ifEmpty { media?.objects("all").orEmpty() })
        if (videos.isEmpty()) throw ParseException("这条帖子没有视频。")
        return XPost(id, unescape(author).trim(), unescape(tweet.text("text").orEmpty()).trim(), number(videos))
    }

    private fun videosFrom(items: List<JsonObject>): List<XVideo> = items.mapNotNull { item ->
        val type = item.text("type")?.lowercase() ?: return@mapNotNull null
        if (type != "video" && type != "animated_gif" && type != "gif") return@mapNotNull null
        videoFromLoose(item, if (type == "video") "video" else "gif")
    }

    private fun videoFromLoose(item: JsonObject, kind: String): XVideo? {
        val info = item["video_info"] as? JsonObject
        val variants = info?.objects("variants").orEmpty()
            .ifEmpty { item.objects("variants") }
            .mapNotNull { it.toVariant() }
        val ranked = XVariants.ranked(variants)
        val urls = ranked.map { it.url }.ifEmpty {
            val direct = item.text("url") ?: item.text("src")
            if (direct != null && direct.startsWith("https://") && ".mp4" in direct.lowercase().substringBefore('?')) {
                listOf(direct)
            } else {
                emptyList()
            }
        }
        if (urls.isEmpty()) return null
        val duration = info?.long("duration_millis")
            ?: item.long("durationMs")
            ?: item.long("duration_millis")
            ?: item.double("duration")?.takeIf { it > 0 }?.let { round(it * 1000).toLong() }
        val thumb = item.text("media_url_https")
            ?: item.text("thumbnail_url")
            ?: item.text("poster")
        return XVideo(
            index = 0,
            kind = kind,
            thumbnailUrl = thumb,
            durationMs = duration?.takeIf { it > 0 },
            bitrate = ranked.firstOrNull()?.bitrate,
            urls = urls,
        )
    }

    private fun number(videos: List<XVideo>): List<XVideo> =
        videos.mapIndexed { index, video -> video.copy(index = index + 1) }

    private fun tombstoneMessage(root: JsonObject): String {
        val text = ((root["tombstone"] as? JsonObject)?.get("text") as? JsonObject)
            ?.text("text")
            .orEmpty()
            .lowercase()
        return when {
            "log in" in text || "logged in" in text || "age-restricted" in text || "sensitive" in text ->
                "这条内容需要登录 X 才能看，应用不支持登录。"
            "protected" in text || "private" in text || "limits who can view" in text ->
                "这是受保护的帖子，无法下载。"
            "deleted" in text || "suspended" in text || "no longer available" in text ->
                "这条帖子已删除或不存在。"
            else -> "这条帖子已删除、设为私密，或无法查看。"
        }
    }

    private fun unescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    private fun JsonObject.toVariant(): XVariant? {
        val url = text("url") ?: text("src") ?: return null
        val type = text("content_type") ?: text("contentType") ?: text("type")
        return XVariant(url, type, int("bitrate"))
    }

    private fun JsonObject.objects(key: String): List<JsonObject> {
        val array = this[key] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.text(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JsonObject.int(key: String): Int? = text(key)?.toDoubleOrNull()?.toInt()

    private fun JsonObject.long(key: String): Long? = text(key)?.toLongOrNull()

    private fun JsonObject.double(key: String): Double? = text(key)?.toDoubleOrNull()
}
