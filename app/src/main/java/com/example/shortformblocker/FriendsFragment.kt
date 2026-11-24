package com.example.shortformblocker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Friends screen: search/add friends, pending friend requests, and the friends list.

class FriendsFragment : Fragment() {

    private var requestsListener: ListenerRegistration? = null
    private var friendsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_friends, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize firebase
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid ?: return

        val searchUsername = view.findViewById<EditText>(R.id.et_search_username)
        val addFriendButton = view.findViewById<MaterialButton>(R.id.btn_add_friend)
        val friendRequestsTitle = view.findViewById<TextView>(R.id.friendRequestsTitle)
        val friendRequestsContainer = view.findViewById<LinearLayout>(R.id.friendRequestsContainer)
        val myFriendsTitle = view.findViewById<TextView>(R.id.myFriendsTitle)
        val friendsContainer = view.findViewById<LinearLayout>(R.id.friendsContainer)


        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Search by username
        addFriendButton.setOnClickListener {
            val username = searchUsername.text.toString().trim().lowercase()

            if (username.isEmpty()){
                Toast.makeText(requireContext(), "Enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Find user in userDirectory
            db.collection("userDirectory")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty){
                        Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    } else {
                        val foundUser = result.documents[0]
                        // Stop requests to yourself
                        if (foundUser.id == uid) {
                            Toast.makeText(requireContext(), "You cannot add yourself", Toast.LENGTH_SHORT).show()
                        } else {
                            val foundUsername = foundUser.getString("username")

                            // Send friend request to the selected user
                            val requestData = hashMapOf(
                                "fromUid" to uid,
                                "status" to "pending",
                                "createdAt" to FieldValue.serverTimestamp()
                            )

                            db.collection("users")
                                .document(foundUser.id)
                                .collection("friendRequests")
                                .document(uid)
                                .set(requestData)
                                .addOnSuccessListener {
                                    if (isAdded) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Friend request sent to @$foundUsername",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }.addOnFailureListener {
                                    if (isAdded) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Failed to send friend request",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                        }
                    }
                }.addOnFailureListener {
                    if (isAdded){
                        Toast.makeText(requireContext(), "Failed to search users", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Listen for and load friend requests
        requestsListener = db.collection("users")
            .document(uid)
            .collection("friendRequests")
            .addSnapshotListener { requests, error ->
                if (!isAdded || this@FriendsFragment.view !== view) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    Toast.makeText(requireContext(), "Failed to load friend requests", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                // Clear old requests and display current requests
                friendRequestsContainer.removeAllViews()
                friendRequestsTitle.text = "Friend Requests (${requests?.size() ?: 0})"

                for (request in requests?.documents.orEmpty()) {
                    val senderUid = request.getString("fromUid") ?: continue

                    // Get sender information
                    db.collection("userDirectory")
                        .document(senderUid)
                        .get()
                        .addOnSuccessListener { sender ->
                            if (!isAdded || this@FriendsFragment.view !== view) {
                                return@addOnSuccessListener
                            }

                            val displayName = sender.getString("displayName").orEmpty()
                            val username = sender.getString("username").orEmpty()

                            // Create friend request card
                            val requestView = layoutInflater.inflate(
                                R.layout.item_friend_request,
                                friendRequestsContainer,
                                false
                            )

                            val nameText = requestView.findViewById<TextView>(R.id.requestName)
                            val usernameText = requestView.findViewById<TextView>(R.id.requestUsername)
                            val avatarText = requestView.findViewById<TextView>(R.id.requestAvatar)
                            val acceptButton = requestView.findViewById<MaterialButton>(R.id.btn_accept)
                            val declineButton = requestView.findViewById<MaterialButton>(R.id.btn_decline)

                            // Display sender information
                            nameText.text = displayName
                            usernameText.text = "@$username"

                            avatarText.text = displayName
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .take(2)
                                .map { it.first().uppercaseChar() }
                                .joinToString("")

                            // Handle accepting and declining
                            acceptButton.setOnClickListener {
                                acceptFriendRequest(db, uid, senderUid)
                            }

                            declineButton.setOnClickListener {
                                declineFriendRequest(db, uid, senderUid)
                            }

                            friendRequestsContainer.addView(requestView)
                        }
                }
            }

        // Listen for and load friends
        friendsListener = db.collection("users")
            .document(uid)
            .collection("friends")
            .addSnapshotListener { friends, error ->
                if (!isAdded || this@FriendsFragment.view !== view) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    Toast.makeText(requireContext(), "Failed to load friends", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                // Clear previous friend cards
                friendsContainer.removeAllViews()
                myFriendsTitle.text = "My Friends (${friends?.size() ?: 0})"

                for (friend in friends?.documents.orEmpty()) {
                    val friendUid = friend.id

                    // Get friend information
                    db.collection("userDirectory")
                        .document(friendUid)
                        .get()
                        .addOnSuccessListener { user ->
                            if (!isAdded || this@FriendsFragment.view !== view) {
                                return@addOnSuccessListener
                            }

                            val displayName = user.getString("displayName").orEmpty()
                            val username = user.getString("username").orEmpty()

                            // Create friend card
                            val friendView = layoutInflater.inflate(
                                R.layout.item_friend,
                                friendsContainer,
                                false
                            )

                            val nameText = friendView.findViewById<TextView>(R.id.friendName)
                            val usernameText = friendView.findViewById<TextView>(R.id.friendUsername)
                            val avatarText = friendView.findViewById<TextView>(R.id.friendAvatar)

                            // Display friend's information
                            nameText.text = displayName
                            usernameText.text = "@$username"

                            avatarText.text = displayName
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .take(2)
                                .map { it.first().uppercaseChar() }
                                .joinToString("")

                            friendsContainer.addView(friendView)
                        }
                }
            }
    }

    // Accept a friend request and create friendship for both users
    private fun acceptFriendRequest(
        db: FirebaseFirestore,
        uid: String,
        senderUid: String
    ) {
        val myFriend = db.collection("users")
            .document(uid)
            .collection("friends")
            .document(senderUid)

        val theirFriend = db.collection("users")
            .document(senderUid)
            .collection("friends")
            .document(uid)

        val request = db.collection("users")
            .document(uid)
            .collection("friendRequests")
            .document(senderUid)

        // Update friendship records and friend counts
        val myUser = db.collection("users").document(uid)
        val theirUser = db.collection("users").document(senderUid)

        val batch = db.batch()

        batch.set(myFriend, mapOf("addedAt" to FieldValue.serverTimestamp()))
        batch.set(theirFriend, mapOf("addedAt" to FieldValue.serverTimestamp()))

        batch.update(myUser, "friendsCount", FieldValue.increment(1))
        batch.update(theirUser, "friendsCount", FieldValue.increment(1))
        // Remove pending request
        batch.delete(request)

        batch.commit().addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Friend added", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to accept request", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Decline a friend request
    private fun declineFriendRequest(
        db: FirebaseFirestore,
        uid: String,
        senderUid: String
    ) {
        db.collection("users")
            .document(uid)
            .collection("friendRequests")
            .document(senderUid)
            .delete()
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Friend request declined", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to decline request", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Remove Firestore listeners when leaving the screen
    override fun onDestroyView() {
        requestsListener?.remove()
        friendsListener?.remove()

        requestsListener = null
        friendsListener = null

        super.onDestroyView()
    }
}