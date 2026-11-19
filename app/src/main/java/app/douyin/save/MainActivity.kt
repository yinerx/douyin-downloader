package app.douyin.save

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingDownload: (() -> Unit)? = null
    private var queuedShare: String? = null

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    state = state,
                    onInput = viewModel::updateInput,
                    onDownloadInput = { runWithStorage { viewModel.downloadInput() } },
                    onDownloadClipboard = { runWithStorage { viewModel.downloadClipboard() } },
                    onDismissClipboard = viewModel::dismissClipboard,
                    onOpen = { openVideo(this@MainActivity, it) },
                )
                HiddenWebView(onReady = { source ->
                    viewModel.routerSource = source
                    window.decorView.post {
                        val text = queuedShare ?: return@post
                        queuedShare = null
                        runWithStorage { viewModel.start(text) }
                    }
                })
            }
        }
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        if (intent?.action != Intent.ACTION_SEND) {
            readClipboard()
        }
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        intent.action = Intent.ACTION_MAIN
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
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        if (!clipboard.hasPrimaryClip()) return
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            .orEmpty()
        if (text.isBlank()) return
        viewModel.offerClipboard(text)
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
}

@Composable
private fun HiddenWebView(onReady: (WebViewRouterSource) -> Unit) {
    AndroidView(
        factory = { context ->
            WebView(context).also { webView ->
                onReady(WebViewRouterSource(webView))
            }
        },
        modifier = Modifier
            .size(width = 360.dp, height = 640.dp)
            .offset(x = (-2400).dp),
    )
}
