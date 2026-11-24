package app.douyin.save

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadDialog(
    state: UiState,
    onCancel: () -> Unit,
    onHide: () -> Unit,
    onPlay: (HistoryEntry) -> Unit,
    onShare: (List<HistoryEntry>) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    if (state.dialogHidden) return
    when (state.phase) {
        Phase.Working -> WorkingDialog(state, onCancel, onHide)
        Phase.Success -> SuccessDialog(state, onPlay, onShare, onClose)
        Phase.Error -> FailureDialog(state, onRetry, onClose)
        else -> Unit
    }
}

@Composable
private fun WorkingDialog(state: UiState, onCancel: () -> Unit, onHide: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onHide,
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        title = { Text("正在处理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    state.status.ifBlank { "正在处理…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                val progress = state.progress
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    val animated by animateFloatAsState(progress, label = "downloadProgress")
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) { Text("后台下载") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}

@Composable
private fun SuccessDialog(
    state: UiState,
    onPlay: (HistoryEntry) -> Unit,
    onShare: (List<HistoryEntry>) -> Unit,
    onClose: () -> Unit,
) {
    val saved = state.savedNow.filter { it.success && it.contentUri != null }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        title = { Text("下载完成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.status, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                if (saved.size > 1) {
                    Text(
                        "共 ${saved.size} 个视频，播放打开第 1 个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        saved.forEach { entry ->
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { saved.firstOrNull()?.let(onPlay) },
                enabled = saved.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("播放") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onShare(saved) }, enabled = saved.isNotEmpty()) { Text("分享") }
                TextButton(onClick = onClose) { Text("完成") }
            }
        },
    )
}

@Composable
private fun FailureDialog(state: UiState, onRetry: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        title = { Text("下载失败") },
        text = {
            Text(
                state.status.ifBlank { "下载失败" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
        },
        confirmButton = {
            Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) { Text("重试") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        },
    )
}
