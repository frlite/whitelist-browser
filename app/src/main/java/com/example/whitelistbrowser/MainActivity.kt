package com.example.whitelistbrowser

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.whitelistbrowser.databinding.ActivityMainBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var whitelist: WhitelistStore

    /** 记录最近一次在 onPageStarted 中被拦截的 URL，防止重定向循环 */
    private var lastInterceptedUrl: String? = null

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) openLocalFile(uri)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScan()
        } else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanLauncher = registerForActivityResult(
        ScanContract()
    ) { result ->
        if (result.contents != null) {
            handleScanResult(result.contents)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        whitelist = WhitelistStore(this)

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            // 允许 file:// 页面执行 JS 和访问本地资源（游戏等本地 HTML 需要）
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        binding.webView.webViewClient = RestrictedWebViewClient()

        binding.btnGo.setOnClickListener { navigateToAddress() }
        binding.etUrl.setOnEditorActionListener { _, _, _ ->
            navigateToAddress()
            true
        }
        binding.btnHome.setOnClickListener { loadHome() }
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.btnRefresh.setOnClickListener { binding.webView.reload() }
        binding.btnOpenFile.setOnClickListener {
            openFileLauncher.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml"))
        }
        binding.btnScan.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startScan()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnWhitelist.setOnClickListener {
            startActivity(android.content.Intent(this, WhitelistActivity::class.java))
        }

        loadHome()
    }

    private fun loadHome() {
        binding.webView.loadUrl("file:///android_asset/home.html")
    }

    private fun navigateToAddress() {
        val input = binding.etUrl.text.toString().trim()
        if (input.isEmpty()) return
        val url = normalizeInput(input) ?: run {
            Toast.makeText(this, R.string.toast_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse(url)
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                if (isAllowed(url)) {
                    binding.webView.loadUrl(url)
                } else {
                    showWhitelistDialog(url)
                }
            }
            "file" -> {
                if (isLocalFileAllowed(uri)) {
                    binding.webView.loadUrl(url)
                } else {
                    Toast.makeText(this, R.string.toast_scheme_blocked, Toast.LENGTH_SHORT).show()
                }
            }
            else -> Toast.makeText(this, R.string.toast_scheme_blocked, Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeInput(input: String): String? {
        if (input.contains(" ")) return null
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.startsWith("file://") -> input
            input.contains(".") -> "https://$input"
            else -> null
        }
    }

    /** 判断某个 URL 是否允许加载 */
    private fun isAllowed(url: String): Boolean {
        val uri = Uri.parse(url)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val host = uri.host ?: return false
                whitelist.contains(host)
            }
            "file" -> isLocalFileAllowed(uri)
            "about" -> true
            else -> false
        }
    }

    /** 只允许加载应用 assets 首页和用户从「打开本地文件」复制进来的文件 */
    private fun isLocalFileAllowed(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return path.startsWith("/android_asset/") ||
            path.startsWith("${filesDir.absolutePath}/local/")
    }

    private fun showWhitelistDialog(url: String) {
        val host = Uri.parse(url).host ?: url
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_blocked_title)
            .setMessage(getString(R.string.dialog_blocked_message, host))
            .setPositiveButton(R.string.dialog_add_whitelist) { _, _ ->
                whitelist.add(host)
                binding.webView.loadUrl(url)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun openLocalFile(uri: Uri) {
        val file = LocalFileHelper.copyToLocal(this, uri)
        if (file != null) {
            binding.webView.loadUrl(Uri.fromFile(file).toString())
        } else {
            Toast.makeText(this, R.string.toast_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("将二维码放入框内扫描")
            setBeepEnabled(false)
            setOrientationLocked(false)
            setCameraId(0)
        }
        scanLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        val uri = Uri.parse(contents)
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                if (isAllowed(contents)) {
                    binding.webView.loadUrl(contents)
                } else {
                    showWhitelistDialog(contents)
                }
            }
            else -> Toast.makeText(this, "不支持的链接类型", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class RestrictedWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val url = request.url.toString()
            if (isAllowed(url)) return false
            // 主框架导航被拦截：只提示，不跳转（页面仍停留在当前页）
            if (request.isForMainFrame) {
                val host = Uri.parse(url).host
                if (host != null && !whitelist.contains(host)) {
                    Toast.makeText(this@MainActivity,
                        getString(R.string.toast_blocked, host), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity,
                        R.string.toast_scheme_blocked, Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (isAllowed(url)) return false
            val host = Uri.parse(url).host
            if (host != null && !whitelist.contains(host)) {
                Toast.makeText(this@MainActivity,
                    getString(R.string.toast_blocked, host), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity,
                    R.string.toast_scheme_blocked, Toast.LENGTH_SHORT).show()
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (url != null && !isAllowed(url)) {
                handlePageStartBlocked(view, url)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            binding.etUrl.setText(url)
        }
    }

    /** onPageStarted 阶段拦截（兜底，主要应对服务器重定向） */
    private fun handlePageStartBlocked(view: WebView, url: String) {
        if (url == lastInterceptedUrl) {
            // 防止循环拦截：直接回首页
            lastInterceptedUrl = null
            loadHome()
            return
        }
        lastInterceptedUrl = url
        view.stopLoading()
        if (view.canGoBack()) {
            view.goBack()
        } else {
            loadHome()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
