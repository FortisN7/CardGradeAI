package fortis.nicholas.cardgradeai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class RecentUploadFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_recent_upload, container, false)
        val result = arguments?.getString("result")

        view.findViewById<TextView>(R.id.result_text).text = result ?: "No recent upload"

        return view
    }
}
