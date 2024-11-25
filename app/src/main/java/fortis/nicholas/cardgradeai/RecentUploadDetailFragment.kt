package fortis.nicholas.cardgradeai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class RecentUploadDetailFragment : Fragment(R.layout.fragment_recent_upload_detail) {

    private lateinit var imageView: ImageView
    private lateinit var responseTextView: TextView

    private var uploadId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            uploadId = it.getLong("upload_id")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.upload_detail_image)
        responseTextView = view.findViewById(R.id.upload_detail_response)

        val uploadDao = (requireActivity().application as MyApplication).uploadDao

        lifecycleScope.launch {
            val upload = uploadDao.getUploadById(uploadId)
            upload?.let {
                if (!it.imagePath.isNullOrEmpty()) {
                    val file = File(it.imagePath)
                    if (file.exists()) {
                        val correctedBitmap = getCorrectedBitmap(file)
                        imageView.setImageBitmap(correctedBitmap)
                    } else {
                        imageView.setImageResource(R.drawable.ic_placeholder)
                    }
                } else {
                    imageView.setImageResource(R.drawable.ic_placeholder)
                }

                responseTextView.text = it.apiResponse
            }
        }

        // Set up swipe gesture to navigate back to RecentUploadFragment
        setupSwipeGesture(view)
    }

    private fun getCorrectedBitmap(file: File): Bitmap? {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            rotateBitmap(bitmap, orientation)
        } catch (e: IOException) {
            Log.e("RecentUploadDetail", "Error reading Exif data", e)
            null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun setupSwipeGesture(view: View) {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Detect a left swipe and navigate to RecentUploadFragment
                if (e1 != null) {
                    if (e1.x - e2.x > 100) {
                        navigateToRecentUploadsFragment()
                        return true
                    }
                }
                return false
            }
        })

        // Set the touch listener to detect the swipe gesture
        view.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun navigateToRecentUploadsFragment() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RecentUploadFragment())
            .commit()
    }

    companion object {
        // Create a new instance of RecentUploadDetailFragment with the uploadId
        fun newInstance(uploadId: Long): RecentUploadDetailFragment {
            val fragment = RecentUploadDetailFragment()
            val args = Bundle()
            args.putLong("upload_id", uploadId)
            fragment.arguments = args
            return fragment
        }
    }
}
