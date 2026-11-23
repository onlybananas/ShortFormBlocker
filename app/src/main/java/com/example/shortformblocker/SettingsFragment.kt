package com.example.shortformblocker

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.content.edit

// Settings screen: daily limits, notification toggles, and account options.

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase and cache
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val userRef = FirebaseFirestore.getInstance().collection("users").document(uid)
        val cache = requireContext().getSharedPreferences("settings_cache_$uid", Context.MODE_PRIVATE)

        // Initialize views
        val youtubeRow = view.findViewById<View>(R.id.row_limit_youtube)
        val instagramRow = view.findViewById<View>(R.id.row_limit_instagram)
        val youtubeLimit = view.findViewById<TextView>(R.id.youtubeLimitTextView)
        val instagramLimit = view.findViewById<TextView>(R.id.instagramLimitTextView)
        val youtubePending = view.findViewById<TextView>(R.id.youtubePendingTextView)
        val instagramPending = view.findViewById<TextView>(R.id.instagramPendingTextView)
        val warnSwitch = view.findViewById<MaterialSwitch>(R.id.switch_warn_before_limit)
        val remindSwitch = view.findViewById<MaterialSwitch>(R.id.switch_remind_if_ignored)

        // Daily limit options
        val options = arrayOf("15 min", "30 min", "45 min", "60 min", "75 min", "90 min", "120 min")
        val values = arrayOf(15, 30, 45, 60, 75, 90, 120)

        // Get today's date or a later date
        fun date(daysFromToday: Int): String {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, daysFromToday)
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        }

        // Display current and pending daily limits
        fun displayLimit(
            limitView: TextView,
            pendingView: TextView,
            currentLimit: Long,
            pendingLimit: Long?,
            pendingDate: String?
        ) {
            limitView.text = "$currentLimit min  ›"
            pendingView.visibility = View.GONE
            // Display pending limit if there is one
            if (pendingLimit != null && pendingDate != null && pendingLimit != currentLimit) {

                if (date(0) >= pendingDate) {
                    limitView.text = "$pendingLimit min  ›"
                } else {
                    pendingView.text = "New limit: $pendingLimit min starts tomorrow"
                    pendingView.visibility = View.VISIBLE
                }
            }
        }

        // Save daily limit settings to cache
        fun saveLimitCache(
            appName: String,
            currentLimit: Long,
            pendingLimit: Long?,
            pendingDate: String?
        ) {
            cache.edit().apply {
                putLong("${appName}_limit", currentLimit)
                // Save or remove pending changes
                if (pendingLimit != null && pendingDate != null) {
                    putLong("${appName}_pending", pendingLimit)
                    putString("${appName}_date", pendingDate)
                } else {
                    remove("${appName}_pending")
                    remove("${appName}_date")
                }
                apply()
            }
        }

        // Set up daily limit for tracked app
        fun setupLimit(
            appName: String,
            defaultLimit: Long,
            row: View,
            limitView: TextView,
            pendingView: TextView
        ) {
            val appRef = userRef.collection("trackedApps").document(appName)
            val title = appName.replaceFirstChar { it.uppercase() }

            // Display cached settings
            if (cache.contains("${appName}_limit")) {
                val currentLimit = cache.getLong("${appName}_limit", defaultLimit)
                val pendingLimit = if (cache.contains("${appName}_pending")) {
                    cache.getLong("${appName}_pending", 0L)
                } else {
                    null
                }
                val pendingDate = cache.getString("${appName}_date", null)

                displayLimit(limitView, pendingView, currentLimit, pendingLimit, pendingDate)
            }
            var changingLimit = false

            // Refresh limit from Firestore
            appRef.get().addOnSuccessListener { doc ->
                if (this@SettingsFragment.view !== view || changingLimit) {
                    return@addOnSuccessListener
                }
                // Get current and pending limits from db
                val currentLimit = doc.getLong("dailyLimitMin") ?: defaultLimit
                val pendingLimit = doc.getLong("pendingLimitMin")
                val pendingDate = doc.getString("pendingLimitDate")

                // Remove duplicate or expired pending changes
                if (pendingLimit != null &&
                    (pendingLimit == currentLimit || (pendingDate != null
                            && date(0) >= pendingDate))) {

                    val effectiveLimit = if (pendingLimit == currentLimit) {
                        currentLimit
                    } else {
                        pendingLimit
                    }
                    // Remove pending limit
                    val updates = mutableMapOf<String, Any>(
                        "pendingLimitMin" to FieldValue.delete(),
                        "pendingLimitDate" to FieldValue.delete()
                    )
                    // Activate new limit
                    if (effectiveLimit != currentLimit) {
                        updates["dailyLimitMin"] = effectiveLimit
                    }
                    // Update firestore, cache, and screen
                    appRef.update(updates).addOnSuccessListener {
                        saveLimitCache(appName, effectiveLimit, null, null)

                        if (this@SettingsFragment.view === view) {
                            displayLimit(limitView, pendingView, effectiveLimit, null, null)
                        }
                    }

                } else {
                    // Cache and display current settings
                    saveLimitCache(appName, currentLimit, pendingLimit, pendingDate)

                    displayLimit(
                        limitView, pendingView,
                        currentLimit, pendingLimit, pendingDate
                    )
                }
            }

            // Change daily limit
            row.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("$title Daily Limit")
                    .setItems(options) { dialog, selectedIndex ->
                        val newLimit = values[selectedIndex].toLong()
                        changingLimit = true
                        row.isEnabled = false

                        // Check current limit
                        appRef.get().addOnSuccessListener { doc ->
                            val currentLimit = doc.getLong("dailyLimitMin") ?: defaultLimit
                            val tomorrow = date(1)

                            val updates = if (newLimit == currentLimit) {
                                mapOf(
                                    "pendingLimitMin" to FieldValue.delete(),
                                    "pendingLimitDate" to FieldValue.delete()
                                )
                            } else {
                                mapOf(
                                    "pendingLimitMin" to newLimit,
                                    "pendingLimitDate" to tomorrow
                                )
                            }

                            // Save limit to Firestore
                            appRef.update(updates)
                                .addOnSuccessListener {
                                    val pendingLimit =
                                        if (newLimit == currentLimit) null else newLimit

                                    val pendingDate = if (pendingLimit == null) null else tomorrow

                                    // Update cache
                                    saveLimitCache(appName, currentLimit, pendingLimit, pendingDate)

                                    if (this@SettingsFragment.view === view) {
                                        displayLimit(
                                            limitView, pendingView,
                                            currentLimit, pendingLimit, pendingDate
                                        )
                                    }
                                }.addOnFailureListener {
                                    if (isAdded) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Failed to save limit",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }.addOnCompleteListener {
                                    changingLimit = false
                                    if (this@SettingsFragment.view === view) {
                                        row.isEnabled = true
                                    }
                                }
                        }.addOnFailureListener {
                            changingLimit = false

                            if (this@SettingsFragment.view === view) {
                                row.isEnabled = true
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to load limit",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }.setNegativeButton("Cancel", null)
                    .show()
            }
        }

        // Set up tracked apps
        setupLimit("youtube", 60L, youtubeRow, youtubeLimit, youtubePending)
        setupLimit("instagram", 45L, instagramRow, instagramLimit, instagramPending)

        // Load and save notification switches
        fun setupSwitch(toggle: MaterialSwitch, field: String) {
            toggle.isChecked = cache.getBoolean(field, true)
            toggle.isEnabled = true

            var loading = true
            // Save switch state to db
            toggle.setOnCheckedChangeListener { _, checked ->
                if (!loading) {
                    toggle.isEnabled = false
                    userRef.update(field, checked)
                        .addOnSuccessListener {
                            cache.edit { putBoolean(field, checked) }
                        }
                        .addOnFailureListener {
                            if (this@SettingsFragment.view === view) {
                                loading = true
                                toggle.isChecked = !checked
                                loading = false
                                Toast.makeText(requireContext(), "Failed to save setting", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnCompleteListener {
                            if (this@SettingsFragment.view === view) toggle.isEnabled = true
                        }
                }
            }

            userRef.get().addOnSuccessListener { doc ->
                if (this@SettingsFragment.view === view) {
                    val checked = doc.getBoolean(field) ?: true
                    toggle.isChecked = checked
                    cache.edit { putBoolean(field, checked) }
                    loading = false
                    toggle.isEnabled = true
                }
            }.addOnFailureListener {
                if (this@SettingsFragment.view === view) {
                    loading = false
                    toggle.isEnabled = true
                }
            }
        }
        // Set up notification switches
        setupSwitch(warnSwitch, "warnBeforeLimit")
        setupSwitch(remindSwitch, "remindIfIgnored")

        // Change password
        view.findViewById<View>(R.id.row_change_password).setOnClickListener {
            showChangePasswordDialog(auth)
        }

        // Log out
        view.findViewById<View>(R.id.row_log_out).setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    // Display change password dialog box
    private fun showChangePasswordDialog(auth: FirebaseAuth) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        val current = EditText(requireContext()).apply { hint = "Current Password" }
        val new = EditText(requireContext()).apply { hint = "New Password" }
        val confirm = EditText(requireContext()).apply { hint = "Confirm New Password" }

        val fields = listOf(current, new, confirm)
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        // Hide password text
        fields.forEach {
            it.setSingleLine(true)
            it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layout.addView(it)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            saveButton.setOnClickListener {
                val oldPassword = current.text.toString()
                val newPassword = new.text.toString()
                val confirmation = confirm.text.toString()
                // Validate password fields
                val error = when {
                    oldPassword.isEmpty() || newPassword.isEmpty() || confirmation.isEmpty() ->
                        "All fields are required"
                    newPassword != confirmation -> "Passwords do not match"
                    newPassword.length < 6 -> "Password must be at least 6 characters"
                    newPassword == oldPassword -> "New password must be different"
                    else -> null
                }

                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                saveButton.isEnabled = false
                // Verify current password
                val credential = EmailAuthProvider.getCredential(email, oldPassword)

                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        user.updatePassword(newPassword)
                            .addOnSuccessListener {
                                dialog.dismiss()
                                if (isAdded) Toast.makeText(
                                    requireContext(), "Password updated", Toast.LENGTH_SHORT
                                ).show()
                            }.addOnFailureListener {
                                saveButton.isEnabled = true
                                if (isAdded) Toast.makeText(
                                    requireContext(), "Failed to update password", Toast.LENGTH_SHORT
                                ).show()
                            }
                    }.addOnFailureListener {
                        saveButton.isEnabled = true
                        if (isAdded) Toast.makeText(
                            requireContext(), "Incorrect current password", Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
        dialog.show()
    }
}