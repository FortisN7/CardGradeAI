package fortis.nicholas.cardgradeai

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.upload_detail_image)
        responseTextView = view.findViewById(R.id.upload_detail_response)

        // Load the upload details from the database using the upload ID
        val uploadDao = (requireActivity().application as MyApplication).uploadDao

        // Use a coroutine to load the upload details from Room
        lifecycleScope.launch {
            val upload = uploadDao.getUploadById(uploadId)
            upload?.let {
                val decodedImage = Base64.decode(it.imagePath, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(decodedImage, 0, decodedImage.size)
                imageView.setImageBitmap(bitmap)
                responseTextView.text = it.apiResponse
            }
        }
    }

    companion object {
        fun newInstance(uploadId: Long): RecentUploadDetailFragment {
            val fragment = RecentUploadDetailFragment()
            val args = Bundle()
            args.putLong("upload_id", uploadId)
            fragment.arguments = args
            return fragment
        }
    }
}
