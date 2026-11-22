package app.douyin.save

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.douyin.save.parser.RemoteUpdate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val CardShape = RoundedCornerShape(24.dp)
private val ScreenPadding = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
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
    onDeleteHistory: (entries: List<HistoryEntry>, withFiles: Boolean) -> Unit = { _, _ -> },
    onNoticeShown: () -> Unit = {},
) {
    val busy = state.phase == Phase.Working
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.historyNotice) {
        val notice = state.historyNotice ?: return@LaunchedEffect
        snackbar.showSnackbar(notice, withDismissAction = true)
        onNoticeShown()
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = colors.inverseSurface,
                    contentColor = colors.inverseOnSurface,
                    dismissActionContentColor = colors.inverseOnSurface,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // A soft glow behind the header so the page does not read as a flat grey form.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.primary.copy(alpha = 0.10f), Color.Transparent),
                        ),
                    ),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header") {
                    Header(onOpenSettings = { showSettings = true })
                }
                item(key = "clipboard") {
                    // Keep the last offer around while the card animates out.
                    val lastOffer = remember { arrayOfNulls<ClipboardOffer>(1) }
                    state.clipboard?.let { lastOffer[0] = it }
                    AnimatedVisibility(
                        visible = state.clipboard != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        val offer = lastOffer[0] ?: return@AnimatedVisibility
                        ClipboardCard(
                            url = offer.url,
                            enabled = !busy,
                            onDownload = onDownloadClipboard,
                            onDismiss = onDismissClipboard,
                        )
                    }
                }
                item(key = "input") {
                    InputCard(
                        input = state.input,
                        busy = busy,
                        onInput = onInput,
                        onDownload = onDownloadInput,
                    )
                }
                item(key = "status") {
                    AnimatedVisibility(
                        visible = state.phase != Phase.Idle && state.status.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        StatusCard(
                            state = state,
                            onOpen = onOpen,
                        )
                    }
                }
                item(key = "history-title") {
                    SectionTitle(
                        title = "下载记录",
                        count = state.history.size,
                        working = state.historyBusy,
                        onClearAll = if (state.history.isNotEmpty()) {
                            { deleteRequest = DeleteRequest(state.history, clearAll = true) }
                        } else {
                            null
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (state.history.isEmpty()) {
                    item(key = "history-empty") { EmptyHistory() }
                } else {
                    items(state.history, key = { "${it.savedAt}-${it.awemeId}-${it.success}" }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onOpen = onOpen,
                            onDelete = if (state.historyBusy) {
                                null
                            } else {
                                { deleteRequest = DeleteRequest(listOf(entry), clearAll = false) }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item(key = "footer") { Footer() }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceContainerLow,
        ) {
            SettingsSheet(
                state = state,
                busy = busy,
                onCheckUpdate = onCheckUpdate,
                onUpdateUrl = onUpdateUrl,
                onCheckOnLaunch = onCheckOnLaunch,
            )
        }
    }

    deleteRequest?.let { request ->
        DeleteDialog(
            request = request,
            onConfirm = { withFiles ->
                deleteRequest = null
                onDeleteHistory(request.entries, withFiles)
            },
            onDismiss = { deleteRequest = null },
        )
    }

    val offer = state.updateOffer
    // A found update takes over from the settings sheet.
    LaunchedEffect(offer != null) {
        if (offer != null) showSettings = false
    }
    if (offer != null) {
        UpdateDialog(
            offer = offer,
            downloading = state.updateDownloading,
            progress = state.updateProgress,
            status = state.updateStatus,
            onDownload = onDownloadUpdate,
            onDismiss = onDismissUpdate,
        )
    }
}

// ───────────────────────── Header ─────────────────────────

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "抖音无水印",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "分享即存 · 无水印 · 只存本机相册",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "设置与更新")
        }
    }
}

@Composable
private fun BrandMark(size: Dp) {
    val extra = extraColors
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(extra.brandStart, extra.brandEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Download,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

// ───────────────────────── Clipboard ─────────────────────────

@Composable
private fun ClipboardCard(
    url: String,
    enabled: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = extraColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(extra.brandStart, extra.brandEnd)))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("发现剪贴板里的抖音链接", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onDownload,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = extra.brandStart,
                    disabledContainerColor = Color.White.copy(alpha = 0.4f),
                    disabledContentColor = extra.brandStart.copy(alpha = 0.6f),
                ),
            ) {
                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("立即下载")
            }
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.height(46.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.5f),
                ),
            ) {
                Text("忽略")
            }
        }
    }
}

// ───────────────────────── Input ─────────────────────────

