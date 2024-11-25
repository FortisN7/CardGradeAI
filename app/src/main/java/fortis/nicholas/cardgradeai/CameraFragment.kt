package fortis.nicholas.cardgradeai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

class CameraFragment : Fragment() {
    private lateinit var previewView: PreviewView
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

        if (!hasCameraPermission()) {
            requestCameraPermission()
        } else {
            startCamera()
        }

        view.findViewById<Button>(R.id.capture_button).setOnClickListener {
            Toast.makeText(context, "Photo taken successfully! Please wait...", Toast.LENGTH_SHORT).show()
            takePicture()
        }

        return view
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera use cases", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePicture() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            requireContext().externalMediaDirs.firstOrNull(),
            "IMG_${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val filePath = photoFile.absolutePath
                    Log.d(TAG, "Photo saved: $filePath")
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
        Executors.newSingleThreadExecutor().execute {
            try {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                val correctedBitmap = rotateImage(bitmap, photoPath)
                val resizedBitmap = reduceImageResolution(correctedBitmap)
                val base64Image = encodeImageToBase64(resizedBitmap)
                val payload = createPayload(base64Image)

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.CHATGPT_APIKEY}")
                    .post(RequestBody.create("application/json".toMediaType(), payload.toString()))
                    .build()

                val response = client.newCall(request).execute()
                response.body?.string()?.let { responseString ->
                    val parsedResponse = parseResponse(responseString)
                    saveToDatabase(photoPath, parsedResponse)
                    navigateToRecentUploads()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending request to ChatGPT", e)
            }
        }
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun reduceImageResolution(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024
        val aspectRatio = bitmap.width.toFloat() / bitmap.height
        val newWidth = if (bitmap.width > bitmap.height) maxDimension else (maxDimension * aspectRatio).toInt()
        val newHeight = if (bitmap.height > bitmap.width) maxDimension else (maxDimension / aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun rotateImage(bitmap: Bitmap, photoPath: String): Bitmap {
        val exif = ExifInterface(photoPath)
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveToDatabase(imagePath: String, response: String) {
        val upload = Upload(imagePath = imagePath, apiResponse = response)
        lifecycleScope.launch {
            val database = UploadDatabase.getDatabase(requireContext())
            database.uploadDao().insert(upload)
        }
    }

    private fun parseResponse(responseString: String): String {
        return try {
            val responseJson = JSONObject(responseString)
            val choicesArray = responseJson.getJSONArray("choices")
            val messageObject = choicesArray.getJSONObject(0).getJSONObject("message")
            messageObject.getString("content").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: $responseString", e)
            "No valid response"
        }
    }

    private fun navigateToRecentUploads() {
        lifecycleScope.launch {
            // Fetch the most recent upload from the database
            val database = UploadDatabase.getDatabase(requireContext())
            val recentUpload = database.uploadDao().getLatestUpload()

            // Pass the recent upload to RecentUploadDetailFragment
            val fragment = RecentUploadDetailFragment.newInstance(recentUpload.id)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }
    }


    private fun createPayload(base64Image: String): JSONObject {
        return JSONObject().apply {
            put("model", "gpt-4o-2024-11-20")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "You are a professional Pokemon card grader and work for PSA, the largest card grader in the world. Be very meticulous and scrutinize details more heavily because if it shows in an image, it's probably worse in real life. Don't answer me like I'm talking to you by saying something like sure! Here! This is for an end-user product.") })
                put(JSONObject().apply { put("role", "user"); put("content", "The image I sent is the Pokemon card that I want graded. Using what you know about card grading, estimate the grade of the card on the PSA scale. Don't say you can't do it, I just want an estimate and a short answer for each metric that helped you come to that conclusion. Keep it in a range of 1 value if you must do a range as the estimate i.e. 9-8 or 7-6.") })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply { put("url", "data:image/jpeg;base64,$base64Image") })
                    }.toString())
                })
            })
            put("temperature", 1)
            put("max_tokens", 2048)
            put("top_p", 1)
            put("frequency_penalty", 0)
            put("presence_penalty", 0)
        }
    }
}
