package com.roozbehzarei.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.roozbehzarei.webview.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale

// The URL of the website to be loaded
private const val WEBSITE = "https://mobilityltd.net"

class MainActivity : AppCompatActivity() {

    private var currentPhotoPath: String? = null;

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    // Create a global variable to hold the file chooser callback
    private var mUploadMessage: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        /**
         * Define and configure [webView]
         */
        webView = binding.webView
        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = MyWebChromeClient()
        with(webView.settings) {
            // Enable JavaScript execution
            javaScriptEnabled = true
            // Enable DOM storage API
            domStorageEnabled = true
            // Disable support for zooming using webView's on-screen zoom controls and gestures
            setSupportZoom(false)
            // ask for permission to access the camera
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true

        }
        // If dark theme is turned on, automatically render all web contents using a dark theme
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
                }

                Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
                }
            }
        }


// Use this to launch the file chooser
        fileChooserActivityResultLauncher.launch(intent)

        // Start loading the given website URL
        webView.loadUrl(WEBSITE)


        // Define Swipe-to-refresh behavior
        binding.root.setOnRefreshListener {
            if (webView.url == null) {
                webView.loadUrl(WEBSITE)
            } else {
//                webView.reload()
            }
        }

        // Theme Swipe-to-refresh layout
        val spinnerTypedValue = TypedValue()
        theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimary, spinnerTypedValue, true
        )
        val spinnerColor = spinnerTypedValue.resourceId
        binding.root.setColorSchemeResources(spinnerColor)

        val backgroundTypedValue = TypedValue()
        theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimaryContainer, backgroundTypedValue, true
        )
        val backgroundColor = backgroundTypedValue.resourceId
        binding.root.setProgressBackgroundColorSchemeResource(backgroundColor)

        /**
         * Disable Swipe-to-refresh if [webView] is scrolling
         */
        webView.viewTreeObserver.addOnScrollChangedListener {
            binding.root.isEnabled = false
        }

        /**
         * When navigating back, close the app if there's no previous webpage for [webView] to go back to
         */
        val mCallback = onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
        mCallback.isEnabled = true

        setContentView(binding.root)
    }

private val fileChooserActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val data: Intent? = result.data
        val resultUris: Array<Uri>?
        if (data == null || data.data == null) {
            // If there is not data, then we may have taken a photo
            if (currentPhotoPath != null) {
                val photoFile = File(currentPhotoPath!!)
                val photoURI: Uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "com.example.android.fileprovider",
                    photoFile
                )
                resultUris = arrayOf(photoURI)
                currentPhotoPath = null
            } else {
                resultUris = null
            }
        } else {
            resultUris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
        }
        mUploadMessage?.onReceiveValue(resultUris)
        mUploadMessage = null
    } else {
        mUploadMessage?.onReceiveValue(null)
        mUploadMessage = null
    }
}


    private inner class MyWebViewClient : WebViewClient() {

        /**
         * Let [webView] load the [WEBSITE]
         * Otherwise, launch another Activity that handles URLs
         */
        override fun shouldOverrideUrlLoading(
            view: WebView?, request: WebResourceRequest?
        ): Boolean {
            if (request?.url.toString().contains(WEBSITE)) {
                return false
            }
            Intent(Intent.ACTION_VIEW, request?.url).apply {
                startActivity(this)
            }
            return true
        }

        // Show progress indicator when a webpage is being loaded
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            binding.webView.visibility = View.VISIBLE
            binding.errorLayout.visibility = View.GONE
            binding.progressIndicator.visibility = View.VISIBLE
        }

        // Hide progress indicator when a webpage is finished loading
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            binding.root.isRefreshing = false
            binding.progressIndicator.visibility = View.INVISIBLE
        }

        // Show error screen if loading a webpage has failed
        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            binding.webView.visibility = View.GONE
            binding.errorLayout.visibility = View.VISIBLE
            binding.root.isEnabled = false
            binding.retryButton.setOnClickListener {
                if (view?.url.isNullOrEmpty()) {
                    view?.loadUrl(WEBSITE)
                } else {
                    view?.reload()
                }
            }
        }

    }

    private inner class MyWebChromeClient : WebChromeClient() {

        // Update the progress of progress indicator when loading a webpage
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            binding.progressIndicator.progress = newProgress
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.grant(request.resources)
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
// Ensure there's no existing callback
            if (mUploadMessage != null) {
                mUploadMessage?.onReceiveValue(null)
                mUploadMessage = null
            }

            mUploadMessage = filePathCallback
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                // Create a file to store the image
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "com.example.android.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    fileChooserActivityResultLauncher.launch(takePictureIntent)
                }
            }

            return true
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

}