package com.roozbehzarei.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.roozbehzarei.webview.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale

// constants
private const val WEBSITE = "https://test.mobilityltd.net"

//private const val WEBSITE = "https://mobilityltd.net"
private const val DOMAIN = "mobilityltd.net"
private const val REQUEST_IMAGE_CAPTURE = 1
private const val REQUEST_LOCATION_PERMISSION = 2
private const val REQUEST_MEDIA_PERMISSION = 3

class MainActivity : AppCompatActivity() {

    private var currentPhotoPath: String? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    // Create a global variable to hold the file chooser callback
    private var mUploadMessage: ValueCallback<Array<Uri>>? = null

    @RequiresApi(VERSION_CODES.TIRAMISU)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.root.isEnabled = false

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

        webView.setDownloadListener { url, _, _, mimetype, _ ->
            // if sdk version under 32, request download through download manager
            if (Build.VERSION.SDK_INT < VERSION_CODES.Q) {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimetype)
                val fileName = url.toString().split("/").last().split("%5C").last()
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)
                Log.d("Download", "Downloading: $fileName")
            } else {
                // if sdk version 32 and above, request download through webview download manager
                webView.loadUrl("javascript:window.open('$url')")
            }
//            val request = DownloadManager.Request(Uri.parse(url))
//            request.setMimeType(mimetype)
//            val fileName = url.toString().split("/").last().split("%5C").last()
//            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
//            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//            downloadManager.enqueue(request)
//            Log.d("Download", "Downloading: $fileName")
        }

        fileChooserActivityResultLauncher.launch(intent)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQUEST_IMAGE_CAPTURE
            )
        } else if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), REQUEST_LOCATION_PERMISSION
            )
        } else if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), REQUEST_MEDIA_PERMISSION
            )
        } else {
            loadWebView()
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

    private val fileChooserActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val resultUris: Array<Uri>?
                if (data == null || data.data == null) {
                    // If there is not data, then we may have taken a photo
                    if (currentPhotoPath != null) {
                        val photoFile = File(currentPhotoPath!!)
                        val photoURI: Uri = FileProvider.getUriForFile(
                            this@MainActivity, "com.example.android.fileprovider", photoFile
                        )
                        resultUris = arrayOf(photoURI)
                        currentPhotoPath = null
                    } else {
                        resultUris = null
                    }
                } else {
                    resultUris =
                        WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                }
                mUploadMessage?.onReceiveValue(resultUris)
                mUploadMessage = null
            } else {
                mUploadMessage?.onReceiveValue(null)
                mUploadMessage = null
            }


        }

    private fun loadWebView() {
        webView.loadUrl(WEBSITE)

    }


    private inner class MyWebViewClient : WebViewClient() {

        /**
         * Let [webView] load the [WEBSITE]
         * Otherwise, launch another Activity that handles URLs
         */
        override fun shouldOverrideUrlLoading(
            view: WebView?, request: WebResourceRequest?
        ): Boolean {
            if (request?.url.toString().contains(WEBSITE) || request?.url.toString()
                    .contains(DOMAIN)
            ) {
                if (request?.url.toString().contains(".jpg")) {
                    // handle download through webview download manager
                    return false
                }
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
//            binding.root.isRefreshing = false
        }

        // Hide progress indicator when a webpage is finished loading
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
//            binding.root.isRefreshing = false
//            binding.progressIndicator.visibility = View.INVISIBLE
        }


    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted
                    loadWebView()

                } else {
                    // Permission was denied. Disable the functionality that depends on this permission.
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
                return
            }

            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted
                    loadWebView()
                } else {
                    // Permission was denied. Disable the functionality that depends on this permission.
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
                return
            }

            REQUEST_MEDIA_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted
                    loadWebView()
                } else {
                    // Permission was denied. Disable the functionality that depends on this permission.
                    Toast.makeText(this, "Media permission denied", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Handle other permissions if any
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private inner class MyWebChromeClient : WebChromeClient() {


        // Update the progress of progress indicator when loading a webpage
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
//            binding.progressIndicator.progress = newProgress
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) {
                return
            }
            runOnUiThread {
                if (request.origin.toString().contains(WEBSITE)) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                }
            }
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
                        this@MainActivity, "com.example.android.fileprovider", it
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
        val timeStamp: String =
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
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