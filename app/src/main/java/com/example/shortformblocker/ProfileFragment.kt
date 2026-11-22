package com.example.shortformblocker

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ProfileFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ProfileFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

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
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize firebase
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        // Initialize profile views
        val nameTextView = view.findViewById<TextView>(R.id.nameTextView)
        val usernameTextView = view.findViewById<TextView>(R.id.usernameTextView)
        val currentStreakTextView = view.findViewById<TextView>(R.id.currentStreakTextView)
        val longestStreakTextView = view.findViewById<TextView>(R.id.longestStreakTextView)
        val friendsCountTextView = view.findViewById<TextView>(R.id.friendsCountTextView)
        val userEmailTextView = view.findViewById<TextView>(R.id.userEmailTextView)
        val memberDateTextView = view.findViewById<TextView>(R.id.memberDateTextView)
        val editProfileButton = view.findViewById<MaterialButton>(R.id.editProfileButton)

        val backTextView = view.findViewById<View>(R.id.backTextView)
        backTextView.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val viewAllFriendsTextView = view.findViewById<View>(R.id.viewAllFriendsTextView)
        viewAllFriendsTextView.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, FriendsFragment())
                .addToBackStack(null)
                .commit()
        }

        // Check for logged in user
        val uid = auth.currentUser?.uid
        if (uid == null){
            Toast.makeText(requireContext(), "User not found.", Toast.LENGTH_SHORT).show()
            return
        }
        // Load cached user data
        val prefs = requireContext().getSharedPreferences(
            "profile_cache_$uid", Context.MODE_PRIVATE
        )

        var currentFirstName = prefs.getString("firstName", "").orEmpty()
        var currentLastName = prefs.getString("lastName", "").orEmpty()
        val cachedUsername = prefs.getString("username", "").orEmpty()

        val cachedStreak = prefs.getLong("currentStreak", 0L)
        val cachedLongestStreak = prefs.getLong("longestStreak", 0L)
        val cachedFriendsCount = prefs.getLong("friendsCount", 0L)
        val cachedMemberSince = prefs.getString("memberSince", "").orEmpty()

        // Display cached information
        nameTextView.text = "$currentFirstName $currentLastName".trim()

        usernameTextView.text = if (cachedUsername.isNotEmpty()) {
            "@$cachedUsername"
        } else {
            ""
        }

        currentStreakTextView.text = cachedStreak.toString()
        longestStreakTextView.text = cachedLongestStreak.toString()
        friendsCountTextView.text = cachedFriendsCount.toString()
        userEmailTextView.text = auth.currentUser?.email.orEmpty()
        memberDateTextView.text = cachedMemberSince

        // Get data from firestore
        val userRef = db.collection("users").document(uid)

        userRef.get()
            .addOnSuccessListener { doc ->
                if (!isAdded || this@ProfileFragment.view !== view) {
                    return@addOnSuccessListener
                }

                currentFirstName = doc.getString("firstName")?.trim().orEmpty()
                currentLastName = doc.getString("lastName")?.trim().orEmpty()
                val username = doc.getString("username")?.trim().orEmpty()

                val currentStreak = doc.getLong("currentStreak") ?: 0L
                val longestStreak = doc.getLong("longestStreak") ?: 0L
                val friendsCount = doc.getLong("friendsCount") ?: 0L

                val createdAt = doc.getTimestamp("createdAt")
                val memberSince = if (createdAt != null) {
                    SimpleDateFormat("MMM yyyy", Locale.getDefault())
                        .format(createdAt.toDate())
                } else {
                    ""
                }

                // Update the screen
                nameTextView.text = "$currentFirstName $currentLastName".trim()
                usernameTextView.text = if (username.isNotEmpty()) {
                    "@$username"
                } else {
                    ""
                }

                currentStreakTextView.text = currentStreak.toString()
                longestStreakTextView.text = longestStreak.toString()
                friendsCountTextView.text = friendsCount.toString()
                memberDateTextView.text = memberSince

                // Save latest information to cache
                prefs.edit {
                    putString("firstName", currentFirstName)
                    putString("lastName", currentLastName)
                    putString("username", username)
                    putLong("currentStreak", currentStreak)
                    putLong("longestStreak", longestStreak)
                    putLong("friendsCount", friendsCount)
                    putString("memberSince", memberSince)
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded || this@ProfileFragment.view !== view) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    e.localizedMessage ?: "Failed to load profile",
                    Toast.LENGTH_SHORT
                ).show()
            }

        // Edit profile button
        editProfileButton.setOnClickListener {
            // Create first and last name fields
            val firstNameInput = EditText(requireContext())
            firstNameInput.hint = "First Name"
            firstNameInput.setText(currentFirstName)

            val lastNameInput = EditText(requireContext())
            lastNameInput.hint = "Last Name"
            lastNameInput.setText(currentLastName)

            // Add fields to layout
            val layout = LinearLayout(requireContext())
            layout.orientation = LinearLayout.VERTICAL
            layout.addView(firstNameInput)
            layout.addView(lastNameInput)

            // Create edit profile dialog box
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Edit Profile")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create()

            dialog.setOnShowListener {
                val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

                saveButton.setOnClickListener {
                    val firstName = firstNameInput.text.toString().trim()
                    val lastName = lastNameInput.text.toString().trim()

                    // Validate name input
                    if (firstName.isEmpty() || lastName.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "Both names are required",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        saveButton.isEnabled = false

                        // Update names in firestore
                        userRef.update(
                            mapOf(
                                "firstName" to firstName,
                                "lastName" to lastName
                            )
                        ).addOnSuccessListener {
                            currentFirstName = firstName
                            currentLastName = lastName
                            // Update cache
                            prefs.edit {
                                putString("firstName", firstName)
                                putString("lastName", lastName)
                            }
                            // Update home cache
                            requireContext()
                                .getSharedPreferences("home_cache", Context.MODE_PRIVATE)
                                .edit {
                                    putString("name_$uid", firstName)
                                }
                            // Update displayed name
                            if (this@ProfileFragment.view === view) {
                                nameTextView.text = "$firstName $lastName"
                            }

                            dialog.dismiss()
                        }.addOnFailureListener {
                            saveButton.isEnabled = true
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to update profile",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }

            dialog.show()
        }


    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ProfileFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}