package fortis.nicholas.cardgradeai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class UploadAdapter(
    private val uploads: List<Upload>,
    private val onItemClick: (Upload) -> Unit
) : RecyclerView.Adapter<UploadAdapter.UploadViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UploadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_upload, parent, false)
        return UploadViewHolder(view)
    }

    override fun onBindViewHolder(holder: UploadViewHolder, position: Int) {
        val upload = uploads[position]
        holder.bind(upload)
    }

    override fun getItemCount(): Int = uploads.size

    inner class UploadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.uploaded_image)
        private val responseTextView: TextView = itemView.findViewById(R.id.api_response)

        fun bind(upload: Upload) {
            if (!upload.imagePath.isNullOrEmpty()) {
                val file = File(upload.imagePath)
                if (file.exists()) {
                    // Decode and correct image orientation
                    val correctedBitmap = getCorrectedBitmap(file.absolutePath)
                    imageView.setImageBitmap(correctedBitmap)
                } else {
                    Log.e("UploadAdapter", "Image file does not exist: ${upload.imagePath}")
                    imageView.setImageResource(R.drawable.ic_placeholder) // Placeholder image
                }
            } else {
                Log.e("UploadAdapter", "Image path is null or empty")
                imageView.setImageResource(R.drawable.ic_placeholder)
            }

            // Display API response
            responseTextView.text = upload.apiResponse

            itemView.setOnClickListener {
                onItemClick(upload)
            }
        }

        private fun getCorrectedBitmap(imagePath: String): Bitmap {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            val exif = ExifInterface(imagePath)

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            return if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.width, bitmap.height, matrix, true
                )
            } else {
                bitmap
            }
        }
    }
}