@Composable
private fun InputCard(
    input: String,
    busy: Boolean,
    onInput: (String) -> Unit,
    onDownload: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "粘贴分享链接",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (input.isEmpty()) {
                SmallAction(
                    icon = AppIcons.Paste,
                    label = "粘贴",
                    enabled = !busy,
                    onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        if (text.isNotBlank()) onInput(text)
                    },
                )
            } else {
                SmallAction(
                    icon = Icons.Filled.Close,
                    label = "清空",
                    enabled = !busy,
                    onClick = { onInput("") },
                )
            }
        }
        TextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            enabled = !busy,
            placeholder = {
                Text(
                    "粘贴抖音分享文案，或 https://v.douyin.com/…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        PrimaryButton(
            text = if (busy) "处理中…" else "开始下载",
            busy = busy,
            enabled = !busy && input.isNotBlank(),
            onClick = onDownload,
        )
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.height(34.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ───────────────────────── Status ─────────────────────────

@Composable
private fun StatusCard(state: UiState, onOpen: (HistoryEntry) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = extraColors
    val container by animateColorAsState(
        when (state.phase) {
            Phase.Success -> extra.successContainer
            Phase.Error -> colors.errorContainer
            else -> colors.surfaceContainerLow
        },
        label = "statusContainer",
    )
    val accent = when (state.phase) {
        Phase.Success -> extra.success
        Phase.Error -> colors.error
        else -> colors.primary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(container)
            .border(1.dp, accent.copy(alpha = 0.16f), CardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "statusIcon",
            ) { phase ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when (phase) {
                        Phase.Success -> Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(24.dp))
                        Phase.Error -> Icon(AppIcons.ErrorOutline, null, tint = accent, modifier = Modifier.size(24.dp))
                        else -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = accent,
                            trackColor = accent.copy(alpha = 0.18f),
                            strokeCap = StrokeCap.Round,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title = when (state.phase) {
                    Phase.Success -> "下载完成"
                    Phase.Error -> "没能下载"
                    else -> "正在处理"
                }
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state.phase) {
                        Phase.Error -> colors.onErrorContainer
                        Phase.Success -> extra.onSuccessContainer
                        else -> colors.onSurfaceVariant
                    },
                )
            }
        }
        if (state.phase == Phase.Working) {
            val progress = state.progress
            if (progress == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round,
                )
            } else {
                val animated by animateFloatAsState(progress, label = "downloadProgress")
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            }
        }
        if (state.phase == Phase.Success) {
            // The entry just written by the view model is at the top of the history.
            val latest = state.history.firstOrNull()?.takeIf { it.success && it.contentUri != null }
            if (latest != null) {
                Button(
                    onClick = { onOpen(latest) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extra.success,
                        contentColor = extra.onSuccess,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("播放刚保存的视频")
                }
            }
        }
    }
}

// ───────────────────────── History ─────────────────────────

