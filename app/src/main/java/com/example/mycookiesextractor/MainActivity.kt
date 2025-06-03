package com.example.mycookiesextractor

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.OutputStreamWriter // IMPORTANT: Correct import for OutputStreamWriter

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: Button
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button
    private lateinit var refreshButton: Button
    private lateinit var saveCookiesButton: Button
    private lateinit var postCookiesButton: Button
    private lateinit var statusTextView: TextView

    // Initial URL to load when the app starts
    private val INITIAL_URL = "https://google.com"

    // YOUR API ENDPOINT
    private val API_ENDPOINT = "https://amit0987-cookies-recever-api.hf.space/save-cookies"

    // OkHttpClient instance for making API calls
    private val httpClient = OkHttpClient()

    // SAF Launcher for creating a document (saving a file)
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain") // MIME type for the file you want to save
    ) { uri: Uri? ->
        // This callback is executed when the user has selected a file location or cancelled
        uri?.let { documentUri ->
            // User selected a URI, now proceed to extract and save cookies to this URI
            saveCookiesToUri(documentUri)
        } ?: run {
            // User cancelled the file picker
            statusTextView.text = "File saving cancelled by user."
            Toast.makeText(this, "File saving cancelled.", Toast.LENGTH_SHORT).show()
            Log.d("CookieSave", "User cancelled file creation.")
        }
    }


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
        postCookiesButton = findViewById(R.id.postCookiesButton)
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
        // Removed explicit permission checks on launch as SAF handles it differently.
        // If you need to auto-save, consider app-specific internal storage or
        // making the user trigger the save via a button.
    }

    private fun setupWebView() {
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = MyWebViewClient(this)
        webView.webChromeClient = MyWebChromeClient(this)

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
            // Trigger the SAF file picker to let the user choose where to save the file
            val fileName = "cookies_${System.currentTimeMillis()}.txt"
            createDocumentLauncher.launch(fileName)
        }

        postCookiesButton.setOnClickListener {
            extractAndPostCookiesToApi()
        }
    }

    // --- SAF-based Cookie Saving Logic ---
    private fun saveCookiesToUri(documentUri: Uri) {
        val currentUrl = webView.url ?: INITIAL_URL

        lifecycleScope.launch(Dispatchers.IO) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()

            val cookiesString = cookieManager.getCookie(currentUrl)

            if (cookiesString != null && cookiesString.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "Attempting to save cookies to selected location..."
                }

                try {
                    // Use ContentResolver to open an OutputStream from the URI
                    contentResolver.openOutputStream(documentUri)?.use { outputStream ->
                        // Correctly bridge OutputStream to Writer using OutputStreamWriter
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write("--- Cookies for URL: ${currentUrl} ---\n\n")
                            writer.write(cookiesString.replace(";", ";\n"))
                            writer.write("\n\n--- Raw Cookie String ---\n")
                            writer.write(cookiesString)
                            writer.write("\n\n-------------------------\n")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        statusTextView.text = "Cookies extracted & saved to:\n${documentUri.path}\n\nExample: ${cookiesString.split(";")[0]}"
                        Toast.makeText(this@MainActivity, "Cookies saved successfully!", Toast.LENGTH_SHORT).show()
                    }
                    Log.i("CookieSave", "Cookies saved to: $documentUri")
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusTextView.text = "Error saving cookies via SAF: ${e.message}"
                        Toast.makeText(this@MainActivity, "Error saving cookies: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    Log.e("CookieSave", "Error saving cookies via SAF", e)
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "No cookies found for ${currentUrl} to extract."
                    Toast.makeText(this@MainActivity, "No cookies found.", Toast.LENGTH_SHORT).show()
                }
                Log.d("CookieSave", "No cookies found for ${currentUrl}")
            }
        }
    }

    private fun extractAndPostCookiesToApi() {
        val currentUrl = webView.url ?: INITIAL_URL

        lifecycleScope.launch(Dispatchers.IO) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()

            val cookiesString = cookieManager.getCookie(currentUrl)

            if (cookiesString != null && cookiesString.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "Parsing and posting cookies to API for ${currentUrl}..."
                }

                val cookieMap = cookiesString.split(";")
                    .mapNotNull { it.trim().split("=", limit = 2) }
                    .filter { it.size == 2 }
                    .associate { (key, value) -> key.trim() to value.trim() }

                val session = cookieMap["session"] ?: ""
                val user = cookieMap["user"] ?: ""
                val expires = cookieMap["expires"] ?: ""

                try {
                    val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val requestBodyJson = """
                        {
                            "data": {
                                "session": "$session",
                                "user": "$user",
                                "expires": "$expires"
                            },
                            "raw_cookies_string": "$cookiesString",
                            "source_url": "$currentUrl"
                        }
                    """.trimIndent()

                    Log.d("CookiePostDebug", "Request Body JSON: $requestBodyJson") // Debugging: Log outgoing JSON

                    val requestBody = requestBodyJson.toRequestBody(jsonMediaType)

                    val request = Request.Builder()
                        .url(API_ENDPOINT)
                        .post(requestBody)
                        .build()

                    val response = httpClient.newCall(request).execute()

                    withContext(Dispatchers.Main) {
                        val responseBodyString = response.body?.string() // Consume once
                        if (response.isSuccessful) {
                            statusTextView.text = "Cookies POSTED successfully!\nResponse: ${responseBodyString?.take(100)}..."
                            Log.i("CookiePost", "API POST Successful. Code: ${response.code}, Response: $responseBodyString")
                            Toast.makeText(this@MainActivity, "Cookies posted to API!", Toast.LENGTH_SHORT).show()
                        } else {
                            statusTextView.text = "Failed to POST cookies. Code: ${response.code}\nMessage: ${response.message}"
                            Log.e("CookiePost", "API POST Failed. Code: ${response.code}, Message: ${response.message}, Body: $responseBodyString")
                            Toast.makeText(this@MainActivity, "Failed to post cookies.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        statusTextView.text = "Network error posting cookies: ${e.message}"
                        Toast.makeText(this@MainActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    Log.e("CookiePost", "Network error during POST", e)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusTextView.text = "An unexpected error occurred: ${e.message}"
                        Toast.makeText(this@MainActivity, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    Log.e("CookiePost", "Unexpected error during POST", e)
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "No cookies found for ${currentUrl} to POST to API."
                    Toast.makeText(this@MainActivity, "No cookies to post.", Toast.LENGTH_SHORT).show()
                }
                Log.d("CookiePost", "No cookies found for ${currentUrl}")
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

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
            return super.onConsoleMessage(consoleMessage)
        }
    }
}