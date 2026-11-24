package app.douyin.save

import android.content.Context
import app.douyin.save.parser.ParseException
import app.douyin.save.parser.RemoteUpdate
import app.douyin.save.parser.UpdateManifests
import app.douyin.save.parser.UpdateSources
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

internal const val UPDATE_NOT_PUBLISHED =
    "还没有发布。推送一个和 versionName 相同的 tag 之后，这里才会出现新版本。"

internal const val UPDATE_NOT_AN_APK =
    "下载到的不是 APK。确认 Release 里的附件是公开的安装包。"

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
        if (!UpdateSources.usesGitHub(url)) return readManifest(url)
        val primary = readManifest(UpdateSources.ASSET)
        if (primary !is UpdateCheckResult.Failed) return primary
        val fallback = readManifest(UpdateSources.API)
        return if (fallback is UpdateCheckResult.Failed && primary.message == UpdateSources.RATE_LIMIT) {
            primary
        } else {
            fallback
        }
    }

    private fun readManifest(url: String): UpdateCheckResult {
        val response = try {
            fetch(url, "application/json, application/octet-stream;q=0.9, */*;q=0.8")
        } catch (_: IOException) {
            return UpdateCheckResult.Failed("没有网络，检查更新失败。")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (it.code == 404) return UpdateCheckResult.Failed(UPDATE_NOT_PUBLISHED)
            UpdateSources.limitMessage(it.code)?.let { message ->
                return UpdateCheckResult.Failed(message)
            }
            if (!it.isSuccessful) {
                return UpdateCheckResult.Failed("检查更新失败（HTTP ${it.code}）。")
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

    private fun fetch(url: String, accept: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .build()
        return client.newCall(request).execute()
    }

    fun download(url: String, onProgress: (Float?) -> Unit): File {
        fetch(url, "application/vnd.android.package-archive, application/octet-stream, */*").use { response ->
            if (response.code == 404) {
                throw ParseException(UPDATE_NOT_PUBLISHED)
            }
            UpdateSources.limitMessage(response.code)?.let { throw ParseException(it) }
            if (!response.isSuccessful) {
                throw ParseException("下载更新包失败（HTTP ${response.code}）。")
            }
            val body = response.body ?: throw ParseException("更新包是空的。")
            val type = response.header("Content-Type").orEmpty()
            if (type.contains("text/html")) {
                throw ParseException(UPDATE_NOT_AN_APK)
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
                                throw ParseException(UPDATE_NOT_AN_APK)
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

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
