
package com.nsboostbd.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
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

    private var activePopupContainer: FrameLayout? = null
    private var activePopupWebView: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val websiteUrl = "https://nsboostbd.com/"
    private val allowedHost = "nsboostbd.com"

    private val fileChooserLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val callback = filePathCallback
                ?: return@registerForActivityResult

            val results =
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data

                    when {
                        data?.clipData != null -> {
                            val clip = data.clipData!!
                            Array(clip.itemCount) {
                                clip.getItemAt(it).uri
                            }
                        }

                        data?.data != null -> {
                            arrayOf(data.data!!)
                        }

                        else -> null
                    }
                } else null

            callback.onReceiveValue(results)
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

        configureCookies()
        setupWebView()
        setupSwipeRefresh()
        setupRetryButton()
        setupBackButton()

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

    // COOKIE SETTINGS

    private fun configureCookies() {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)
    }

    // WEBVIEW SETTINGS

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView) {

        target.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT

            mixedContentMode =
                WebSettings.MIXED_CONTENT_NEVER_ALLOW

            allowFileAccess = false
            allowContentAccess = true

            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)

            loadWithOverviewMode = true
            useWideViewPort = true

            builtInZoomControls = false
            displayZoomControls = false

            mediaPlaybackRequiresUserGesture = true
        }

        target.setBackgroundColor(android.graphics.Color.WHITE)
    }

    // MAIN WEBVIEW

    private fun setupWebView() {

        configureWebView(webView)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                return handleUrl(uri)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {
                return url?.let {
                    handleUrl(Uri.parse(it))
                } ?: false
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = 0
                hideError()
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                binding.progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                if (request?.isForMainFrame == true) {
                    showError()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(
                view: WebView?,
                newProgress: Int
            ) {
                binding.progressBar.progress = newProgress

                binding.progressBar.visibility =
                    if (newProgress >= 100) View.GONE
                    else View.VISIBLE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {

                if (!isUserGesture) return false

                closePopupWindow(false)

                val popup = WebView(this@MainActivity)
                configureWebView(popup)

                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(popup, true)

                val container = FrameLayout(this@MainActivity)
                container.setBackgroundColor(
                    android.graphics.Color.WHITE
                )

                container.addView(
                    popup,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                activePopupWebView = popup
                activePopupContainer = container

                binding.root.addView(container)

                popup.webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false
                        return handlePopupUrl(uri)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        url: String?
                    ): Boolean {
                        return url?.let {
                            handlePopupUrl(Uri.parse(it))
                        } ?: false
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        binding.progressBar.visibility = View.VISIBLE
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?
                    ) {
                        super.onPageFinished(view, url)

                        binding.progressBar.visibility = View.GONE
                        CookieManager.getInstance().flush()
                    }
                }

                popup.webChromeClient = object : WebChromeClient() {

                    override fun onCloseWindow(window: WebView?) {
                        closePopupWindow(true)
                    }

                    override fun onProgressChanged(
                        view: WebView?,
                        newProgress: Int
                    ) {
                        binding.progressBar.progress = newProgress

                        binding.progressBar.visibility =
                            if (newProgress >= 100) View.GONE
                            else View.VISIBLE
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        callback: ValueCallback<Array<Uri>>?,
                        params: FileChooserParams?
                    ): Boolean {
                        return openFileChooser(callback, params)
                    }
                }

                val transport =
                    resultMsg?.obj as? WebView.WebViewTransport

                if (transport == null) {
                    closePopupWindow(false)
                    return false
                }

                transport.webView = popup
                resultMsg.sendToTarget()

                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                return openFileChooser(callback, params)
            }
        }

        setupDownloads()
    }

    // GOOGLE AUTHENTICATION URL

    private fun isGoogleAuthUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == "accounts.google.com"
    }

    // URL HANDLING

    private fun handleUrl(uri: Uri): Boolean {

        val scheme = uri.scheme?.lowercase() ?: return true

        if (scheme == "https" || scheme == "http") {

            if (isGoogleAuthUrl(uri)) {
                closePopupWindow(false)
                return openExternal(uri)
            }

            val host = uri.host?.lowercase() ?: ""

            if (
                host == allowedHost ||
                host.endsWith(".$allowedHost") ||
                host.endsWith(".google.com") ||
                host.endsWith(".googleapis.com") ||
                host.endsWith(".gstatic.com")
            ) {
                return false
            }

            return openExternal(uri)
        }

        return openExternal(uri)
    }

    private fun handlePopupUrl(uri: Uri): Boolean {

        val scheme = uri.scheme?.lowercase() ?: return true

        if (scheme == "https" || scheme == "http") {

            if (isGoogleAuthUrl(uri)) {
                closePopupWindow(false)
                return openExternal(uri)
            }

            val host = uri.host?.lowercase() ?: ""

            if (
                host == allowedHost ||
                host.endsWith(".$allowedHost") ||
                host.endsWith(".google.com") ||
                host.endsWith(".googleapis.com") ||
                host.endsWith(".gstatic.com")
            ) {
                return false
            }

            return openExternal(uri)
        }

        return openExternal(uri)
    }

    // OPEN EXTERNAL LINKS

    private fun openExternal(uri: Uri): Boolean {

        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            true

        } catch (e: ActivityNotFoundException) {

            Toast.makeText(
                this,
                "No application found to open this link",
                Toast.LENGTH_SHORT
            ).show()

            true
        }
    }

    // POPUP MANAGEMENT

    private fun closePopupWindow(reloadMain: Boolean = false) {

        val popup = activePopupWebView
        val container = activePopupContainer

        activePopupWebView = null
        activePopupContainer = null

        container?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        popup?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }

        CookieManager.getInstance().flush()
        binding.progressBar.visibility = View.GONE

        if (reloadMain) {
            webView.reload()
        }
    }

    // FILE UPLOAD

    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {

        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        return try {

            val intent = params?.createIntent()
                ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

            fileChooserLauncher.launch(intent)
            true

        } catch (e: Exception) {

            filePathCallback = null

            Toast.makeText(
                this,
                "Cannot open file chooser",
                Toast.LENGTH_SHORT
            ).show()

            false
        }
    }

    // DOWNLOAD

    private fun setupDownloads() {
        webView.setDownloadListener(
            DownloadListener { url, _, _, _, _ ->
                openExternal(Uri.parse(url))
            }
        )
    }

    // SWIPE REFRESH

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

    // RETRY BUTTON

    private fun setupRetryButton() {

        binding.btnRetry.setOnClickListener {

            if (isNetworkAvailable()) {
                hideError()
                loadWebsite()
            } else {
                Toast.makeText(
                    this,
                    "No internet connection",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // BACK BUTTON

    private fun setupBackButton() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    val popup = activePopupWebView

                    if (popup != null) {

                        if (popup.canGoBack()) {
                            popup.goBack()
                        } else {
                            closePopupWindow(true)
                        }

                    } else if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    // NETWORK

    private fun isNetworkAvailable(): Boolean {

        val manager =
            getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            val network = manager.activeNetwork ?: return false

            val capabilities =
                manager.getNetworkCapabilities(network) ?: return false

            return capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )

        } else {

            @Suppress("DEPRECATION")
            val info = manager.activeNetworkInfo

            return info != null && info.isConnected
        }
    }

    // ERROR SCREEN

    private fun showError() {

        webView.visibility = View.GONE
        swipeRefresh.isEnabled = false

        binding.errorLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE

        swipeRefresh.isRefreshing = false
    }

    private fun hideError() {

        binding.errorLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        swipeRefresh.isEnabled = true
    }

    // LOAD WEBSITE

    private fun loadWebsite() {
        webView.loadUrl(websiteUrl)
    }

    // SAVE STATE

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    // LIFECYCLE

    override fun onPause() {

        activePopupWebView?.onPause()
        webView.onPause()

        CookieManager.getInstance().flush()

        super.onPause()
    }

    override fun onResume() {

        super.onResume()

        activePopupWebView?.onResume()
        webView.onResume()
    }

    override fun onDestroy() {

        filePathCallback?.onReceiveValue(null)
        filePathCallback = null

        closePopupWindow(false)

        webView.stopLoading()
        webView.removeAllViews()
        webView.destroy()

        super.onDestroy()
    }
}
