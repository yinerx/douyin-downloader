package app.douyin.save

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.douyin.save.parser.RemoteUpdate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

private val Ink = Color(0xFF111111)
private val Muted = Color(0xFF5C6570)
private val Line = Color(0xFFE4E7EC)
private val Paper = Color(0xFFF5F6F8)
private val CardWhite = Color(0xFFFFFFFF)
private val Good = Color(0xFF1B7F4E)
private val Bad = Color(0xFFB42318)
private val Shape = RoundedCornerShape(16.dp)

@Composable
internal fun HomeScreen(
    state: UiState,
    onInput: (String) -> Unit,
    onDownloadInput: () -> Unit,
    onDownloadClipboard: () -> Unit,
    onDismissClipboard: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onCheckUpdate: () -> Unit,
    onUpdateUrl: (String) -> Unit,
    onCheckOnLaunch: (Boolean) -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    val busy = state.phase == Phase.Working
    Scaffold(containerColor = Paper) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("抖音无水印", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "把分享链接存成无水印视频，只写在这台手机的相册里。",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            if (state.clipboard != null) {
                item {
                    Section {
                        Text("检测到剪贴板中的抖音链接", color = Ink, fontWeight = FontWeight.Medium)
                        Text(
                            state.clipboard.url,
                            color = Muted,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onDownloadClipboard,
                                enabled = !busy,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                            ) {
                                Text("下载")
                            }
                            OutlinedButton(
                                onClick = onDismissClipboard,
                                enabled = !busy,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("忽略")
                            }
                        }
                    }
                }
            }
            item {
                Section {
                    Text("分享文案或链接", color = Ink, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInput,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("粘贴抖音分享文案，或 https://v.douyin.com/…") },
                        enabled = !busy,
                    )
                    Button(
                        onClick = onDownloadInput,
                        enabled = !busy && state.input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Ink,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFC5CAD1),
                            disabledContentColor = Color.White,
                        ),
                    ) {
                        Text(if (busy) "处理中" else "开始下载")
                    }
                }
            }
            if (state.phase != Phase.Idle && state.status.isNotBlank()) {
                item {
                    Section {
                        val color = when (state.phase) {
                            Phase.Success -> Good
                            Phase.Error -> Bad
                            else -> Ink
                        }
                        Text(state.status, color = color, fontSize = 15.sp, lineHeight = 21.sp)
                        if (state.phase == Phase.Working) {
                            val progress = state.progress
                            if (progress == null) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ink, trackColor = Line)
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Ink,
                                    trackColor = Line,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "下载记录",
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        "还没有记录。在抖音里点分享并选择本应用，或把链接粘贴到上面。",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            } else {
                items(state.history, key = { "${it.savedAt}-${it.awemeId}-${it.success}" }) { entry ->
                    Section {
                        Text(
                            entry.title.ifBlank { "抖音视频" },
                            color = Ink,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOf(entry.author, formatTime(entry.savedAt))
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(meta, color = Muted, fontSize = 13.sp)
                        }
                        Text(entry.message, color = if (entry.success) Good else Bad, fontSize = 13.sp)
                        if (entry.success && entry.contentUri != null) {
                            TextButton(onClick = { onOpen(entry) }, contentPadding = PaddingValues(0.dp)) {
                                Text("打开视频")
                            }
                        }
                    }
                }
            }
            item {
                UpdateSection(
                    state = state,
                    busy = busy,
                    onCheckUpdate = onCheckUpdate,
                    onUpdateUrl = onUpdateUrl,
                    onCheckOnLaunch = onCheckOnLaunch,
                )
            }
            item { Text("", modifier = Modifier.padding(bottom = 8.dp)) }
        }
    }
    val offer = state.updateOffer
    if (offer != null) {
        UpdateDialog(
            offer = offer,
            downloading = state.updateDownloading,
            progress = state.updateProgress,
            onDownload = onDownloadUpdate,
            onDismiss = onDismissUpdate,
        )
    }
}

@Composable
private fun UpdateSection(
    state: UiState,
    busy: Boolean,
    onCheckUpdate: () -> Unit,
    onUpdateUrl: (String) -> Unit,
    onCheckOnLaunch: (Boolean) -> Unit,
) {
    Section {
        Text("检查更新", color = Ink, fontWeight = FontWeight.Medium)
        Text("当前版本 ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 13.sp)
        OutlinedTextField(
            value = state.updateUrl,
            onValueChange = onUpdateUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("更新地址") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启动时自动检查", color = Ink, modifier = Modifier.weight(1f))
            Switch(checked = state.checkOnLaunch, onCheckedChange = onCheckOnLaunch)
        }
        OutlinedButton(
            onClick = onCheckUpdate,
            enabled = !busy && !state.updateChecking && !state.updateDownloading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (state.updateChecking) "正在检查…" else "检查更新")
        }
        if (state.updateStatus.isNotBlank()) {
            val color = when {
                state.updateStatus.startsWith("已是最新") -> Good
                state.updateStatus.startsWith("读不到") ||
                    state.updateStatus.contains("失败") ||
                    state.updateStatus.startsWith("没有网络") -> Bad
                else -> Ink
            }
            Text(state.updateStatus, color = color, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun UpdateDialog(
    offer: RemoteUpdate,
    downloading: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 ${offer.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("更新说明", color = Muted, fontSize = 13.sp)
                Text(
                    offer.changelog,
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                )
                if (downloading) {
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ink, trackColor = Line)
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Ink,
                            trackColor = Line,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            ) {
                Text(if (downloading) "正在下载…" else "下载并安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) { Text("以后再说") }
        },
    )
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, Shape)
            .background(CardWhite, Shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return "${time.monthValue}月${time.dayOfMonth}日 %02d:%02d".format(time.hour, time.minute)
}

internal fun openVideo(context: Context, entry: HistoryEntry) {
    val uri = entry.contentUri ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uri), "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "没有找到可以播放这个视频的应用", Toast.LENGTH_SHORT).show()
    }
}
