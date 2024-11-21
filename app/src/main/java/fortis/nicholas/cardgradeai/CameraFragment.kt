package fortis.nicholas.cardgradeai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

                    // Navigate to Recent Upload Fragment
                    val recentUploadFragment = RecentUploadFragment()
                    val args = Bundle()
                    args.putString("photo_path", filePath) // Pass photo path if needed
                    recentUploadFragment.arguments = args

                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, recentUploadFragment)
                        .addToBackStack(null)
                        .commit()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(context, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
