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

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [LoginFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LoginFragment : Fragment() {
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
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        // Bind input layouts, text fields, and button
        val emailLayout = view.findViewById<TextInputLayout>(R.id.loginEmailInputLayout)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.loginPasswordInputLayout)
        val emailEdit = view.findViewById<TextInputEditText>(R.id.loginEmailEditText)
        val passwordEdit = view.findViewById<TextInputEditText>(R.id.loginPasswordEditText)
        val loginButton = view.findViewById<MaterialButton>(R.id.loginButton)

        // "Don't have an account? Sign up" -> SignUpFragment
        view.findViewById<View>(R.id.signUpLinkTextView).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.authContainer, SignUpFragment())
                .addToBackStack(null)   // so Back returns to Login
                .commit()
        }

        // Forgot password button
        val forgotPasswordTextView = view.findViewById<View>(R.id.forgotPasswordTextView)
        forgotPasswordTextView.setOnClickListener {
            emailLayout.error = null
            passwordLayout.error = null

            val email = emailEdit.text?.toString()?.trim().orEmpty()

            if (email.isEmpty()) {
                emailLayout.error = "Enter your email first to reset your password"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "Enter a valid email address"
                return@setOnClickListener
            }
            // One reset request at a time
            forgotPasswordTextView.isEnabled = false

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    forgotPasswordTextView.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Password reset email sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    forgotPasswordTextView.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        e.localizedMessage ?: "Failed to send reset email",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        // Log in button and firebase authentication
        loginButton.setOnClickListener {
            emailLayout.error = null
            passwordLayout.error = null

            val email = emailEdit.text?.toString()?.trim().orEmpty()
            val password = passwordEdit.text?.toString().orEmpty()

            var isValid = true

            if (email.isEmpty()) {
                emailLayout.error = "Email is required"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "Enter a valid email address"
                isValid = false
            }

            if (password.isEmpty()) {
                passwordLayout.error = "Password is required"
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            // One submission at a time
            loginButton.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    // Go to main screen and finish auth
                    startActivity(Intent(requireContext(), MainActivity::class.java))
                    requireActivity().finish()
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    loginButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        e.localizedMessage ?: "Log in failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment LoginFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            LoginFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}