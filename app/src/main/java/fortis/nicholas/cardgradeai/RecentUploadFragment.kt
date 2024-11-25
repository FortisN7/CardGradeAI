package fortis.nicholas.cardgradeai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class RecentUploadFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val uploadDao by lazy { (requireActivity().application as MyApplication).uploadDao }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_recent_upload, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)

        // Set a LayoutManager to the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Load uploads from the database
        loadUploads()

        return view
    }

    // Load all uploads from the database
    private fun loadUploads() {
        lifecycleScope.launch {
            val uploads = uploadDao.getAllUploads()
            println(uploads)
            // Set RecyclerView adapter to display the uploads
            val adapter = UploadAdapter(uploads) { upload ->
                // Handle card click (navigate to UploadDetailFragment)
                navigateToUploadDetail(upload)
            }
            recyclerView.adapter = adapter
        }
    }

    private fun navigateToUploadDetail(upload: Upload) {
        // Create a new fragment instance for the upload detail
        val uploadDetailFragment = RecentUploadDetailFragment()

        // Pass the upload ID or other details to the new fragment
        val bundle = Bundle().apply {
            putLong("upload_id", upload.id) // Pass upload ID (or any other necessary data)
        }

        uploadDetailFragment.arguments = bundle

        // Navigate to the UploadDetailFragment
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, uploadDetailFragment) // Use your container ID here
            .addToBackStack(null) // Optionally, add to back stack for back navigation
            .commit()
    }
}
