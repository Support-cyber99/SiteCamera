package com.example.sitecamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Site Camera
 *
 * Flow:
 *  1. On first launch (or whenever the user taps the gear icon) they set a
 *     SITE NAME once. It is stored in SharedPreferences and reused for every photo.
 *  2. Before each shot, the user can type a short COMPLAINT / remark in the
 *     text box above the shutter button.
 *  3. On capture, the photo is saved via CameraX/MediaStore, then re-opened
 *     and stamped with:
 *       - current date & time (top-left, auto, always present)
 *       - a bordered box (bottom-left) containing the complaint text and,
 *         below it, a bullet + the saved site name — matching the look of
 *         the reference "GPS Map Camera" style photos.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var etComplaint: EditText
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSettings: ImageButton

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val TAG = "SiteCamera"
        private const val PREFS_NAME = "site_camera_prefs"
        private const val KEY_SITE_NAME = "site_name"
        private const val KEY_LAST_COMPLAINT = "last_complaint"
        private const val REQUEST_CAMERA_PERMISSION = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on while camera is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        previewView = findViewById(R.id.previewView)
        etComplaint = findViewById(R.id.etComplaint)
        btnCapture = findViewById(R.id.btnCapture)
        btnSettings = findViewById(R.id.btnSettings)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Restore the last-used complaint text so you don't have to retype it
        // if the app was closed and reopened mid-batch.
        etComplaint.setText(prefs.getString(KEY_LAST_COMPLAINT, ""))

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }

        btnSettings.setOnClickListener { showSiteNameDialog() }
        btnCapture.setOnClickListener { takePhoto() }

        // Ask for the site name the very first time the app is opened.
        if (getSiteName().isBlank()) {
            showSiteNameDialog()
        }
    }

    private fun getSiteName(): String = prefs.getString(KEY_SITE_NAME, "") ?: ""

    private fun showSiteNameDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(getSiteName())
            hint = "e.g. KALYANI FUEL STATION GADDIGE MANGALORE"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Site Name")
            .setMessage("This is stamped on every photo. You only need to set it once — change it any time with the gear icon.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                prefs.edit().putString(KEY_SITE_NAME, name).apply()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
                Toast.makeText(this, "Could not start camera: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val complaint = etComplaint.text.toString().trim()
        val siteName = getSiteName()

        // Remember this remark so it's still here for the next photo (and next launch).
        prefs.edit().putString(KEY_LAST_COMPLAINT, complaint).apply()

        val fileName = "SITE_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SiteCamera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        btnCapture.isEnabled = false

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@MainActivity, "Capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                    btnCapture.isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    if (savedUri != null) {
                        stampImage(savedUri, siteName, complaint)
                        Toast.makeText(this@MainActivity, "Saved to Pictures/SiteCamera", Toast.LENGTH_SHORT).show()
                        // Note: complaint text box is intentionally left as-is so you can
                        // capture many photos in a row with the same remark without retyping.
                        // Clear it manually whenever you want to change the remark.
                    }
                    btnCapture.isEnabled = true
                }
            }
        )
    }

    /**
     * Re-opens the just-saved photo and burns the date/time + complaint +
     * site name onto it, then writes it back to the same MediaStore entry.
     */
    private fun stampImage(uri: Uri, siteName: String, complaint: String) {
        try {
            val original = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return

            val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)
            val width = bitmap.width
            val height = bitmap.height

            val baseTextSize = width * 0.032f
            val greenColor = Color.parseColor("#39FF14")

            fun textPaints(size: Float, bold: Boolean = false): Pair<Paint, Paint> {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.14f
                    textSize = size
                    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = greenColor
                    style = Paint.Style.FILL
                    textSize = size
                    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                return Pair(stroke, fill)
            }

            // ---- Timestamp, top-left, always auto-generated ----
            val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            val timestamp = sdf.format(Date())
            val (tsStroke, tsFill) = textPaints(baseTextSize, bold = true)
            val tsX = width * 0.03f
            val tsY = height * 0.06f
            canvas.drawText(timestamp, tsX, tsY, tsStroke)
            canvas.drawText(timestamp, tsX, tsY, tsFill)

            // ---- Bottom bordered info box: complaint + bullet/site name ----
            val margin = width * 0.03f
            val lineHeight = baseTextSize * 1.55f
            val lines = mutableListOf<Pair<String, Boolean>>() // text, bold
            if (complaint.isNotBlank()) lines.add(complaint to false)
            if (siteName.isNotBlank()) lines.add("\u25CF  $siteName" to true)

            if (lines.isNotEmpty()) {
                val boxLeft = margin
                val boxRight = width - margin
                val boxHeight = lineHeight * lines.size + baseTextSize * 0.5f
                val boxBottom = height - margin
                val boxTop = boxBottom - boxHeight

                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = greenColor
                    style = Paint.Style.STROKE
                    strokeWidth = width * 0.004f
                }
                canvas.drawRoundRect(RectF(boxLeft, boxTop, boxRight, boxBottom), 14f, 14f, boxPaint)

                val textX = boxLeft + baseTextSize * 0.6f
                var textY = boxTop + lineHeight * 0.75f
                for ((text, bold) in lines) {
                    val (stroke, fill) = textPaints(baseTextSize, bold)
                    canvas.drawText(text, textX, textY, stroke)
                    canvas.drawText(text, textX, textY, fill)
                    textY += lineHeight
                }
            }

            val outputStream: OutputStream? = contentResolver.openOutputStream(uri, "w")
            outputStream?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stamp image", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
