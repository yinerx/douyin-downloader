package com.yiner.videodownloader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yiner.videodownloader.parser.DouyinRequest
import com.yiner.videodownloader.parser.ParseException
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal interface RouterJsonSource {
    suspend fun load(pageUrl: String, awemeId: String): String
}

/**
 * 分享页的首屏 HTML 已经不再带 videoInfoRes。手机 WebView 执行页面自己的脚本后，
 * window._ROUTER_DATA 里才会出现 play_addr。这里只等数据出现，不解释字段。
 */
internal class WebViewRouterSource(private val webView: WebView) : RouterJsonSource {
    private val main = Handler(Looper.getMainLooper())

    override suspend fun load(pageUrl: String, awemeId: String): String = suspendCancellableCoroutine { cont ->
        val finished = AtomicBoolean(false)
        fun finishSuccess(json: String) {
            if (finished.compareAndSet(false, true) && cont.isActive) cont.resume(json)
        }
        fun finishError(error: Throwable) {
            if (finished.compareAndSet(false, true) && cont.isActive) cont.resumeWithException(error)
        }
        cont.invokeOnCancellation {
            main.post {
                runCatching { webView.stopLoading() }
            }
        }
        main.post {
            try {
                webView.stopLoading()
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.useWideViewPort = true
                webView.settings.loadWithOverviewMode = true
                webView.settings.userAgentString = DouyinRequest.USER_AGENT
                webView.settings.mediaPlaybackRequiresUserGesture = true
                webView.setInitialScale(100)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val scheme = request.url.scheme.orEmpty()
                        if (scheme != "http" && scheme != "https") return true
                        val host = request.url.host.orEmpty()
                        val allowed = host.endsWith("douyin.com") ||
                            host.endsWith("iesdouyin.com") ||
                            host.endsWith("douyinvod.com") ||
                            host.endsWith("bytecdn.com") ||
                            host.endsWith("snssdk.com")
                        if (!allowed) {
                            Log.w(TAG, "blocked ${request.url}")
                            return true
                        }
                        return false
                    }
                }
                val started = SystemClock.elapsedRealtime()
                val script = readScript(awemeId)
                fun poll() {
                    if (finished.get() || !cont.isActive) return
                    if (SystemClock.elapsedRealtime() - started > TIMEOUT_MS) {
                        finishError(
                            ParseException("解析超时。页面没有返回视频数据，作品可能已删除、设为私密，或抖音改了页面。"),
                        )
                        return
                    }
                    webView.evaluateJavascript(script) { raw ->
                        val json = decodeJsString(raw)
                        if (json.contains("\"play_addr\"")) {
                            finishSuccess(json)
                        } else {
                            main.postDelayed({ poll() }, 400)
                        }
                    }
                }
                webView.loadUrl(
                    pageUrl,
                    mapOf(
                        "User-Agent" to DouyinRequest.USER_AGENT,
                        "Referer" to DouyinRequest.REFERER,
                    ),
                )
                poll()
            } catch (error: Exception) {
                finishError(error)
            }
        }
    }

    private fun readScript(awemeId: String): String {
        require(awemeId.all { it.isDigit() }) { "aweme id" }
        return """
            (function(){
              try {
                if (!window._ROUTER_DATA) return "";
                var s = JSON.stringify(window._ROUTER_DATA);
                if (s.indexOf("\"$awemeId\"") < 0) return "";
                if (s.indexOf("\"play_addr\"") < 0) return "";
                return s;
              } catch (e) { return ""; }
            })()
        """.trimIndent()
    }

    companion object {
        private const val TIMEOUT_MS = 20_000L
        const val TAG = "DouyinSave"
    }
}

internal fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return try {
        JSONArray("[$raw]").getString(0)
    } catch (_: Exception) {
        ""
    }
}
