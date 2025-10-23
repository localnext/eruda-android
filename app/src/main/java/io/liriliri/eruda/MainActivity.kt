package io.liriliri.eruda

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.liriliri.eruda.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        filePathCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        filePathCallback = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupWebView()
        setupToolbar()
        observeViewModel()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                
                // Modern features
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportMultipleWindows(false)
                
                // Security
                allowFileAccess = false
                allowContentAccess = false
                
                // Performance
                setRenderPriority(WebSettings.RenderPriority.HIGH)
            }
            
            // Dark mode support
            setupDarkMode()
            
            webViewClient = ModernWebViewClient(
                onPageStarted = { url ->
                    viewModel.onPageStarted(url)
                    binding.webIcon.setImageResource(R.drawable.tool)
                },
                onPageFinished = { url, title ->
                    viewModel.onPageFinished(url, title)
                    injectEruda()
                }
            )
            
            webChromeClient = ModernWebChromeClient(
                onProgressChanged = { progress ->
                    viewModel.updateProgress(progress)
                },
                onReceivedIcon = { icon ->
                    binding.webIcon.setImageBitmap(icon)
                },
                onShowFileChooser = { callback, params ->
                    handleFileChooser(callback, params)
                }
            )
            
            loadUrl("https://github.com/liriliri/eruda")
        }
    }
    
    private fun setupDarkMode() {
        val settings = binding.webView.settings
        if (resources.getString(R.string.mode) == "night") {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
            }
        }
    }
    
    private fun setupToolbar() {
        with(binding) {
            textUrl.apply {
                setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_GO || 
                        (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                        handleUrlInput()
                        true
                    } else {
                        false
                    }
                }
                
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        setText(webView.url)
                        setSelection(text.length)
                        btnStart.setImageResource(R.drawable.arrow_right)
                    } else {
                        setText(webView.title)
                        btnStart.setImageResource(R.drawable.refresh)
                    }
                }
            }
            
            btnStart.setOnClickListener {
                if (textUrl.hasFocus()) {
                    handleUrlInput()
                } else {
                    webView.reload()
                }
            }
            
            goBack.setOnClickListener {
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
            
            goForward.setOnClickListener {
                if (webView.canGoForward()) {
                    webView.goForward()
                }
            }
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }
    
    private fun updateUI(state: BrowserUiState) {
        with(binding) {
            progressBar.progress = state.progress
            progressBar.visibility = if (state.isLoading) 
                android.view.View.VISIBLE 
            else 
                android.view.View.INVISIBLE
            
            if (!textUrl.hasFocus()) {
                textUrl.setText(state.title ?: "Loading...")
            }
            
            // Update navigation state
            val canGoBack = webView.canGoBack()
            val canGoForward = webView.canGoForward()
            
            goBack.isEnabled = canGoBack
            goForward.isEnabled = canGoForward
            goBack.alpha = if (canGoBack) 1.0f else 0.4f
            goForward.alpha = if (canGoForward) 1.0f else 0.4f
        }
    }
    
    private fun handleUrlInput() {
        val input = binding.textUrl.text.toString()
        val processedUrl = viewModel.processUrlInput(input)
        binding.webView.loadUrl(processedUrl)
        binding.textUrl.clearFocus()
        hideKeyboard()
    }
    
    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
    
    private fun injectEruda() {
        val script = """
            (function () {
                if (window.eruda) return;
                var define;
                if (window.define) {
                    define = window.define;
                    window.define = null;
                }
                var script = document.createElement('script'); 
                script.src = '//cdn.jsdelivr.net/npm/eruda'; 
                document.body.appendChild(script); 
                script.onload = function () { 
                    eruda.init();
                    if (define) {
                        window.define = define;
                    }
                }
            })();
        """.trimIndent()
        
        binding.webView.evaluateJavascript(script, null)
    }
    
    private fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        
        return try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (e: Exception) {
            Log.e(TAG, "File chooser error", e)
            filePathCallback = null
            false
        }
    }
    
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
    
    companion object {
        private const val TAG = "Eruda.MainActivity"
    }
}

// Extension functions
fun String.isHttpUrl(): Boolean = startsWith("http://") || startsWith("https://")

fun String.mayBeUrl(): Boolean {
    val domains = arrayOf(".com", ".io", ".me", ".org", ".net", ".tv", ".cn", ".co", ".app")
    return domains.any { contains(it, ignoreCase = true) } && !contains(" ")
}

fun String.toSearchUrl(): String {
    val encoded = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
    return "https://www.google.com/search?q=$encoded"
}

fun String.normalizeUrl(): String = when {
    isHttpUrl() -> this
    mayBeUrl() -> if (startsWith("www.")) "https://$this" else "https://www.$this"
    else -> toSearchUrl()
}
