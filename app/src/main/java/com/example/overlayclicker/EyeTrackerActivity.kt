package com.example.overlayclicker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EyeTrackerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: EyeOverlayView
    private lateinit var statusView: TextView
    private lateinit var analysisExecutor: ExecutorService

    // Blinzel-Erkennung: beide Augen zu (< CLOSED) und danach wieder offen (> OPEN) = ein Blinzeln
    private var eyesClosed = false
    private var closedSince = 0L
    private var blinkCount = 0

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Kamera-Berechtigung fehlt", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analysisExecutor = Executors.newSingleThreadExecutor()
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        overlay = EyeOverlayView(this)
        statusView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.BLACK)
            setBackgroundColor(0xDDFFFFFF.toInt())
            setPadding(24, 16, 24, 16)
            text = "Kamera startet..."
        }

        val root = FrameLayout(this)
        val fullSize = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(previewView, fullSize)
        root.addView(overlay as View, FrameLayout.LayoutParams(fullSize))
        root.addView(
            statusView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = 120 }
        )
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { imageProxy -> processFrame(imageProxy) }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = rotation == 90 || rotation == 270
        val imageW = if (rotated) imageProxy.height else imageProxy.width
        val imageH = if (rotated) imageProxy.width else imageProxy.height
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(image)
            .addOnSuccessListener { faces ->
                val eyes = mutableListOf<PointF>()
                for (face in faces) {
                    face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { eyes.add(it) }
                    face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { eyes.add(it) }
                }
                overlay.update(eyes, imageW, imageH)
                updateBlinkState(faces.firstOrNull())
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Läuft auf dem Main-Thread (Listener von ML Kit). */
    private fun updateBlinkState(face: Face?) {
        if (face == null) {
            statusView.text = "Kein Gesicht erkannt"
            eyesClosed = false
            return
        }
        val left = face.leftEyeOpenProbability ?: return
        val right = face.rightEyeOpenProbability ?: return
        val now = System.currentTimeMillis()

        if (!eyesClosed && left < EYE_CLOSED && right < EYE_CLOSED) {
            eyesClosed = true
            closedSince = now
        } else if (eyesClosed && left > EYE_OPEN && right > EYE_OPEN) {
            eyesClosed = false
            val duration = now - closedSince
            if (duration in MIN_BLINK_MS..MAX_BLINK_MS) {
                blinkCount++
            }
        }

        statusView.text = "Blinzeln: $blinkCount  |  Augen L %.2f  R %.2f".format(left, right)
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val EYE_CLOSED = 0.3f
        private const val EYE_OPEN = 0.7f
        private const val MIN_BLINK_MS = 60L
        private const val MAX_BLINK_MS = 700L
    }
}

/** Zeichnet gelbe Kreise an den Augenpositionen, passend zur Vorschau (Center-Crop, gespiegelt). */
class EyeOverlayView(context: Context) : View(context) {
    private var eyes: List<PointF> = emptyList()
    private var imageW = 1
    private var imageH = 1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    fun update(points: List<PointF>, imgWidth: Int, imgHeight: Int) {
        eyes = points
        imageW = imgWidth
        imageH = imgHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val scale = maxOf(width / imageW.toFloat(), height / imageH.toFloat())
        val offX = (imageW * scale - width) / 2f
        val offY = (imageH * scale - height) / 2f
        for (p in eyes) {
            val x = (imageW - p.x) * scale - offX   // Frontkamera-Vorschau ist gespiegelt
            val y = p.y * scale - offY
            canvas.drawCircle(x, y, 45f, paint)
        }
    }
}
