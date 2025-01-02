package com.example.cameraxapp

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.cameraxapp.databinding.ActivityMainBinding
import java.io.File
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import org.opencv.android.OpenCVLoader


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var clefSpinner: Spinner
    private lateinit var keySignatureSpinner: Spinner
    private lateinit var captureButton: Button

    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!")
        } else {
            Log.d(TAG, "OpenCV initialization succeeded.")
        }

        // Initialize UI components
        clefSpinner = findViewById(R.id.clefSpinner)
        keySignatureSpinner = findViewById(R.id.keySignatureSpinner)
        captureButton = findViewById(R.id.captureButton)

        // Setup the clef dropdown
        val clefOptions = arrayOf("Treble Clef", "Bass Clef", "C Clef")
        val clefAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, clefOptions)
        clefAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        clefSpinner.adapter = clefAdapter

        // Setup the key signature dropdown (Example: C Major, G Major, etc.)
        val keyOptions = arrayOf(
            "F♯ major/F♯ minor",
            "C♯ major/C♯ minor",
            "G♯ major/G♯ minor",
            "D major/B minor",
            "A major/F♯ minor",
            "E major/C♯ minor",
            "B major/G♯ minor",
            "F major/D minor",
            "B♭ major/G minor",
            "E♭ major/C minor",
            "A♭ major/F minor",
            "D♭ major/B♭ minor",
            "G♭ major/E♭ minor",
            "C♭ major/A♭ minor",
            "F♭ major/D♭ minor",
            "C major/A minor"
        )

        val keyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, keyOptions)
        keyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        keySignatureSpinner.adapter = keyAdapter

        // Handle the Capture button click event
        captureButton.setOnClickListener {
            // Capture the image
            captureImage()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // ImageCapture use case
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val file = File(externalMediaDirs.first(), "captured_image.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(file)
                    Log.d(TAG, "Image captured: $savedUri")

                    // Pass the captured image URI to the review screen
                    val intent = Intent(this@MainActivity, ReviewActivity::class.java).apply {
                        putExtra("image_uri", savedUri.toString())
                    }
                    startActivity(intent)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturing image", exception)
                }
            }
        )
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )
        private const val TAG = "MainActivity"
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }
}
