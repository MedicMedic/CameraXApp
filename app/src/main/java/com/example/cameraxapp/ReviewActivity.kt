package com.example.cameraxapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.example.cameraxapp.databinding.ActivityReviewBinding
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Core
import org.opencv.core.MatOfPoint2f
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import com.example.cameraxapp.MainActivity
import com.example.cameraxapp.R
import org.opencv.core.Scalar
import android.media.ExifInterface
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi


class ReviewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var imageUri: Uri
    private lateinit var progressBar: ProgressBar
    private lateinit var readNotesButton: Button

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        imageView = findViewById(R.id.reviewImageView)
        progressBar = findViewById(R.id.loadingProgressBar)
        readNotesButton = findViewById(R.id.read_notes_button)

        // Get the image URI passed from MainActivity
        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString)

            // Load the image into the ImageView
            imageView.setImageURI(imageUri)
        }

        // Button to process the notes
        readNotesButton.setOnClickListener {
            // Change button text to "Processing..."
            readNotesButton.text = "Processing..."
            readNotesButton.isEnabled = false  // Disable button during processing

            // Show progress bar
            progressBar.visibility = ProgressBar.VISIBLE

            // Handle the "Read Notes" functionality
            readNotes()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun readNotes() {
        // Convert the image to Bitmap
        var bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)

        // Correct the orientation of the image
        bitmap = correctImageOrientation(bitmap, imageUri)

        // Convert Bitmap to OpenCV Mat
        val mat = BitmapToMat(bitmap)

        // Preprocess image
        val processedMat = preprocessImage(mat)

        // Detect contours (potential noteheads)
        val contours = detectContours(processedMat)

        // Visualize all contours (for debugging purposes)
        highlightContoursBeforeFilter(mat, contours)

        // Highlight detected notes
        highlightNotes(mat, contours)

        // Convert Mat back to Bitmap and set it to ImageView
        val resultBitmap = MatToBitmap(mat)
        imageView.setImageBitmap(resultBitmap)

        // Hide progress bar and update button text
        progressBar.visibility = ProgressBar.GONE
        readNotesButton.text = "Go Back to Main"
        readNotesButton.isEnabled = true  // Re-enable button

        // Set the button to navigate back to MainActivity when clicked
        readNotesButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()  // Optionally finish the current activity
        }
    }

    private fun highlightContoursBeforeFilter(mat: Mat, contours: List<MatOfPoint>) {
        val redColor = Scalar(0.0, 0.0, 255.0)  // Red color for contour visualization

        // Draw all contours on the image (for debugging purposes)
        for (contour in contours) {
            Imgproc.drawContours(mat, listOf(contour), -1, redColor, 1)
        }
    }

    // Highlight the detected notes on the original image
    @OptIn(UnstableApi::class)
    private fun highlightNotes(mat: Mat, contours: List<MatOfPoint>) {
        val yellowColor = Scalar(255.0, 255.0, 0.0) // Yellow color for highlighting
        val minNoteSize = 100  // Minimum area threshold for noteheads
        val aspectRatioRange = 0.8..1.2  // Allow for near-circular shapes (noteheads)

        // Loop through the contours and check if they resemble noteheads
        for (contour in contours) {
            // Convert MatOfPoint to MatOfPoint2f
            val contour2f = MatOfPoint2f(*contour.toArray())

            // Approximate the contour shape to check if it's circular (notehead shape)
            val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

            // Debug: Log the size and aspect ratio of the bounding box
            val boundingRect = Imgproc.boundingRect(contour)
            val aspectRatio = boundingRect.width.toFloat() / boundingRect.height.toFloat()
            val contourArea = boundingRect.area()
            Log.d("BoundingBox", "Rect: $boundingRect, Aspect Ratio: $aspectRatio, Area: $contourArea")

            // Filter based on area and aspect ratio to avoid dots and small shapes
            if (aspectRatio in aspectRatioRange && contourArea > minNoteSize) {
                // Highlight the contour (it's a notehead)
                Imgproc.rectangle(mat, boundingRect.tl(), boundingRect.br(), yellowColor, 2)

                // Add text next to the detected note
                val notePosition = Point(boundingRect.x.toDouble(), boundingRect.y.toDouble() - 10)
                Imgproc.putText(mat, "Note", notePosition, Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, Scalar(0.0, 255.0, 0.0), 2)
            }
        }
    }


    // Convert Bitmap to Mat (OpenCV)
    private fun BitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    // Convert Mat to Bitmap
    private fun MatToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    // Preprocess the image to prepare for contour detection
    private fun preprocessImage(mat: Mat): Mat {
        val grayMat = Mat()
        val thresholdMat = Mat()

        // Convert to grayscale
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

        // Apply GaussianBlur to reduce noise
        Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)

        // Apply thresholding to create binary image
        Imgproc.threshold(grayMat, thresholdMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

        return thresholdMat
    }

    // Detect contours in the threshold image
    private fun detectContours(mat: Mat): List<MatOfPoint> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        // Find contours
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        return contours
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun correctImageOrientation(bitmap: Bitmap, imageUri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(imageUri)
        val exifInterface = ExifInterface(inputStream!!)

        // Get the orientation from the EXIF data
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        var rotationAngle = 0
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotationAngle = 90
            ExifInterface.ORIENTATION_ROTATE_180 -> rotationAngle = 180
            ExifInterface.ORIENTATION_ROTATE_270 -> rotationAngle = 270
        }

        if (rotationAngle != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationAngle.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        return bitmap  // No rotation needed if orientation is normal
    }
}
