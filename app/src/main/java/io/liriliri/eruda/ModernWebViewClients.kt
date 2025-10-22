package io.liriliri.eruda

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.*
import androidx.core.content.ContextCompat.startActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ModernWebViewClient(
    private val onPageStarted: (url: String) -> Unit,
    private val onPageFinished: (url: String, title: String?) -> Unit
) : WebViewClient() {
    
    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        
        return when {
            url.isHttpUrl() -> false
            else -> handleNonHttpUrl(view, url)
        }
    }
    
    private fun handleNonHttpUrl(view: WebView, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            view.context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle URL: $url", e)
            true
        }
    }
    
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (!request.isForMainFrame) return null
        
        val url = request.url.toString()
        if (!url.isHttpUrl()) return null
        
        // Only intercept if we need to remove CSP headers
        return interceptForCsp(url, request)
    }
    
    private fun interceptForCsp(url: String, request: WebResourceRequest): WebResourceResponse? {
        val contentType = request.requestHeaders["content-type"]
        if (contentType == "application/x-www-form-urlencoded") {
            return null
        }
        
        Log.d(TAG, "Checking CSP for: $url")
        
        var headers = request.requestHeaders.toHeaders()
        val cookie = CookieManager.getInstance().getCookie(url)
        if (cookie != null) {
            headers = (headers.toMap() + ("cookie" to cookie)).toHeaders()
        }
        
        val httpRequest = Request.Builder()
            .url(url)
            .headers(headers)
            .build()
        
        return try {
            val response = okHttpClient.newCall(httpRequest).execute()
            
            // Only intercept if CSP header is present
            if (response.headers["content-security-policy"] == null) {
                response.close()
                return null
            }
            
            Log.i(TAG, "Removing CSP from: $url")
            
            val filteredHeaders = response.headers.toMap()
                .filterKeys { it.lowercase() != "content-security-policy" }
            
            WebResourceResponse(
                response.header("content-type", "text/html")?.split(";")?.first(),
                response.header("content-encoding", "utf-8"),
                response.code,
                response.message,
                filteredHeaders,
                response.body?.byteStream()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error intercepting request", e)
            null
        }
    }
    
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }
    
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished(url, view.title)
    }
    
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            Log.e(TAG, "Page load error: ${error.description}")
        }
    }
    
    companion object {
        private const val TAG = "ModernWebViewClient"
    }
}

class ModernWebChromeClient(
    private val onProgressChanged: (progress: Int) -> Unit,
    private val onReceivedIcon: (icon: Bitmap) -> Unit,
    private val onShowFileChooser: (
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ) -> Boolean
) : WebChromeClient() {
    
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }
    
    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
        super.onReceivedIcon(view, icon)
        onReceivedIcon(icon)
    }
    
    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return onShowFileChooser(filePathCallback, fileChooserParams)
    }
    
    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        Log.d(TAG, "Page title: $title")
    }
    
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        Log.d(
            TAG, 
            "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
        )
        return true
    }
    
    companion object {
        private const val TAG = "ModernWebChromeClient"
    }
}
