package fortis.nicholas.cardgradeai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType

class CameraFragment : Fragment() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val CAMERA_PERMISSION_CODE = 1001
        private const val TAG = "CameraFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)
        previewView = view.findViewById(R.id.preview_view)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request permissions and initialize camera
        if (!hasCameraPermission()) {
            requestCameraPermission()
        } else {
            startCamera()
        }

        // Set up capture button
        view.findViewById<Button>(R.id.capture_button).setOnClickListener {
            takePicture()
        }

        return view
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(
                    context,
                    "Camera permission is required to use this feature.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Build the Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Build ImageCapture
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            // Force Back Camera
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind to lifecycle with forced back camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera use cases", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePicture() {
        val imageCapture = imageCapture ?: return

        // Define output file
        val photoFile = File(
            requireContext().externalMediaDirs.firstOrNull(),
            "IMG_${System.currentTimeMillis()}.jpg"
        )

        // Set up output options
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Capture image
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Save file path to pass to the Recent Upload Fragment (if needed)
                    val filePath = photoFile.absolutePath
                    Log.d(TAG, "Photo saved: $filePath")

                    // Now, send the image to the ChatGPT API
                    sendToChatGPT(filePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(context, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun sendToChatGPT(photoPath: String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                // Ensure fragment is attached before accessing context
                if (!isAdded) {
                    Log.e(TAG, "Fragment is not attached to the activity.")
                    return@execute
                }

                // Convert image to Base64
                val bitmap = BitmapFactory.decodeFile(photoPath)
                val resizedBitmap = reduceImageResolution(bitmap) // Resize the image
                val rotatedBitmap = rotateImage(resizedBitmap) // Rotate image if necessary
                val base64Image = encodeImageToBase64(rotatedBitmap)

                // Create the API payload
                val payload = JSONObject().apply {
                    put("model", "gpt-4o-2024-11-20")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are a professional Pokemon card grader...")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "The image I sent is the Pokemon card...")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/png;base64,$base64Image")
                                })
                            }.toString()) // Embed the image data
                        })
                    })
                    put("temperature", 1)
                    put("max_tokens", 1024)
                    put("top_p", 1)
                    put("frequency_penalty", 0)
                    put("presence_penalty", 0)
                }

                // Send the request
                val client = OkHttpClient()
                val requestBody = RequestBody.create(
                    "application/json".toMediaType(),
                    payload.toString()
                )
                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.CHATGPT_APIKEY}")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                // Parse and save response in the database
                if (responseBody != null) {
                    try {
                        val responseJson = JSONObject(responseBody)

                        // Check if "choices" key exists
                        if (responseJson.has("choices")) {
                            val output = responseJson.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")

                            // Save to Room database
                            val upload = Upload(imagePath = photoPath, apiResponse = output)

                            // Ensure fragment is attached before accessing context
                            if (isAdded) {
                                lifecycleScope.launch {
                                    val database = UploadDatabase.getDatabase(requireContext())
                                    database.uploadDao().insert(upload)  // Save the data in Room
                                }
                            }
                        } else {
                            Log.e("RecentUploadFragment", "Error: No 'choices' in response.")
                        }
                    } catch (e: Exception) {
                        Log.e("RecentUploadFragment", "Error parsing response.", e)
                    }
                } else {
                    Log.e("RecentUploadFragment", "Error: Empty response from server.")
                }
            } catch (e: Exception) {
                Log.e("RecentUploadFragment", "Error sending request to ChatGPT", e)
            }
        }
    }


    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream) // Try PNG if JPEG fails
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun reduceImageResolution(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024 // Set a max dimension for the image
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth = if (bitmap.width > bitmap.height) maxDimension else (maxDimension * aspectRatio).toInt()
        val newHeight = if (bitmap.height > bitmap.width) maxDimension else (maxDimension / aspectRatio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun rotateImage(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(90f) // Rotate image by 90 degrees
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
