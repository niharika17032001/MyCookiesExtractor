package com.example.mycookiesextractor

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.ConsoleMessage // Import ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: Button
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button
    private lateinit var refreshButton: Button
    private lateinit var saveCookiesButton: Button
    private lateinit var statusTextView: TextView

    // Initial URL to load when the app starts
    private val INITIAL_URL = "https://google.com" // Good for testing cookies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)
        backButton = findViewById(R.id.backButton)
        forwardButton = findViewById(R.id.forwardButton)
        refreshButton = findViewById(R.id.refreshButton)
        saveCookiesButton = findViewById(R.id.saveCookiesButton)
        statusTextView = findViewById(R.id.statusTextView)

        // --- Setup WebView ---
        setupWebView()

        // --- Setup Button Listeners ---
        setupButtonListeners()

        // --- Initial Load ---
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            urlEditText.setText(webView.url)
        } else {
            urlEditText.setText(INITIAL_URL)
            webView.loadUrl(INITIAL_URL)
        }

        // --- AUTOMATIC COOKIE EXTRACTION ON APP LAUNCH ---
        extractAndSaveCookies()
    }

    private fun setupWebView() {
        // Essential WebSettings for modern websites
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true // Enable JavaScript
        webSettings.domStorageEnabled = true // Enable HTML5 Local Storage (crucial for many sites)
        webSettings.databaseEnabled = true // Enable HTML5 database storage
        // webSettings.setAppCacheEnabled(true) // REMOVED: Deprecated and removed in recent APIs
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT // Use default caching logic
        webSettings.allowFileAccess = true // Allow access to file URLs
        webSettings.allowContentAccess = true // Allow content provider access
        webSettings.setSupportZoom(true) // Enable zoom functionality
        webSettings.builtInZoomControls = true // Show zoom controls
        webSettings.displayZoomControls = false // Hide zoom controls (optional, keeps UI cleaner)
        webSettings.loadWithOverviewMode = true // Zoom out if the content does not fit
        webSettings.useWideViewPort = true // Use a wide viewport (for desktop-like rendering)

        // Configure CookieManager for persistence
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true) // Crucial: WebView must accept cookies
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Set WebViewClient to keep navigation within your app
        webView.webViewClient = MyWebViewClient(this)

        // Set WebChromeClient for progress updates, console messages, etc.
        webView.webChromeClient = MyWebChromeClient(this)

        // Handle URL input (e.g., pressing enter)
        urlEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                goButton.performClick()
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun setupButtonListeners() {
        goButton.setOnClickListener {
            var url = urlEditText.text.toString().trim()
            if (url.isNotBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                webView.loadUrl(url)
            }
        }

        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        forwardButton.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }

        refreshButton.setOnClickListener {
            webView.reload()
        }

        saveCookiesButton.setOnClickListener {
            extractAndSaveCookies()
        }
    }

    private fun extractAndSaveCookies() {
        val currentUrl = webView.url ?: INITIAL_URL

        lifecycleScope.launch(Dispatchers.IO) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()

            val cookiesString = cookieManager.getCookie(currentUrl)

            if (cookiesString != null && cookiesString.isNotBlank()) {
                val fileName = "extracted_cookies_for_app_${packageName.replace(".", "_")}.txt"
                val file = File(filesDir, fileName)

                withContext(Dispatchers.Main) {
                    statusTextView.text = "Saving cookies to ${file.absolutePath}..."
                }

                try {
                    FileWriter(file).use { writer ->
                        writer.write("--- Cookies for URL: ${currentUrl} ---\n\n")
                        writer.write(cookiesString.replace(";", ";\n"))
                        writer.write("\n\n--- Raw Cookie String ---\n")
                        writer.write(cookiesString)
                        writer.write("\n\n-------------------------\n")
                    }
                    withContext(Dispatchers.Main) {
                        statusTextView.text = "Cookies extracted & saved to:\n${file.absolutePath}\n\nExample: ${cookiesString.split(";")[0]}"
                    }
                    Log.i("CookieSave", "Cookies saved to: ${file.absolutePath}")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusTextView.text = "Error saving cookies: ${e.message}"
                    }
                    Log.e("CookieSave", "Error saving cookies", e)
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "No cookies found for ${currentUrl} to extract."
                }
                Log.d("CookieSave", "No cookies found for ${currentUrl}")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        Log.d("CookiePersistence", "Cookies flushed on onPause.")
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
        Log.d("CookiePersistence", "Cookies flushed on onStop.")
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        Log.d("WebViewLifecycle", "WebView destroyed.")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        Log.d("WebViewState", "WebView state saved.")
    }

    private inner class MyWebViewClient(private val context: Context) : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            url?.let {
                urlEditText.setText(it)
            }
            (context as? MainActivity)?.statusTextView?.text = "Page loaded: ${url ?: "N/A"}"
            Log.d("WebViewClient", "Page finished loading: ${url}")
        }

        override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
            super.onReceivedError(view, request, error)
            val errorMessage = "Error loading: ${error?.description ?: "Unknown error"}"
            (context as? MainActivity)?.statusTextView?.text = errorMessage
            Log.e("WebViewClient", errorMessage)
        }
    }

    private inner class MyWebChromeClient(private val context: Context) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            (context as? MainActivity)?.statusTextView?.text = "Loading: ${newProgress}%"
            if (newProgress == 100) {
                (context as? MainActivity)?.statusTextView?.text = "Page loaded."
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            supportActionBar?.title = title ?: getString(R.string.app_name)
        }

        // FIX: Changed return type from Unit to Boolean and return true/false
        // FIX: Imported ConsoleMessage
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
            return super.onConsoleMessage(consoleMessage) // Call super and return its result
            // Alternatively, return 'true' if you fully handle the message and don't want it logged by WebView's default handler.
            // return true
        }
    }
}