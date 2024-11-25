package fortis.nicholas.cardgradeai

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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
            // Check if imagePath is not null or empty before decoding
            if (!upload.imagePath.isNullOrEmpty()) {
                val decodedByteArray = Base64.decode(upload.imagePath, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.size)
                imageView.setImageBitmap(bitmap)
            } else {
                // Set a placeholder if image is empty or null
                Log.e("Oops", "Oops")
            }

            responseTextView.text = upload.apiResponse

            itemView.setOnClickListener {
                onItemClick(upload)
            }
        }
    }
}
