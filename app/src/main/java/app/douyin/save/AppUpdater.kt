package app.douyin.save

import android.content.Context
import app.douyin.save.parser.ParseException
import app.douyin.save.parser.RemoteUpdate
import app.douyin.save.parser.UpdateManifests
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

internal const val UPDATE_UNREACHABLE =
    "读不到更新。仓库 yinerx/douyin-downloader 目前是私有的，手机没有登录 GitHub，Release 接口不会返回内容。把仓库改成公开，或把下面的更新地址改成一个谁都能打开的 JSON 链接。"

internal sealed class UpdateCheckResult {
    data class Available(val info: RemoteUpdate) : UpdateCheckResult()
    data class UpToDate(val versionName: String) : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

internal class AppUpdater(
    private val context: Context,
    private val client: OkHttpClient,
) {
    fun check(url: String): UpdateCheckResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "douyin-save")
            .header("Accept", "application/vnd.github+json, application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403 || response.code == 404) {
                return UpdateCheckResult.Failed(UPDATE_UNREACHABLE)
            }
            if (!response.isSuccessful) {
                return UpdateCheckResult.Failed("检查更新失败（HTTP ${response.code}）。")
            }
            val remote = try {
                UpdateManifests.parse(text)
            } catch (error: ParseException) {
                return UpdateCheckResult.Failed(error.message ?: "更新信息格式无法识别。")
            }
            return if (UpdateManifests.isNewerThan(remote, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                UpdateCheckResult.Available(remote)
            } else {
                UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }
        }
    }

    fun download(url: String, onProgress: (Float?) -> Unit): File {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "douyin-save")
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403 || response.code == 404) {
                throw ParseException(UPDATE_UNREACHABLE)
            }
            if (!response.isSuccessful) {
                throw ParseException("下载更新包失败（HTTP ${response.code}）。")
            }
            val body = response.body ?: throw ParseException("更新包是空的。")
            val type = response.header("Content-Type").orEmpty()
            if (type.contains("text/html")) {
                throw ParseException(UPDATE_UNREACHABLE)
            }
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "douyin-save-update.apk")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var checked = false
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (!checked) {
                            val zip = count >= 2 && buffer[0] == 'P'.code.toByte() && buffer[1] == 'K'.code.toByte()
                            if (!zip) {
                                file.delete()
                                throw ParseException(UPDATE_UNREACHABLE)
                            }
                            checked = true
                        }
                        output.write(buffer, 0, count)
                        readTotal += count
                        onProgress(if (total > 0) (readTotal.toFloat() / total).coerceIn(0f, 1f) else null)
                    }
                }
            }
            if (file.length() < 16 * 1024) {
                file.delete()
                throw ParseException("更新包太小，不是完整的 APK。")
            }
            return file
        }
    }
}
