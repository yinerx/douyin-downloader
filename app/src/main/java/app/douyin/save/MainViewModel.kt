package app.douyin.save

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.douyin.save.parser.AwemeIds
import app.douyin.save.parser.DouyinRequest
import app.douyin.save.parser.ParseException
import app.douyin.save.parser.RemoteUpdate
import app.douyin.save.parser.ShareTextParser
import app.douyin.save.parser.VideoInfoParser
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal enum class Phase { Idle, Working, Success, Error }

internal data class ClipboardOffer(val rawText: String, val url: String)

internal data class UiState(
    val input: String = "",
    val clipboard: ClipboardOffer? = null,
    val phase: Phase = Phase.Idle,
    val status: String = "",
    val progress: Float? = null,
    val history: List<HistoryEntry> = emptyList(),
    val updateUrl: String = "",
    val checkOnLaunch: Boolean = true,
    val updateStatus: String = "",
    val updateChecking: Boolean = false,
    val updateOffer: RemoteUpdate? = null,
    val updateDownloading: Boolean = false,
    val updateProgress: Float? = null,
    val historyBusy: Boolean = false,
    val historyNotice: String? = null,
)

internal class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val historyStore = HistoryStore(app)
    private val prefs = UpdatePrefs(app)
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val downloadClient = client.newBuilder()
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
    private val linkResolver = LinkResolver(client)
    private val sharePage = HttpSharePage(client)
    private val downloader = VideoDownloader(downloadClient)
    private val gallery = GallerySaver(app, downloader)
    private val updater = AppUpdater(app, client)
    private val _installRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val installRequests: SharedFlow<String> = _installRequests.asSharedFlow()
    private val mediaFiles = AndroidMediaFiles(app)
    private val _consentRequests = MutableSharedFlow<ConsentPrompt>(extraBufferCapacity = 1)

    /** System dialogs the activity must show before gallery files we do not own can be deleted. */
    val consentRequests: SharedFlow<ConsentPrompt> = _consentRequests.asSharedFlow()
    private var deleteSession: DeleteSession? = null

    var routerSource: RouterJsonSource? = null

    private val _state = MutableStateFlow(
        UiState(
            history = historyStore.load(),
            updateUrl = prefs.updateUrl(),
            checkOnLaunch = prefs.checkOnLaunch(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private var ignoredUrl: String? = null

    fun updateInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun offerClipboard(text: String) {
        if (_state.value.phase == Phase.Working) return
        val url = runCatching { ShareTextParser.extractUrl(text) }.getOrNull() ?: return
        if (url == ignoredUrl) return
        _state.update { it.copy(clipboard = ClipboardOffer(text, url)) }
    }

    fun dismissClipboard() {
        ignoredUrl = _state.value.clipboard?.url
        _state.update { it.copy(clipboard = null) }
    }

    fun downloadClipboard() {
        val offer = _state.value.clipboard ?: return
        start(offer.rawText)
    }

    fun downloadInput() {
        start(_state.value.input)
    }

    fun start(text: String) {
        job?.cancel()
        val url = runCatching { ShareTextParser.extractUrl(text) }.getOrNull()
        ignoredUrl = url
        _state.update {
            it.copy(
                input = text,
                clipboard = null,
                phase = Phase.Working,
                status = "正在识别链接…",
                progress = null,
            )
        }
        job = viewModelScope.launch {
            try {
                download(text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isActive) throw error
                val message = userMessage(error)
                Log.w(TAG, message, error)
                record(success = false, awemeId = "", title = text.take(40), author = "", message = message, uri = null)
                _state.update {
                    it.copy(phase = Phase.Error, status = message, progress = null, history = historyStore.load())
                }
            }
        }
    }

    private suspend fun download(text: String) {
        val url = ShareTextParser.extractUrl(text)
        setWorking("正在解析短链接…")
        val finalUrl = withContext(Dispatchers.IO) { linkResolver.resolve(url) }
        Log.i(TAG, "resolved $finalUrl")
        if (finalUrl.contains("jingxuan") || finalUrl.contains("web_video_404")) {
            throw ParseException("这个链接打不开，作品可能已删除或设为私密。")
        }
        if (AwemeIds.isNoteOrSlides(url) || AwemeIds.isNoteOrSlides(finalUrl)) {
            throw ParseException("这是图文或合集链接，当前只支持下载视频。")
        }
        val awemeId = AwemeIds.fromUrl(finalUrl) ?: AwemeIds.fromUrl(url)
            ?: throw ParseException("无法从链接中解析出作品编号。请确认是抖音视频分享链接。")

        setWorking("正在获取无水印地址…")
        val httpPayload = withContext(Dispatchers.IO) {
            runCatching { sharePage.fetch(awemeId) }.getOrNull()
        }
        val fromPage = httpPayload?.let { runCatching { VideoInfoParser.parse(it, awemeId) }.getOrNull() }
        val video = if (fromPage != null) {
            Log.i(TAG, "source=ssr awemeId=$awemeId")
            fromPage
        } else {
            setWorking("正在打开分享页…")
            val source = routerSource ?: throw ParseException("界面还没准备好，请重试。")
            val pageJson = source.load(DouyinRequest.mobileSharePage(awemeId), awemeId)
            Log.i(TAG, "source=webview awemeId=$awemeId bytes=${pageJson.length}")
            VideoInfoParser.parse(pageJson, awemeId)
        }

        setWorking("正在选择清晰度…")
        val chosen = withContext(Dispatchers.IO) { downloader.select(video.downloadUrls) }
        Log.i(TAG, "uri=${video.videoUri} bytes=${chosen.totalBytes} play=${chosen.url.substringBefore('?')}")

        var lastTick = 0L
        val saved = withContext(Dispatchers.IO) {
            gallery.save(video, chosen) { read, total ->
                val now = SystemClock.elapsedRealtime()
                val done = total > 0 && read >= total
                if (!done && now - lastTick < 200) return@save
                lastTick = now
                val fraction = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else null
                val label = if (total > 0) "正在下载 ${(read * 100 / total).toInt()}%" else "正在下载…"
                _state.update { it.copy(phase = Phase.Working, status = label, progress = fraction) }
            }
        }
        record(
            success = true,
            awemeId = video.awemeId,
            title = video.title,
            author = video.author,
            message = "已保存到相册",
            uri = saved,
        )
        _state.update {
            it.copy(
                phase = Phase.Success,
                status = "已保存到相册（DCIM/Douyin）",
                progress = 1f,
                history = historyStore.load(),
            )
        }
    }

    fun setCheckOnLaunch(enabled: Boolean) {
        prefs.setCheckOnLaunch(enabled)
        _state.update { it.copy(checkOnLaunch = enabled) }
    }

    fun setUpdateUrl(url: String) {
        prefs.setUpdateUrl(url)
        _state.update { it.copy(updateUrl = url) }
    }

    fun checkForUpdate(manual: Boolean) {
        if (!manual && !_state.value.checkOnLaunch) return
        if (_state.value.updateChecking || _state.value.updateDownloading) return
        val url = _state.value.updateUrl.trim().ifBlank { prefs.updateUrl() }
        viewModelScope.launch {
            if (manual) {
                _state.update { it.copy(updateChecking = true, updateStatus = "正在检查更新…") }
            } else {
                _state.update { it.copy(updateChecking = true) }
            }
            val result = try {
                withContext(Dispatchers.IO) { updater.check(url) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UpdateCheckResult.Failed(userMessage(error))
            }
            when (result) {
                is UpdateCheckResult.UpToDate -> _state.update {
                    it.copy(
                        updateChecking = false,
                        updateStatus = if (manual) "已是最新版本（${result.versionName}）" else it.updateStatus,
                    )
                }
                is UpdateCheckResult.Failed -> _state.update {
                    it.copy(
                        updateChecking = false,
                        updateStatus = if (manual) result.message else it.updateStatus,
                    )
                }
                is UpdateCheckResult.Available -> {
                    val quiet = !manual && result.info.versionCode > 0 &&
                        result.info.versionCode == prefs.dismissedCode()
                    _state.update {
                        it.copy(
                            updateChecking = false,
                            updateOffer = if (quiet) it.updateOffer else result.info,
                            updateStatus = if (manual && quiet) "" else it.updateStatus,
                        )
                    }
                }
            }
        }
    }

    fun dismissUpdate() {
        val code = _state.value.updateOffer?.versionCode ?: 0
        if (code > 0) prefs.setDismissed(code)
        _state.update { it.copy(updateOffer = null) }
    }

    fun downloadUpdate() {
        val offer = _state.value.updateOffer ?: return
        if (_state.value.updateDownloading) return
        viewModelScope.launch {
            _state.update { it.copy(updateDownloading = true, updateProgress = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    updater.download(offer.apkUrl) { fraction ->
                        _state.update { it.copy(updateProgress = fraction) }
                    }
                }
                _state.update { it.copy(updateDownloading = false, updateProgress = 1f) }
                _installRequests.emit(file.absolutePath)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        updateDownloading = false,
                        updateProgress = null,
                        updateStatus = userMessage(error),
                    )
                }
            }
        }
    }

    /** Deletes history [entries]; with [withFiles] also deletes their videos from the gallery. */
    fun deleteHistory(entries: List<HistoryEntry>, withFiles: Boolean) {
        if (entries.isEmpty() || _state.value.historyBusy) return
        _state.update { it.copy(historyBusy = true, historyNotice = null) }
        val session = DeleteSession(entries, withFiles, mediaFiles)
        deleteSession = session
        viewModelScope.launch {
            val step = runDeleteStep { session.start() }
            handleDeleteStep(session, step)
        }
    }

    /** Result of the system delete dialog requested through [consentRequests]. */
    fun onDeleteConsent(granted: Boolean) {
        val session = deleteSession ?: return
        viewModelScope.launch {
            val step = runDeleteStep { session.onConsent(granted) }
            handleDeleteStep(session, step)
        }
    }

    fun historyNoticeShown() {
        _state.update { it.copy(historyNotice = null) }
    }

    private suspend fun runDeleteStep(block: () -> DeleteStep): DeleteStep? = try {
        withContext(Dispatchers.IO) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "delete failed", error)
        null
    }

    private suspend fun handleDeleteStep(session: DeleteSession, step: DeleteStep?) {
        if (deleteSession !== session) return
        when (step) {
            is DeleteStep.AskConsent -> _consentRequests.emit(step.prompt)
            is DeleteStep.Done -> {
                deleteSession = null
                val result = step.result
                withContext(Dispatchers.IO) { historyStore.remove(result.removed) }
                _state.update { current ->
                    // The success card plays history[0]; if that entry is gone, retire the card.
                    val latestGone = current.history.firstOrNull()?.let { it in result.removed } == true
                    val retire = latestGone && current.phase == Phase.Success
                    current.copy(
                        history = historyStore.load(),
                        historyBusy = false,
                        historyNotice = result.message,
                        phase = if (retire) Phase.Idle else current.phase,
                        status = if (retire) "" else current.status,
                    )
                }
            }
            null -> {
                deleteSession = null
                _state.update {
                    it.copy(historyBusy = false, historyNotice = "删除失败，记录都还在", history = historyStore.load())
                }
            }
        }
    }

    private fun setWorking(status: String) {
        _state.update { it.copy(phase = Phase.Working, status = status, progress = null) }
    }

    private fun record(
        success: Boolean,
        awemeId: String,
        title: String,
        author: String,
        message: String,
        uri: String?,
    ) {
        historyStore.add(
            HistoryEntry(
                awemeId = awemeId,
                title = title.ifBlank { "抖音视频" },
                author = author,
                savedAt = System.currentTimeMillis(),
                success = success,
                message = message,
                contentUri = uri,
            ),
        )
    }

    companion object {
        private const val TAG = "DouyinSave"
    }
}

internal fun userMessage(error: Throwable): String = when (error) {
    is ParseException -> error.message ?: "解析失败"
    is UnknownHostException -> "没有网络，或手机解析不了抖音域名。"
    is SocketTimeoutException -> "连接抖音超时，请重试。"
    is SSLException -> "和抖音的安全连接失败，请检查网络。"
    is IOException -> "网络中断，请重试。"
    else -> "下载失败：${error.message ?: error.javaClass.simpleName}"
}
