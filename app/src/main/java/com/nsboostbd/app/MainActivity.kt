
package com.nsboostbd.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val homeUrl = "https://nsboostbd.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = FrameLayout(this)
        webView = WebView(this)

        progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        )
        progressBar.max = 100

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.setBackgroundColor(Color.WHITE)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return true
                return url.scheme != "https" && url.scheme != "http"
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(
                view: WebView?,
                newProgress: Int
            ) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress < 100) View.VISIBLE
                    else View.GONE
            }
        }

        layout.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        layout.addView(
            progressBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                8
            )
        )

        setContentView(layout)

        if (savedInstanceState == null) {
            webView.loadUrl(homeUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }
}
