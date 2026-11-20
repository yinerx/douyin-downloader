package app.douyin.save

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
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
        // The page is always light, so status icons stay dark even if the phone is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onInput = viewModel::updateInput,
                    onDownloadInput = { runWithStorage { viewModel.downloadInput() } },
                    onDownloadClipboard = { runWithStorage { viewModel.downloadClipboard() } },
                    onDismissClipboard = viewModel::dismissClipboard,
                    onOpen = { openVideo(this@MainActivity, it) },
                )
            }
        }
        // Creating a WebView during the first frame stops the system splash screen
        // from exiting. The activity window is then left at alpha 0, which looks like
        // a permanent white screen. Attach the WebView only after that first draw.
        window.decorView.doOnPreDraw {
            window.decorView.post { attachWebView() }
        }
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Clipboard reads before the window is focused throw or block on some
        // Android 10+ / HyperOS builds. Wait until focus, then do it off the draw pass.
        if (hasFocus && intent?.action != Intent.ACTION_SEND) {
            window.decorView.post { readClipboard() }
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
