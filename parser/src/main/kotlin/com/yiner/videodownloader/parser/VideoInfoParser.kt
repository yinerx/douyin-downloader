package com.yiner.videodownloader.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ResolvedVideo(
    val awemeId: String,
    val title: String,
    val author: String,
    val videoUri: String,
    val downloadUrls: List<String>,
)

/**
 * 从分享页的 _ROUTER_DATA，或其中某一个作品对象，取出无水印下载地址。
 * 不依赖固定的 loaderData 路径：只要对象上有 aweme_id 和 video.play_addr 就能认出来。
 */
object VideoInfoParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val videoUri = Regex("^[A-Za-z0-9]{8,}$")
    private val videoIdParam = Regex("""[?&]video_id=([A-Za-z0-9]{8,})""")

    fun parse(payload: String, expectedAwemeId: String? = null): ResolvedVideo {
        val text = payload.trim()
        if (text.isEmpty()) {
            throw ParseException("页面里没有视频数据。作品可能已删除、设为私密，或抖音改了页面结构。")
        }
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: Exception) {
            throw ParseException("视频数据不是合法的 JSON，页面结构可能已变更。")
        }
        val hits = mutableListOf<Hit>()
        collect(root, hits)
        if (hits.isEmpty()) {
            throw ParseException("页面里没有视频数据。作品可能已删除、设为私密，或抖音改了页面结构。")
        }
        val chosen = if (expectedAwemeId.isNullOrBlank()) {
            hits.firstOrNull { !it.videoUri.isNullOrBlank() } ?: hits.first()
        } else {
            hits.firstOrNull { it.awemeId == expectedAwemeId }
                ?: throw ParseException("页面返回的作品和链接不一致，请重试。")
        }
        val uri = chosen.videoUri
        if (uri.isNullOrBlank()) {
            if (chosen.imageCount > 0) {
                throw ParseException("这是图文作品，当前只支持下载视频。")
            }
            throw ParseException("没有找到播放地址。抖音可能改了字段，需要更新解析模块。")
        }
        return ResolvedVideo(
            awemeId = chosen.awemeId,
            title = chosen.title.ifBlank { "抖音视频" },
            author = chosen.author,
            videoUri = uri,
            downloadUrls = NoWatermarkUrls.candidates(uri, chosen.watermarkedUrl),
        )
    }

    private fun collect(element: JsonElement, hits: MutableList<Hit>) {
        when (element) {
            is JsonObject -> {
                consider(element)?.let(hits::add)
                element.values.forEach { collect(it, hits) }
            }
            is JsonArray -> element.forEach { collect(it, hits) }
            else -> Unit
        }
    }

    private fun consider(obj: JsonObject): Hit? {
        val awemeId = obj.text("aweme_id") ?: return null
        val play = (obj["video"] as? JsonObject)?.get("play_addr") as? JsonObject
        val urls = play?.urlList().orEmpty()
        val uri = play?.text("uri")?.takeIf { videoUri.matches(it) }
            ?: urls.firstNotNullOfOrNull(::videoIdOf)
        val author = (obj["author"] as? JsonObject)?.text("nickname").orEmpty()
        val images = (obj["images"] as? JsonArray)?.size ?: 0
        return Hit(
            awemeId = awemeId,
            title = obj.text("desc").orEmpty().trim(),
            author = author.trim(),
            videoUri = uri,
            watermarkedUrl = urls.firstOrNull { it.startsWith("https://") },
            imageCount = images,
        )
    }

    private fun videoIdOf(url: String): String? = videoIdParam.find(url)?.groupValues?.get(1)

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.urlList(): List<String> {
        val array = this["url_list"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }

    private data class Hit(
        val awemeId: String,
        val title: String,
        val author: String,
        val videoUri: String?,
        val watermarkedUrl: String?,
        val imageCount: Int,
    )
}
