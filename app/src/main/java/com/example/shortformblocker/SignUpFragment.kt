package com.example.shortformblocker

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Locale.getDefault

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SignUpFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SignUpFragment : Fragment() {
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
        return inflater.inflate(R.layout.fragment_sign_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        // Bind input layouts, text fields, and button
        val firstNameLayout = view.findViewById<TextInputLayout>(R.id.firstNameInputLayout)
        val lastNameLayout = view.findViewById<TextInputLayout>(R.id.lastNameInputLayout)
        val usernameLayout = view.findViewById<TextInputLayout>(R.id.usernameInputLayout)
        val emailLayout = view.findViewById<TextInputLayout>(R.id.emailInputLayout)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val confirmPasswordLayout =
            view.findViewById<TextInputLayout>(R.id.confirmPasswordInputLayout)

        val firstNameEdit = view.findViewById<TextInputEditText>(R.id.firstNameEditText)
        val lastNameEdit = view.findViewById<TextInputEditText>(R.id.lastNameEditText)
        val usernameEdit = view.findViewById<TextInputEditText>(R.id.usernameEditText)
        val emailEdit = view.findViewById<TextInputEditText>(R.id.emailEditText)
        val passwordEdit = view.findViewById<TextInputEditText>(R.id.passwordEditText)
        val confirmPasswordEdit = view.findViewById<TextInputEditText>(R.id.confirmPasswordEditText)

        val createAccountButton = view.findViewById<MaterialButton>(R.id.createAccountButton)

        // Back and Login button nav
        val navigateBackToLogin = View.OnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.authContainer, LoginFragment())
                    .commit()
            }
        }
        view.findViewById<View>(R.id.backTextView).setOnClickListener(navigateBackToLogin)
        view.findViewById<View>(R.id.loginLinkTextView).setOnClickListener(navigateBackToLogin)

        // Validate inputs, create Auth account, seed initial firestore data
        createAccountButton.setOnClickListener {
            firstNameLayout.error = null
            lastNameLayout.error = null
            usernameLayout.error = null
            emailLayout.error = null
            passwordLayout.error = null
            confirmPasswordLayout.error = null

            val firstName = firstNameEdit.text?.toString()?.trim().orEmpty()
            val lastName = lastNameEdit.text?.toString()?.trim().orEmpty()
            // Normalize username to lowercase for database and friend search
            val username = usernameEdit.text?.toString()?.trim()?.lowercase(getDefault()).orEmpty()
            val email = emailEdit.text?.toString()?.trim().orEmpty()
            val password = passwordEdit.text?.toString().orEmpty()
            val confirmPassword = confirmPasswordEdit.text?.toString().orEmpty()

            var isValid = true

            if (firstName.isEmpty()) {
                firstNameLayout.error = "First name is required"
                isValid = false
            }
            if (lastName.isEmpty()) {
                lastNameLayout.error = "Last name is required"
                isValid = false
            }
            if (username.isEmpty()) {
                usernameLayout.error = "Username is required"
                isValid = false
            } else if (!username.matches(Regex("^[a-zA-Z0-9._]{3,30}$"))) {
                usernameLayout.error = "3–30 characters: letters, numbers, dots, or underscores"
                isValid = false
            }
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "Enter a valid email address"
                isValid = false
            }
            if (password.length < 6) {
                passwordLayout.error = "Password must be at least 6 characters"
                isValid = false
            }
            if (confirmPassword != password) {
                confirmPasswordLayout.error = "Passwords do not match"
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            // Only let user submit create account once at a time
            createAccountButton.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    if (!isAdded) return@addOnSuccessListener
                    val uid = authResult.user?.uid
                    if (uid == null) {
                        createAccountButton.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "Account created, but user ID was missing.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnSuccessListener
                    }

                    seedUserDocuments(
                        db = db,
                        uid = uid,
                        firstName = firstName,
                        lastName = lastName,
                        username = username,
                        email = email,
                        onComplete = {
                            if (!isAdded) return@seedUserDocuments
                            startActivity(Intent(requireContext(), MainActivity::class.java))
                            requireActivity().finish()
                        },
                        onError = { message ->
                            if (!isAdded) return@seedUserDocuments
                            createAccountButton.isEnabled = true
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        }
                    )
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    createAccountButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        e.localizedMessage ?: "Sign up failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    // Sets up initial user profile data in firestore
    private fun seedUserDocuments(
        db: FirebaseFirestore,
        uid: String,
        firstName: String,
        lastName: String,
        username: String,
        email: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        val userRef = db.collection("users").document(uid)

        // Default profile fields
        val profileData = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "username" to username,
            "email" to email,
            "currentStreak" to 0L,
            "longestStreak" to 0L,
            "friendsCount" to 0L,
            "warnBeforeLimit" to true,
            "remindIfIgnored" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(userRef, profileData)
        // Set default tracked app limits
        batch.set(
            userRef.collection("trackedApps").document("youtube"),
            mapOf("blockShortFormOnly" to false, "dailyLimitMin" to 60L)
        )
        batch.set(
            userRef.collection("trackedApps").document("instagram"),
            mapOf("blockShortFormOnly" to false, "dailyLimitMin" to 45L)
        )
        // Set initial daily summary
        batch.set(
            userRef.collection("dailySummaries").document(today),
            mapOf("date" to today, "totalMinutes" to 0L)
        )

        batch.commit()
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to save profile data")
            }
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SignUpFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic fun newInstance(param1: String, param2: String) =
                SignUpFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_PARAM1, param1)
                        putString(ARG_PARAM2, param2)
                    }
                }
    }
}