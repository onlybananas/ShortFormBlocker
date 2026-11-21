package com.example.shortformblocker

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.core.content.edit

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class HomeFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var isBindingSwitches = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        // Bind text views and switches
        val greetingTextView = view.findViewById<TextView>(R.id.greetingTextView)
        val youtubeSwitch = view.findViewById<MaterialSwitch>(R.id.youtubeSwitch)
        val instagramSwitch = view.findViewById<MaterialSwitch>(R.id.instagramSwitch)
        val streakCountTextView = view.findViewById<TextView>(R.id.streakCountTextView)
        val profileContainer = view.findViewById<View>(R.id.profileContainer)

        // Profile button
        profileContainer.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        val prefs = requireContext().getSharedPreferences("home_cache", Context.MODE_PRIVATE)

        // Use cached values for greeting and switches
        val cachedName = prefs.getString("name_$uid","")?.trim().orEmpty()
        if (cachedName.isNotEmpty()){
            greetingTextView.text = "Hello, $cachedName!"
        }
        val cachedYoutube = prefs.getBoolean("yt_$uid", false)
        val cachedInstagram = prefs.getBoolean("ig_$uid", false)
        val cachedStreak = prefs.getLong("streak_$uid", 0L)
        streakCountTextView.text = cachedStreak.toString()

        isBindingSwitches = true
        youtubeSwitch.isChecked = cachedYoutube
        instagramSwitch.isChecked = cachedInstagram
        isBindingSwitches = false

        // YouTube switch
        youtubeSwitch.setOnCheckedChangeListener { switchView, isChecked ->
            if (isBindingSwitches) return@setOnCheckedChangeListener
            prefs.edit { putBoolean("yt_$uid", isChecked) }
            userRef.collection("trackedApps").document("youtube")
                .set(mapOf("blockShortFormOnly" to isChecked), SetOptions.merge())
        }
        // Instagram switch
        instagramSwitch.setOnCheckedChangeListener { switchView, isChecked ->
            if (isBindingSwitches) return@setOnCheckedChangeListener
            prefs.edit { putBoolean("ig_$uid", isChecked) }
            userRef.collection("trackedApps").document("instagram")
                .set(mapOf("blockShortFormOnly" to isChecked), SetOptions.merge())
        }

        // Update name and streak from db, update screen, save to SharedPreferences
        userRef.get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                val firstName = doc.getString("firstName")?.trim().orEmpty()
                if (firstName.isNotEmpty()) {
                    greetingTextView.text = "Hello, $firstName!"
                    prefs.edit { putString("name_$uid", firstName) }
                } else {
                    greetingTextView.text = "Hello, User!"
                }
                val currentStreak = doc.getLong("currentStreak") ?: 0L
                streakCountTextView.text = currentStreak.toString()
                prefs.edit { putLong("streak_$uid", currentStreak) }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(
                    requireContext(),
                    e.localizedMessage ?: "Failed to load user data",
                    Toast.LENGTH_SHORT
                ).show()
            }
        // Update toggles from db, update screen, save to SharedPreferences
        userRef.collection("trackedApps").get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                val ytEnabled = snapshot.documents
                    .firstOrNull { it.id == "youtube" }
                    ?.getBoolean("blockShortFormOnly") ?: false
                val igEnabled = snapshot.documents
                    .firstOrNull { it.id == "instagram" }
                    ?.getBoolean("blockShortFormOnly") ?: false

                prefs.edit {
                    putBoolean("yt_$uid", ytEnabled)
                    putBoolean("ig_$uid", igEnabled)
                }

                isBindingSwitches = true
                if (youtubeSwitch.isChecked != ytEnabled) youtubeSwitch.isChecked = ytEnabled
                if (instagramSwitch.isChecked != igEnabled) instagramSwitch.isChecked = igEnabled
                isBindingSwitches = false
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(
                    requireContext(),
                    e.localizedMessage ?: "Failed to load app settings",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment HomeFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}