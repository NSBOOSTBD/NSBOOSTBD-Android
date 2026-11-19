package com.nsboostbd.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nsboostbd.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: FrameLayout

    private var activePopupContainer: FrameLayout? = null
    private var activePopupWebView: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val websiteUrl = "https://nsboostbd.com/"
    private val chromeUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@registerForActivityResult
        val results = if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NSBOOSTBD)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        swipeRefresh = binding.swipeRefresh
        progressBar = binding.progressBar
        errorLayout = binding.errorLayout

        setupWebView()
        setupSwipeRefresh()
        setupErrorRetry()
        setupBackPress()

        if (isNetworkAvailable()) {
            if (savedInstanceState == null) {
                loadWebsite()
            } else {
                webView.restoreState(savedInstanceState)
            }
        } else {
            showError()
        }
    }

    private fun configureWebViewSettings(targetWebView: WebView, cookieManager: CookieManager) {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(targetWebView, true)

        @SuppressLint("SetJavaScriptEnabled")
        val settings = targetWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.userAgentString = chromeUserAgent
    }

    private fun closePopupWindow(reloadMain: Boolean = true) {
        val container = activePopupContainer
        val popup = activePopupWebView

        activePopupContainer = null
        activePopupWebView = null

        progressBar.visibility = View.GONE
        if (container != null && container.parent != null) {
            (container.parent as? ViewGroup)?.removeView(container)
        }
        popup?.apply {
            stopLoading()
            destroy()
        }
        CookieManager.getInstance().flush()
        if (reloadMain) {
            webView.reload()
        }
    }

    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        configureWebViewSettings(webView, cookieManager)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrl(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return url?.let { handleUrl(it) } ?: false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
                hideError()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                cookieManager.flush()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    showError()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                closePopupWindow(reloadMain = false)

                val popupWebView = WebView(this@MainActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                configureWebViewSettings(popupWebView, cookieManager)

                val popupContainer = FrameLayout(this@MainActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.WHITE)
                    addView(popupWebView)
                }

                activePopupWebView = popupWebView
                activePopupContainer = popupContainer
                binding.root.addView(popupContainer)

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        closePopupWindow(reloadMain = true)
                    }

                    override fun onProgressChanged(v: WebView?, newProgress: Int) {
                        progressBar.progress = newProgress
                        progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                    }
                }

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        req: WebResourceRequest?
                    ): Boolean {
                        val popupUrl = req?.url?.toString() ?: return false
                        return handlePopupUrl(popupUrl)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                        val popupUrl = url ?: return false
                        return handlePopupUrl(popupUrl)
                    }

                    override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(v, url, favicon)
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = 10
                    }

                    override fun onPageFinished(v: WebView?, url: String?) {
                        super.onPageFinished(v, url)
                        progressBar.visibility = View.GONE
                        cookieManager.flush()
                    }
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                return try {
                    if (intent != null) {
                        fileChooserLauncher.launch(intent)
                        true
                    } else {
                        false
                    }
                } catch (e: ActivityNotFoundException) {
                    this@MainActivity.filePathCallback = null
                    Toast.makeText(this@MainActivity, "Cannot open file chooser", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        webView.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to download file", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun handlePopupUrl(popupUrl: String): Boolean {
        val uri = Uri.parse(popupUrl)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: ""

        if (scheme == "http" || scheme == "https") {
            if (host == "nsboostbd.com" || host.endsWith(".nsboostbd.com")) {
                closePopupWindow(reloadMain = false)
                webView.loadUrl(popupUrl)
                return true
            }
            return false
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            closePopupWindow(reloadMain = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isAllowedInternalHost(host: String): Boolean {
        if (host == "nsboostbd.com" || host.endsWith(".nsboostbd.com")) {
            return true
        }
        val googleDomains = listOf(
            "accounts.google.com",
            "google.com",
            "googleapis.com",
            "gstatic.com",
            "googleusercontent.com",
            "youtube.com",
            "ggpht.com"
        )
        return googleDomains.any { host == it || host.endsWith(".$it") }
    }

    private fun handleUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            val host = uri.host?.lowercase() ?: return false
            if (isAllowedInternalHost(host)) {
                return false
            }
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary)
        )
        swipeRefresh.setOnRefreshListener {
            if (isNetworkAvailable()) {
                webView.reload()
            } else {
                swipeRefresh.isRefreshing = false
                showError()
            }
        }
    }

    private fun setupErrorRetry() {
        binding.btnRetry.setOnClickListener {
            if (isNetworkAvailable()) {
                hideError()
                loadWebsite()
            } else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val popup = activePopupWebView
                if (popup != null) {
                    if (popup.canGoBack()) {
                        popup.goBack()
                    } else {
                        closePopupWindow(reloadMain = true)
                    }
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun loadWebsite() {
        webView.loadUrl(websiteUrl)
    }

    private fun showError() {
        webView.visibility = View.GONE
        swipeRefresh.isEnabled = false
        errorLayout.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun hideError() {
        errorLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        swipeRefresh.isEnabled = true
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val popup = activePopupWebView
            if (popup != null) {
                if (popup.canGoBack()) {
                    popup.goBack()
                } else {
                    closePopupWindow(reloadMain = true)
                }
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        activePopupWebView?.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        activePopupWebView?.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        closePopupWindow(reloadMain = false)
        webView.destroy()
        super.onDestroy()
    }
}
