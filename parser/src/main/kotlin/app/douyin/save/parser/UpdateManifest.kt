package app.douyin.save.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object UpdateSources {
    const val ASSET =
        "https://github.com/yinerx/douyin-downloader/releases/latest/download/latest.json"
    const val API =
        "https://api.github.com/repos/yinerx/douyin-downloader/releases/latest"
    const val RATE_LIMIT =
        "GitHub 限制了当前网络的访问次数，换个节点或稍后再试。"

    /** 空地址、附件地址，或旧的默认 API 地址，都走「先附件、再 API」。自定义地址不走这条。 */
    fun usesGitHub(url: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        return normalized.isEmpty() || normalized == ASSET || normalized == API
    }

    fun limitMessage(status: Int): String? = if (status == 403 || status == 429) RATE_LIMIT else null
}

data class RemoteUpdate(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
)

/**
 * 两种输入都能认：
 * - GitHub Release API（`/releases/latest`）：tag 是版本名，正文里写 `versionCode: 数字`，附件里有安装包
 * - 自己托管的 JSON：`versionCode`、`versionName`、`changelog`、`downloadUrl`（没有时仍认旧的 `apkUrl`）
 */
object UpdateManifests {
    private val json = Json { ignoreUnknownKeys = true }
    private val versionCodeLine = Regex("""versionCode:\s*(\d+)""")

    fun parse(body: String): RemoteUpdate {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: throw ParseException("更新信息不是 JSON。")
        val remote = when {
            root.containsKey("tag_name") || root.containsKey("assets") -> fromGitHub(root)
            root.containsKey("downloadUrl") || root.containsKey("apkUrl") || root.containsKey("versionCode") -> fromPlain(root)
            else -> {
                val message = root.text("message")
                if (message != null) throw ParseException("检查更新失败：$message")
                throw ParseException("更新信息格式无法识别。")
            }
        }
        if (!remote.downloadUrl.startsWith("https://")) {
            throw ParseException("更新包地址不是 https 链接。")
        }
        return remote
    }

    fun isNewerThan(remote: RemoteUpdate, versionCode: Int, versionName: String): Boolean {
        if (remote.versionCode > 0) return remote.versionCode > versionCode
        return compareNames(remote.versionName, versionName) > 0
    }

    internal fun compareNames(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        val count = maxOf(a.size, b.size)
        for (index in 0 until count) {
            val diff = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun fromGitHub(root: JsonObject): RemoteUpdate {
        val tag = root.text("tag_name") ?: throw ParseException("发布里没有版本号。")
        val notes = root.text("body").orEmpty()
        val code = versionCodeLine.find(notes)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val apk = (root["assets"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.text("name")?.endsWith(".apk", ignoreCase = true) == true }
            ?.text("browser_download_url")
            ?: throw ParseException("发布里没有 APK 附件。")
        val changelog = notes.lineSequence()
            .filterNot { versionCodeLine.containsMatchIn(it) }
            .joinToString("\n")
            .trim()
        return RemoteUpdate(
            versionCode = code,
            versionName = tag.removePrefix("v").removePrefix("V"),
            changelog = changelog.ifBlank { tag },
            downloadUrl = apk,
        )
    }

    private fun fromPlain(root: JsonObject): RemoteUpdate {
        val code = root.int("versionCode") ?: throw ParseException("更新信息缺少 versionCode。")
        val name = root.text("versionName") ?: throw ParseException("更新信息缺少 versionName。")
        val url = root.text("downloadUrl") ?: root.text("apkUrl")
            ?: throw ParseException("更新信息缺少 downloadUrl。")
        return RemoteUpdate(
            versionCode = code,
            versionName = name.removePrefix("v").removePrefix("V"),
            changelog = root.text("changelog").orEmpty().ifBlank { name },
            downloadUrl = url,
        )
    }

    private fun parts(value: String): List<Int> =
        value.removePrefix("v").removePrefix("V")
            .split('.', '-', '_')
            .map { piece -> piece.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
}
