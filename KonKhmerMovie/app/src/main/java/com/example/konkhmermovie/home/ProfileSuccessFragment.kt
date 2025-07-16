package com.example.konkhmermovie.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.FragmentProfileSuccessBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class ProfileSuccessFragment : Fragment() {

    private var _binding: FragmentProfileSuccessBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private var userRef: DatabaseReference? = null
    private var selectedImageUri: Uri? = null

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>

    private val PREFS_NAME = "profile_prefs"
    private val KEY_USERNAME = "username"
    private val KEY_PROFILE_IMAGE = "profile_image_url"

    private var loadingDialog: AlertDialog? = null

    private var ivPreviewDialog: ImageView? = null
    private var editDialog: AlertDialog? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var snackbar: Snackbar? = null
    private var hasShownConnectedOnce = false

    private lateinit var videoAdapter: UserMovieAdapter
    private val userVideos = mutableListOf<Movie>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedImageUri = result.data?.data
                ivPreviewDialog?.let {
                    Glide.with(requireContext())
                        .load(selectedImageUri)
                        .circleCrop()
                        .into(it)
                }
                Toast.makeText(requireContext(), "Image selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.d("ProfileSuccessFragment", "✅ onCreateView called")
        _binding = FragmentProfileSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val contactUs = view.findViewById<TextView>(R.id.tvContactUs)
        val dashboard = view.findViewById<TextView>(R.id.tvDashboard)
        val indicator = view.findViewById<View>(R.id.indicator)
        val contactContainer = view.findViewById<LinearLayout>(R.id.contactContainer)
        val dashboardContainer = view.findViewById<LinearLayout>(R.id.dashboardContainer)
        val recyclerViewUserVideos = view.findViewById<RecyclerView>(R.id.recyclerViewUserVideos)
        setupNetworkCallback()

        recyclerViewUserVideos.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewUserVideos.setHasFixedSize(true)
        recyclerViewUserVideos.isNestedScrollingEnabled = false

        videoAdapter = UserMovieAdapter(requireContext(), userVideos,
            onItemClick = { movie ->
                Toast.makeText(requireContext(), "Clicked: ${movie.title}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { movie ->
                deleteVideo(movie)
            }
        )

        recyclerViewUserVideos.adapter = videoAdapter
        binding.recyclerViewUserVideos.adapter = videoAdapter

        fun moveIndicatorTo(tab: TextView) {
            tab.post {
                indicator.layoutParams.width = tab.width
                indicator.requestLayout()
                indicator.animate().x(tab.x).setDuration(200).start()
            }
        }

        moveIndicatorTo(contactUs)
        contactContainer.visibility = View.VISIBLE
        dashboardContainer.visibility = View.GONE

        contactUs.setOnClickListener {
            moveIndicatorTo(contactUs)
            contactContainer.visibility = View.VISIBLE
            dashboardContainer.visibility = View.GONE
        }

        dashboard.setOnClickListener {
            moveIndicatorTo(dashboard)
            contactContainer.visibility = View.GONE
            dashboardContainer.visibility = View.VISIBLE
            auth.currentUser?.let { user -> loadUserVideos(user.uid) }
        }

        val user = auth.currentUser ?: run {
            findNavController().navigate(R.id.profileFragment)
            return
        }

        val uid = user.uid
        val email = user.email ?: "No Email"
        val defaultName = user.displayName ?: email.substringBefore("@")

        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUsername = prefs.getString(KEY_USERNAME, defaultName)
        val cachedProfileImage = prefs.getString(KEY_PROFILE_IMAGE, null)

        binding.tvWelcome.text = cachedUsername
        binding.tvEmail.text = email

        if (!cachedProfileImage.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(cachedProfileImage)
                .placeholder(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivProfile)
        }

        userRef?.get()?.addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val username = snapshot.child("username").getValue(String::class.java) ?: defaultName
                val profileImage = snapshot.child("profileImage").getValue(String::class.java)

                if (username != cachedUsername) {
                    binding.tvWelcome.text = username
                    prefs.edit().putString(KEY_USERNAME, username).apply()
                }

                if (!profileImage.isNullOrEmpty() && profileImage != cachedProfileImage) {
                    Glide.with(requireContext())
                        .load(profileImage)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(binding.ivProfile)
                    prefs.edit().putString(KEY_PROFILE_IMAGE, profileImage).apply()
                }
            } else {
                auth.signOut()
                Toast.makeText(requireContext(), "Your account has been removed.", Toast.LENGTH_LONG).show()
                findNavController().navigate(R.id.profileFragment)
            }
        }

        binding.facebookRow.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/")))
        }
        binding.rowTelegram.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://web.telegram.org/k/")))
        }
        binding.rowTikTok.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/")))
        }

        val mapWebView = view.findViewById<WebView>(R.id.mapWebView)
        mapWebView.settings.javaScriptEnabled = true
        mapWebView.settings.loadWithOverviewMode = true
        mapWebView.settings.useWideViewPort = true
        mapWebView.loadDataWithBaseURL(
            null,
            """
            <iframe 
                src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3907.9204169065324!2d104.852139380158!3d11.62902115912133!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x3109527b5a6c6599%3A0x947e61c3ff00c21d!2sACLEDA%20University%20of%20Business!5e0!3m2!1sen!2skh!4v1751901515227!5m2!1sen!2skh" 
                width="100%" 
                height="100%" 
                style="border:0;" 
                allowfullscreen="" 
                loading="lazy" 
                referrerpolicy="no-referrer-when-downgrade">
            </iframe>
            """.trimIndent(),
            "text/html", "utf-8", null
        )

        binding.btnSignOut.setOnClickListener {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Yes") { _, _ ->
                    auth.signOut()
                    prefs.edit().clear().apply()
                    Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.profileFragment)
                }
                .setNegativeButton("No", null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.WHITE)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.WHITE)

                val messageView = dialog.findViewById<TextView>(android.R.id.message)
                messageView?.setTextColor(android.graphics.Color.WHITE)
                val titleView = dialog.findViewById<TextView>(android.R.id.title)
                titleView?.setTextColor(android.graphics.Color.WHITE)

                dialog.window?.setBackgroundDrawable(
                    GradientDrawable().apply {
                        cornerRadius = 30f
                        setColor(android.graphics.Color.parseColor("#222222")) // dark color
                    }
                )
            }

            dialog.show()
        }

        binding.backButton.setOnClickListener {
            val navOptions = NavOptions.Builder()
                .setEnterAnim(R.anim.slide_in_left)
                .setExitAnim(R.anim.slide_out_right)
                .build()
            findNavController().navigate(R.id.action_profileSuccessFragment_to_homeFragment, null, navOptions)
        }

        binding.btnEdit.setOnClickListener {
            showEditDialog()
        }
    }
    private fun setupNetworkCallback() {
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    snackbar?.dismiss()

                    if (!hasShownConnectedOnce) {
                        hasShownConnectedOnce = true
                        val root = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
                        Snackbar.make(root, "Internet Connected", Snackbar.LENGTH_SHORT)
                            .setDuration(3000)
                            .setAnchorView(R.id.bottom_navigation)
                            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.success_green))
                            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                            .show()
                    }
                }
            }

            override fun onLost(network: Network) {
                activity?.runOnUiThread {
                    hasShownConnectedOnce = false
                    showNoInternetSnackbar()
                }
            }
        }

        connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
        if (!isNetworkConnected()) {
            hasShownConnectedOnce = false
            showNoInternetSnackbar()
        } else {
            hasShownConnectedOnce = true
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternetSnackbar() {
        if (snackbar?.isShown == true) return

        val root = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
        snackbar = Snackbar.make(root, "No Internet Connection", Snackbar.LENGTH_LONG)
            .setDuration(5000)
            .setAnchorView(R.id.bottom_navigation)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar?.show()
    }



    private fun showLoadingDialog() {
        val loadingView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null)
        loadingDialog = AlertDialog.Builder(requireContext())
            .setView(loadingView)
            .setCancelable(false)
            .create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }

    private fun loadUserVideos(uid: String) {
        val videosRef = FirebaseDatabase.getInstance().getReference("videos")
        videosRef.orderByChild("userId").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userVideos.clear()
                    for (videoSnapshot in snapshot.children) {
                        val movie = videoSnapshot.getValue(Movie::class.java)
                        movie?.id = videoSnapshot.key
                        movie?.let { userVideos.add(it) }
                    }
                    if (userVideos.isEmpty()) {
                        Toast.makeText(requireContext(), "No videos posted yet", Toast.LENGTH_SHORT).show()
                    }
                    videoAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load videos: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun deleteVideo(movie: Movie) {
        if (movie.id == null) return

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Delete Video")
        builder.setMessage("Are you sure you want to delete this video?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            val dbRef = FirebaseDatabase.getInstance().getReference("videos").child(movie.id!!)
            val storageRef = FirebaseStorage.getInstance().reference
            val videoUrl = movie.videoUrl

            if (!videoUrl.isNullOrEmpty()) {
                try {
                    val storagePath = Uri.parse(videoUrl).path?.substringAfter("/o/")?.substringBefore("?")
                    if (storagePath != null) {
                        val fileRef = storageRef.child(Uri.decode(storagePath))
                        fileRef.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            dbRef.removeValue().addOnSuccessListener {
                userVideos.remove(movie)
                videoAdapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "Video deleted", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()

        // Change background and text color after showing
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(android.graphics.Color.WHITE)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(android.graphics.Color.WHITE)
            }

            dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = 30f
                setColor(android.graphics.Color.parseColor("#222222")) // dark background
            })
        }

        dialog.show()
    }

    private fun showEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etNewName = dialogView.findViewById<EditText>(R.id.etNewName)
        val btnSelectImage = dialogView.findViewById<TextView>(R.id.btnSelectImage)
        ivPreviewDialog = dialogView.findViewById(R.id.ivPreview)

        etNewName.setText(binding.tvWelcome.text)

        Glide.with(requireContext())
            .load(selectedImageUri ?: binding.ivProfile.drawable)
            .circleCrop()
            .into(ivPreviewDialog!!)

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        val redColor = android.graphics.Color.parseColor("#FF0000")

        val customTitle = TextView(requireContext()).apply {
            text = "Edit Profile"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(redColor)
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER
            textSize = 20f
        }

        editDialog = AlertDialog.Builder(requireContext())
            .setCustomTitle(customTitle)
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { dialog, _ ->
                selectedImageUri = null
                dialog.dismiss()
            }
            .create()

        editDialog?.setOnShowListener {
            val saveButton = editDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelButton = editDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)

            saveButton?.setTextColor(android.graphics.Color.WHITE)
            cancelButton?.setTextColor(android.graphics.Color.WHITE)

            val bgDrawable = GradientDrawable().apply {
                setColor(redColor)
                cornerRadius = 50f
            }

            saveButton?.background = bgDrawable
            cancelButton?.background = bgDrawable

            saveButton?.setOnClickListener {
                val newName = etNewName.text.toString().trim()
                val user = auth.currentUser

                if (user != null && newName.isNotEmpty()) {
                    val uid = user.uid
                    val email = user.email ?: ""
                    val ref = FirebaseDatabase.getInstance().getReference("users").child(uid)

                    val userMap = mutableMapOf<String, Any>(
                        "uid" to uid,
                        "email" to email,
                        "username" to newName
                    )

                    if (selectedImageUri != null) {
                        showLoadingDialog()

                        val imageRef = FirebaseStorage.getInstance().reference
                            .child("profile_images/${uid}_${UUID.randomUUID()}.jpg")

                        imageRef.putFile(selectedImageUri!!)
                            .addOnSuccessListener {
                                imageRef.downloadUrl.addOnSuccessListener { uri ->
                                    userMap["profileImage"] = uri.toString()
                                    ref.setValue(userMap).addOnSuccessListener {
                                        updateProfileUI(newName, uri.toString())
                                        hideLoadingDialog()
                                        editDialog?.dismiss()
                                    }
                                }
                            }
                            .addOnFailureListener {
                                hideLoadingDialog()
                                Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        showLoadingDialog()
                        ref.setValue(userMap).addOnSuccessListener {
                            updateProfileUI(newName, null)
                            hideLoadingDialog()
                            editDialog?.dismiss()
                        }.addOnFailureListener {
                            hideLoadingDialog()
                            Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show()
                }
            }
        }

        editDialog?.show()
        editDialog?.window?.setBackgroundDrawableResource(R.drawable.dialog_red_rounded)
    }

    private fun updateProfileUI(newName: String, profileUrl: String?) {
        binding.tvWelcome.text = newName
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USERNAME, newName).apply()

        profileUrl?.let {
            Glide.with(requireContext())
                .load(it)
                .placeholder(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivProfile)
            prefs.edit().putString(KEY_PROFILE_IMAGE, it).apply()
        }

        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
        selectedImageUri = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        ivPreviewDialog = null
        editDialog = null
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (_: Exception) {}

    }

}