@Composable
private fun SectionTitle(
    title: String,
    count: Int,
    working: Boolean,
    onClearAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            AppIcons.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (working) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("正在删除…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (onClearAll != null) {
            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text("清空", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extra = extraColors
    val haptics = LocalHapticFeedback.current
    val openable = entry.success && entry.contentUri != null
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (openable) "打开视频" else "更多操作",
                onLongClickLabel = "更多操作",
                onClick = { if (openable) onOpen(entry) else menuOpen = true },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            )
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tileColor = if (entry.success) colors.primaryContainer else colors.errorContainer
        val tileTint = if (entry.success) colors.onPrimaryContainer else colors.error
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    openable -> Icons.Filled.PlayArrow
                    entry.success -> AppIcons.Movie
                    else -> AppIcons.ErrorOutline
                },
                contentDescription = null,
                tint = tileTint,
                modifier = Modifier.size(if (openable) 26.dp else 22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.title.ifBlank { "抖音视频" },
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(entry.author, formatTime(entry.savedAt))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (entry.success) extra.success else colors.error),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.success) extra.success else colors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多操作", tint = colors.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = colors.surfaceContainerHigh,
            ) {
                if (openable) {
                    DropdownMenuItem(
                        text = { Text("打开视频") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpen(entry)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (onDelete == null) "正在删除其他记录…" else "删除…") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    enabled = onDelete != null,
                    colors = MenuDefaults.itemColors(
                        textColor = colors.error,
                        leadingIconColor = colors.error,
                    ),
                    onClick = {
                        menuOpen = false
                        onDelete?.invoke()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, colors.outlineVariant, CardShape)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Movie, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("还没有下载记录", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(
                "两种方式都可以开始",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HowToStep(icon = AppIcons.Share, text = "在抖音里点「分享」，选择「抖音无水印」")
            HowToStep(icon = AppIcons.Link, text = "复制链接后打开本应用，会自动识别")
        }
    }
}

@Composable
private fun HowToStep(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Footer() {
    Text(
        "视频保存在 DCIM/Douyin · v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

// ───────────────────────── Delete ─────────────────────────

internal data class DeleteRequest(val entries: List<HistoryEntry>, val clearAll: Boolean) {
    /** Distinct gallery files behind these records; failed downloads have none. */
    val fileCount: Int = entries.mapNotNull { it.contentUri }.distinct().size
}

@Composable
internal fun DeleteDialog(
    request: DeleteRequest,
    onConfirm: (withFiles: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialWithFiles: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    // Safe default: keep the videos.
    var withFiles by rememberSaveable(request) { mutableStateOf(initialWithFiles) }
    val files = request.fileCount
    val single = request.entries.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.error, modifier = Modifier.size(26.dp))
            }
        },
        title = {
            Text(
                if (request.clearAll) "清空全部 ${request.entries.size} 条记录？" else "删除这条记录？",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (single != null && !request.clearAll) {
                    Text(
                        single.title.ifBlank { "抖音视频" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                if (files == 0) {
                    Text(
                        if (request.clearAll) "这些记录没有对应的相册文件，只会删除记录。" else "这条记录没有对应的相册文件，只会删除记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    DeleteOption(
                        selected = !withFiles,
                        title = "只删记录",
                        detail = if (files > 1) "相册里的 $files 个视频保留，仍可在相册里看到" else "相册里的视频保留，仍可在相册里看到",
                        badge = "推荐",
                        danger = false,
                        onSelect = { withFiles = false },
                    )
                    DeleteOption(
                        selected = withFiles,
                        title = "记录和相册里的视频一起删",
                        detail = if (files > 1) "同时从相册删除 $files 个视频" else "同时从相册删除这个视频",
                        badge = null,
                        danger = true,
                        onSelect = { withFiles = true },
                    )
                    AnimatedVisibility(
                        visible = withFiles,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.errorContainer)
                                .padding(12.dp),
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "视频会从相册永久删除，删了就恢复不了。系统可能还会弹窗请你再确认一次。",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onErrorContainer,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(withFiles && files > 0) },
                shape = RoundedCornerShape(14.dp),
                colors = if (withFiles && files > 0) {
                    ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    when {
                        withFiles && files > 0 -> "永久删除"
                        request.clearAll -> "清空记录"
                        else -> "删除记录"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DeleteOption(
    selected: Boolean,
    title: String,
    detail: String,
    badge: String?,
    danger: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (danger) colors.error else colors.primary
    val border by animateColorAsState(if (selected) accent else colors.outlineVariant, label = "optionBorder")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.06f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = accent),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (danger && selected) colors.error else colors.onSurface,
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

// ───────────────────────── Settings & update ─────────────────────────

@Composable
private fun SettingsSheet(
    state: UiState,
    busy: Boolean,
    onCheckUpdate: () -> Unit,
    onUpdateUrl: (String) -> Unit,
    onCheckOnLaunch: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extra = extraColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("设置与更新", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(
                    "当前版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onCheckOnLaunch(!state.checkOnLaunch) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启动时自动检查", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Text(
                        "打开应用时查一次 GitHub 上的新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(checked = state.checkOnLaunch, onCheckedChange = onCheckOnLaunch)
            }
            HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = state.updateUrl,
                onValueChange = onUpdateUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("更新地址") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(14.dp),
            )
        }

        FilledTonalButton(
            onClick = onCheckUpdate,
            enabled = !busy && !state.updateChecking && !state.updateDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.updateChecking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在检查…")
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("检查更新")
            }
        }

        // The checking message is already shown on the button.
        val status = state.updateStatus.takeUnless { state.updateChecking || it == "正在检查更新…" }.orEmpty()
        if (status.isNotBlank()) {
            val good = status.startsWith("已是最新")
            val bad = status.startsWith("读不到") || status.contains("失败") || status.startsWith("没有网络")
            val tint = when {
                good -> extra.success
                bad -> colors.error
                else -> colors.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (good) Icons.Filled.CheckCircle else AppIcons.ErrorOutline,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = tint)
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    offer: RemoteUpdate,
    downloading: Boolean,
    progress: Float?,
    status: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Only surface status text after the user tried to download, so a stale check message never shows here.
    var attempted by remember(offer) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(extraColors.brandStart, extraColors.brandEnd))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.SystemUpdate, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("发现新版本", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VersionPill("v${BuildConfig.VERSION_NAME}", highlighted = false)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "升级到",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    VersionPill("v${offer.versionName.removePrefix("v")}", highlighted = true)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("更新说明", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(
                    offer.changelog.ifBlank { "这个版本没有写更新说明。" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainer)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                )
                if (downloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (progress == null) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(CircleShape),
                                strokeCap = StrokeCap.Round,
                            )
                        } else {
                            val animated by animateFloatAsState(progress, label = "updateProgress")
                            LinearProgressIndicator(
                                progress = { animated },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(CircleShape),
                                strokeCap = StrokeCap.Round,
                                drawStopIndicator = {},
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                } else if (attempted && status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attempted = true
                    onDownload()
                },
                enabled = !downloading,
                shape = RoundedCornerShape(14.dp),
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
private fun VersionPill(text: String, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlighted) colors.primaryContainer else colors.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ───────────────────────── Shared ─────────────────────────

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val clock = "%02d:%02d".format(time.hour, time.minute)
    val today = LocalDate.now(zone)
    return when (time.toLocalDate()) {
        today -> "今天 $clock"
        today.minusDays(1) -> "昨天 $clock"
        else -> "${time.monthValue}月${time.dayOfMonth}日 $clock"
    }
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
