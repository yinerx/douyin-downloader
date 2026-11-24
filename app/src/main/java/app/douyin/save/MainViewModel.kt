package app.douyin.save

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.douyin.save.parser.AwemeIds
import app.douyin.save.parser.DownloadNames
import app.douyin.save.parser.DouyinRequest
import app.douyin.save.parser.ParseException
import app.douyin.save.parser.Platform
import app.douyin.save.parser.RemoteUpdate
import app.douyin.save.parser.ShareTextParser
import app.douyin.save.parser.VideoInfoParser
import app.douyin.save.parser.XLinks
import app.douyin.save.parser.XPost
import app.douyin.save.parser.XVideo
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

internal enum class Phase { Idle, Working, Picking, Success, Error }

internal data class ClipboardOffer(val rawText: String, val url: String, val platform: Platform)

internal data class VideoChoice(
    val index: Int,
    val title: String,
    val detail: String,
    val thumbnailUrl: String?,
)

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
    val historyUndo: HistoryUndo? = null,
    val choices: List<VideoChoice> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val dialogHidden: Boolean = false,
    val savedNow: List<HistoryEntry> = emptyList(),
)

/** A just-deleted batch the user can still bring back from the snackbar. */
internal data class HistoryUndo(val id: Long, val message: String)

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
    private val http = HttpCalls()
    private val linkResolver = LinkResolver(client, http)
    private val sharePage = HttpSharePage(client, http)
    private val xClient = XClient(client, http)
    private val downloader = VideoDownloader(downloadClient, http)
    private val gallery = GallerySaver(app, downloader)
    private val updater = AppUpdater(app, client)
    private val _installRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val installRequests: SharedFlow<String> = _installRequests.asSharedFlow()

    private class DeletedBatch(val id: Long, val snapshot: List<HistoryEntry>, val removed: List<HistoryEntry>)

    private var lastDeleted: DeletedBatch? = null
    private var nextUndoId = 0L

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
    private var pendingX: XPost? = null
    private var currentIsX = false
    private var runId = 0
    private var lastText = ""

    fun updateInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun offerClipboard(text: String) {
        if (_state.value.phase == Phase.Working) return
        val link = runCatching { ShareTextParser.extract(text) }.getOrNull() ?: return
        if (link.url == ignoredUrl) return
        _state.update { it.copy(clipboard = ClipboardOffer(text, link.url, link.platform)) }
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
        runId++
        lastText = text
        pendingX = null
        currentIsX = false
        ignoredUrl = runCatching { ShareTextParser.extract(text).url }.getOrNull()
        _state.update {
            it.copy(
                input = text,
                clipboard = null,
                phase = Phase.Working,
                status = "正在识别链接…",
                progress = null,
                choices = emptyList(),
                selected = emptySet(),
                dialogHidden = false,
                savedNow = emptyList(),
            )
        }
        launchWork { permit -> download(text, permit) }
    }

    fun cancelDownload() {
        if (_state.value.phase != Phase.Working) return
        runId++
        http.cancel()
        job?.cancel()
        job = null
        pendingX = null
        _state.update {
            it.copy(
                phase = Phase.Idle,
                status = "",
                progress = null,
                choices = emptyList(),
                selected = emptySet(),
                dialogHidden = false,
                savedNow = emptyList(),
            )
        }
    }

    fun hideDownload() {
        if (_state.value.phase != Phase.Working) return
        _state.update { it.copy(dialogHidden = true) }
    }

    fun showDownload() {
        if (_state.value.phase == Phase.Idle || _state.value.phase == Phase.Picking) return
        _state.update { it.copy(dialogHidden = false) }
    }

    fun dismissResult() {
        val phase = _state.value.phase
        if (phase != Phase.Success && phase != Phase.Error) return
        _state.update {
            it.copy(phase = Phase.Idle, status = "", progress = null, dialogHidden = false, savedNow = emptyList())
        }
    }

    fun retryDownload() {
        val text = lastText
        if (text.isBlank()) return
        start(text)
    }

    fun toggleChoice(index: Int) {
        if (_state.value.phase != Phase.Picking) return
        _state.update { state ->
            val selected = state.selected.toMutableSet()
            if (!selected.add(index)) selected.remove(index)
            state.copy(selected = selected)
        }
    }

    fun toggleAllChoices() {
        if (_state.value.phase != Phase.Picking) return
        _state.update { state ->
            val all = state.choices.map { it.index }.toSet()
            val selected = if (state.selected.size == all.size) emptySet() else all
            state.copy(selected = selected)
        }
    }

    fun confirmChoices() {
        val post = pendingX ?: return
        if (_state.value.phase != Phase.Picking) return
        val chosen = post.videos.filter { it.index in _state.value.selected }
        if (chosen.isEmpty()) return
        pendingX = null
        currentIsX = true
        _state.update {
            it.copy(
                phase = Phase.Working,
                status = "正在准备下载…",
                progress = null,
                choices = emptyList(),
                selected = emptySet(),
                clipboard = null,
                dialogHidden = false,
                savedNow = emptyList(),
            )
        }
        launchWork { permit -> downloadChosen(post, chosen, permit) }
    }

    fun dismissChoices() {
        if (_state.value.phase != Phase.Picking) return
        pendingX = null
        _state.update {
            it.copy(phase = Phase.Idle, status = "", progress = null, choices = emptyList(), selected = emptySet())
        }
    }

    private fun launchWork(block: suspend (() -> Boolean) -> Unit) {
        val id = runId
        http.cancel()
        job?.cancel()
        job = viewModelScope.launch {
            val permit = { id == runId }
            try {
                block(permit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isActive || id != runId) return@launch
                val message = userMessage(error, currentIsX)
                Log.w(TAG, message, error)
                record(
                    success = false,
                    awemeId = "",
                    title = _state.value.input.take(40),
                    author = "",
                    message = message,
                    uri = null,
                    platform = if (currentIsX) "X" else "抖音",
                )
                if (id != runId) return@launch
                _state.update {
                    it.copy(
                        phase = Phase.Error,
                        status = message,
                        progress = null,
                        history = historyStore.load(),
                        choices = emptyList(),
                        selected = emptySet(),
                        dialogHidden = false,
                    )
                }
            }
        }
    }

    private suspend fun download(text: String, permit: () -> Boolean) {
        val link = ShareTextParser.extract(text)
        currentIsX = link.platform == Platform.X
        when (link.platform) {
            Platform.DOUYIN -> downloadDouyin(link.url, permit)
            Platform.X -> prepareX(link.url, permit)
        }
    }

    private suspend fun prepareX(url: String, permit: () -> Boolean) {
        setWorking("正在解析帖子…")
        val post = withContext(Dispatchers.IO) { xClient.fetch(url, permit) }
        if (!permit()) throw CancellationException("已取消")
        if (post.videos.size <= 1) {
            downloadChosen(post, post.videos, permit)
            return
        }
        pendingX = post
        _state.update {
            it.copy(
                phase = Phase.Picking,
                status = "",
                progress = null,
                choices = post.videos.map { video ->
                    VideoChoice(
                        index = video.index,
                        title = "第 ${video.index} 个",
                        detail = choiceDetail(video),
                        thumbnailUrl = video.thumbnailUrl,
                    )
                },
                selected = emptySet(),
            )
        }
    }

    private suspend fun downloadChosen(post: XPost, videos: List<XVideo>, permit: () -> Boolean) {
        if (videos.isEmpty()) throw ParseException("请先选择要下载的视频。")
        val saved = mutableListOf<HistoryEntry>()
        videos.forEach { video ->
            if (!permit()) throw CancellationException("已取消")
            val prefix = if (post.videos.size > 1) "第 ${video.index} 个 · " else ""
            setWorking("${prefix}正在选择清晰度…")
            val chosen = withContext(Dispatchers.IO) {
                downloader.select(
                    video.urls,
                    userAgent = XLinks.USER_AGENT,
                    referer = XLinks.REFERER,
                    emptyMessage = "视频地址无法下载。X 拒绝了请求，或链接已失效。",
                    minBytes = 1024,
                    permit = permit,
                )
            }
            var lastTick = 0L
            val uri = withContext(Dispatchers.IO) {
                gallery.save(
                    name = DownloadNames.xFileName(post.author, post.id, video.index, post.videos.size),
                    source = chosen,
                    album = "X",
                    onProgress = { read, total ->
                        val now = SystemClock.elapsedRealtime()
                        val done = total > 0 && read >= total
                        if (!done && now - lastTick < 200) return@save
                        lastTick = now
                        val fraction = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else null
                        val percent = if (total > 0) "正在下载 ${(read * 100 / total).toInt()}%" else "正在下载…"
                        _state.update { it.copy(phase = Phase.Working, status = prefix + percent, progress = fraction) }
                    },
                    userAgent = XLinks.USER_AGENT,
                    referer = XLinks.REFERER,
                    minBytes = 1024,
                    permit = permit,
                )
            }
            saved += record(
                success = true,
                awemeId = post.id,
                title = xTitle(post, video),
                author = post.author,
                message = "已保存到相册",
                uri = uri,
                platform = "X",
            )
        }
        val status = if (saved.size == 1) {
            "已保存到相册（DCIM/X）"
        } else {
            "已保存 ${saved.size} 个视频到相册（DCIM/X）"
        }
        _state.update {
            it.copy(
                phase = Phase.Success,
                status = status,
                progress = 1f,
                history = historyStore.load(),
                savedNow = saved.toList(),
                dialogHidden = false,
            )
        }
    }

    private suspend fun downloadDouyin(url: String, permit: () -> Boolean) {
        setWorking("正在解析短链接…")
        val finalUrl = withContext(Dispatchers.IO) { linkResolver.resolve(url, permit) }
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
            runCatching { sharePage.fetch(awemeId, permit) }.getOrElse { error ->
                if (error is CancellationException) throw error
                null
            }
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
        val chosen = withContext(Dispatchers.IO) { downloader.select(video.downloadUrls, permit = permit) }
        Log.i(TAG, "uri=${video.videoUri} bytes=${chosen.totalBytes} play=${chosen.url.substringBefore('?')}")

        var lastTick = 0L
        val saved = withContext(Dispatchers.IO) {
            gallery.save(
                name = DownloadNames.fileName(video.title, video.awemeId),
                source = chosen,
                album = "Douyin",
                onProgress = { read, total ->
                    val now = SystemClock.elapsedRealtime()
                    val done = total > 0 && read >= total
                    if (!done && now - lastTick < 200) return@save
                    lastTick = now
                    val fraction = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else null
                    val label = if (total > 0) "正在下载 ${(read * 100 / total).toInt()}%" else "正在下载…"
                    _state.update { it.copy(phase = Phase.Working, status = label, progress = fraction) }
                },
                permit = permit,
            )
        }
        val entry = record(
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
                savedNow = listOf(entry),
                dialogHidden = false,
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

    /** Removes [entries] from the in-app history only; saved videos stay in the gallery. */
    fun deleteHistory(entries: List<HistoryEntry>) {
        removeFromHistory(entries, if (entries.size == 1) "已删除 1 条记录" else "已删除 ${entries.size} 条记录")
    }

    fun clearHistory() {
        val all = historyStore.load()
        removeFromHistory(all, "已清空 ${all.size} 条记录")
    }

    /** Snackbar "撤销": brings back the batch [id] if it is still the latest delete. */
    fun undoDelete(id: Long) {
        val batch = lastDeleted?.takeIf { it.id == id } ?: return
        lastDeleted = null
        historyStore.restore(batch.snapshot, batch.removed)
        _state.update { it.copy(history = historyStore.load(), historyUndo = null) }
    }

    /** The snackbar for [id] went away without "撤销"; the delete is final. */
    fun undoDismissed(id: Long) {
        if (lastDeleted?.id == id) lastDeleted = null
        _state.update { if (it.historyUndo?.id == id) it.copy(historyUndo = null) else it }
    }

    private fun removeFromHistory(entries: List<HistoryEntry>, message: String) {
        if (entries.isEmpty()) return
        val snapshot = historyStore.load()
        val wanted = entries.toSet()
        val removed = snapshot.filter { it in wanted }
        if (removed.isEmpty()) return
        historyStore.remove(removed)
        val batch = DeletedBatch(++nextUndoId, snapshot, removed)
        lastDeleted = batch
        _state.update { current ->
            // The success card plays history[0]; if that entry is gone, retire the card.
            val latestGone = current.history.firstOrNull()?.let { it in wanted } == true
            val retire = latestGone && current.phase == Phase.Success
            current.copy(
                history = historyStore.load(),
                historyUndo = HistoryUndo(batch.id, message),
                phase = if (retire) Phase.Idle else current.phase,
                status = if (retire) "" else current.status,
            )
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
        platform: String = "抖音",
    ): HistoryEntry {
        val entry = HistoryEntry(
            awemeId = awemeId,
            title = title.ifBlank { if (platform == "X") "X 视频" else "抖音视频" },
            author = author,
            savedAt = System.currentTimeMillis(),
            success = success,
            message = message,
            contentUri = uri,
            platform = platform,
        )
        historyStore.add(entry)
        return entry
    }

    companion object {
        private const val TAG = "DouyinSave"
    }
}

internal fun userMessage(error: Throwable, x: Boolean = false): String = when (error) {
    is ParseException -> error.message ?: "解析失败"
    is UnknownHostException -> if (x) "没有网络，或手机解析不了 X 的域名。" else "没有网络，或手机解析不了抖音域名。"
    is SocketTimeoutException -> if (x) "连接 X 超时，请重试。" else "连接抖音超时，请重试。"
    is SSLException -> if (x) "和 X 的安全连接失败，请检查网络。" else "和抖音的安全连接失败，请检查网络。"
    is IOException -> "网络中断，请重试。"
    else -> "下载失败：${error.message ?: error.javaClass.simpleName}"
}

private fun choiceDetail(video: XVideo): String {
    val duration = video.durationMs?.let(::formatDuration)
    return when {
        video.kind == "gif" && duration != null -> "动图 · $duration"
        video.kind == "gif" -> "动图"
        duration != null -> duration
        else -> "视频"
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes > 0) "$minutes 分 $rest 秒" else "$rest 秒"
}

private fun xTitle(post: XPost, video: XVideo): String {
    val headline = post.text.lineSequence().firstOrNull().orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(40)
        .trim()
    val base = when {
        headline.isNotEmpty() -> headline
        video.kind == "gif" -> "X 动图"
        else -> "X 视频"
    }
    return if (post.videos.size > 1) "$base（第 ${video.index} 个）" else base
}
