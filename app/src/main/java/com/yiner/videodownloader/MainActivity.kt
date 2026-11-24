package com.yiner.videodownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingDownload: (() -> Unit)? = null
    private var queuedShare: String? = null
    private var webView: WebView? = null

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingDownload
        pendingDownload = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(this, "需要存储权限才能保存到相册。", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so Android 12+ can hand us the splash screen.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { false }
        // Follows the system light / dark mode; re-applied from Compose when the mode changes
        // (uiMode is in configChanges, so the activity is not recreated).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val darkTheme = isSystemInDarkTheme()
            LaunchedEffect(darkTheme) { applySystemBars(darkTheme) }
            DouyinSaveTheme(darkTheme = darkTheme) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onInput = viewModel::updateInput,
                    onDownloadInput = { runWithStorage { viewModel.downloadInput() } },
                    onDownloadClipboard = { runWithStorage { viewModel.downloadClipboard() } },
                    onDismissClipboard = viewModel::dismissClipboard,
                    onOpen = { openVideo(this@MainActivity, it) },
                    onCheckUpdate = { viewModel.checkForUpdate(manual = true) },
                    onUpdateUrl = viewModel::setUpdateUrl,
                    onCheckOnLaunch = viewModel::setCheckOnLaunch,
                    onDownloadUpdate = { installOrAskPermission() },
                    onDismissUpdate = viewModel::dismissUpdate,
                    onDeleteHistory = viewModel::deleteHistory,
                    onClearHistory = viewModel::clearHistory,
                    onUndoDelete = viewModel::undoDelete,
                    onUndoDismissed = viewModel::undoDismissed,
                    onToggleChoice = viewModel::toggleChoice,
                    onToggleAllChoices = viewModel::toggleAllChoices,
                    onConfirmChoices = { runWithStorage { viewModel.confirmChoices() } },
                    onDismissChoices = viewModel::dismissChoices,
                    onCancelDownload = viewModel::cancelDownload,
                    onHideDownload = viewModel::hideDownload,
                    onShowDownload = viewModel::showDownload,
                    onDismissResult = viewModel::dismissResult,
                    onRetryDownload = { runWithStorage { viewModel.retryDownload() } },
                    onShare = { shareVideos(this@MainActivity, it) },
                )
                InstallUpdateEffect()
            }
        }
        // Creating a WebView during the first frame stops the system splash screen
        // from exiting. The activity window is then left at alpha 0, which looks like
        // a permanent white screen. Attach the WebView only after that first draw.
        window.decorView.doOnPreDraw {
            window.decorView.post {
                attachWebView()
                viewModel.checkForUpdate(manual = false)
            }
        }
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Clipboard reads before the window is focused throw or block on some
        // Android 10+ / HyperOS builds. Wait until focus, then do it off the draw pass.
        if (hasFocus && intent?.action != Intent.ACTION_SEND) {
            window.decorView.post { readClipboard() }
        }
    }

    private fun handleIncoming(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.isNotBlank() }
            else -> return
        }
        intent.action = Intent.ACTION_MAIN
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "分享内容里没有文字链接。", Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.routerSource == null) {
            queuedShare = text
        } else {
            runWithStorage { viewModel.start(text) }
        }
    }

    private fun readClipboard() {
        if (!hasWindowFocus()) return
        try {
            val clipboard = getSystemService(ClipboardManager::class.java) ?: return
            if (!clipboard.hasPrimaryClip()) return
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                .orEmpty()
            if (text.isBlank()) return
            viewModel.offerClipboard(text)
        } catch (error: RuntimeException) {
            Log.w(TAG, "clipboard read skipped", error)
        }
    }

    private fun attachWebView() {
        if (webView != null) return
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val created = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val content = findViewById<ViewGroup>(android.R.id.content)
        // Behind the Compose view, so a blank WebView cannot cover the UI.
        content.addView(
            created,
            0,
            FrameLayout.LayoutParams(
                resources.displayMetrics.widthPixels.coerceAtLeast(1),
                resources.displayMetrics.heightPixels.coerceAtLeast(1),
            ),
        )
        webView = created
        viewModel.routerSource = WebViewRouterSource(created)
        val text = queuedShare ?: return
        queuedShare = null
        runWithStorage { viewModel.start(text) }
    }

    private fun applySystemBars(darkTheme: Boolean) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun installOrAskPermission() {
        if (!UpdateInstaller.canInstall(this)) {
            Toast.makeText(this, "请允许本应用安装未知应用，然后回来再点一次。", Toast.LENGTH_LONG).show()
            UpdateInstaller.openInstallPermission(this)
            return
        }
        viewModel.downloadUpdate()
    }

    @Composable
    private fun InstallUpdateEffect() {
        LaunchedEffect(Unit) {
            viewModel.installRequests.collect { path ->
                val file = java.io.File(path)
                if (!UpdateInstaller.canInstall(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "请允许本应用安装未知应用，然后回来再点一次。", Toast.LENGTH_LONG).show()
                    UpdateInstaller.openInstallPermission(this@MainActivity)
                } else {
                    runCatching { UpdateInstaller.install(this@MainActivity, file) }
                        .onFailure { error ->
                            Toast.makeText(this@MainActivity, "无法打开安装器：${error.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    }

    private fun runWithStorage(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingDownload = action
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    companion object {
        private const val TAG = "DouyinSave"
    }
}
