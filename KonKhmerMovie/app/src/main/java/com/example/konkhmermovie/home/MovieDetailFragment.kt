package com.example.konkhmermovie.home

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.FragmentMovieDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat


class MovieDetailFragment : Fragment() {

    companion object {

    }
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasShownConnectedOnce = false
    private var snackbar: Snackbar? = null

    private val auth = FirebaseAuth.getInstance()
    private val uid get() = auth.currentUser?.uid

    private val favRef get() = uid?.let {
        FirebaseDatabase.getInstance().getReference("favorites").child(it)
    }

    private var _binding: FragmentMovieDetailBinding? = null
    private val binding get() = _binding!!
    private val args: MovieDetailFragmentArgs by navArgs()

    private var isFavorite = false
    private var resolvedVideoUrl: String? = null
    private var isVideoStarted = false

    private val movieKey: String
        get() = args.title
            .replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")

    private var player: ExoPlayer? = null
    private val playerView: PlayerView get() = binding.playerView

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _binding?.let { binding ->
                if (isPlaying) {
                    binding.thumbnailImage.visibility = View.GONE
                    binding.playButton.visibility = View.GONE
                    isVideoStarted = true
                } else {
                    if (!isVideoStarted) {
                        binding.thumbnailImage.visibility = View.VISIBLE
                        binding.playButton.visibility = View.VISIBLE
                        binding.thumbnailImage.alpha = 1f
                    } else {
                        binding.thumbnailImage.visibility = View.GONE
                        binding.playButton.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private val fullscreenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val position = data?.getLongExtra("position", 0L) ?: 0L
            val isPlaying = data?.getBooleanExtra("isPlaying", false) ?: false

            if (_binding == null) return@registerForActivityResult

            playerView.player = player
            player?.seekTo(position)
            player?.playWhenReady = isPlaying

            if (isPlaying) {
                binding.thumbnailImage.visibility = View.GONE
                binding.playButton.visibility = View.GONE
                isVideoStarted = true
            } else {
                binding.thumbnailImage.visibility = View.GONE
                binding.playButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMovieDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        setupNetworkCallback()

        player = ExoPlayerHolder.getPlayer(requireContext())

        binding.detailTitle.text = args.title.ifEmpty { "No Title" }
        binding.detailDescription.text = args.description ?: ""
        binding.detailDescription.visibility =
            if (args.description.isNullOrEmpty()) View.GONE else View.VISIBLE

        val videoUrlOrFilename = args.videoUrl ?: ""
        if (videoUrlOrFilename.isNotEmpty()) {
            resolveAndInitialize(videoUrlOrFilename)
        } else {
            Toast.makeText(requireContext(), "No video URL", Toast.LENGTH_SHORT).show()
        }

        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.favoriteIcon.setOnClickListener { toggleFavorite()
        }

        binding.downloadIcon.setOnClickListener {
            val videoUrl = args.videoUrl ?: return@setOnClickListener

            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                Toast.makeText(requireContext(), "Please log in to download", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val uri = Uri.parse(videoUrl)
            val fileName = uri.lastPathSegment?.substringAfter("%2F")?.substringBefore("?") ?: "video.mp4"

            val request = DownloadManager.Request(uri).apply {
                setTitle("Downloading video")
                setDescription(fileName)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), fileName)
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(file)
            requireContext().sendBroadcast(intent)

            Toast.makeText(requireContext(), "Download started", Toast.LENGTH_SHORT).show()

            val downloadsRef = FirebaseDatabase.getInstance().getReference("downloads").child(userId)
            val downloadRecord = mapOf(
                "title" to args.title,
                "videoUrl" to args.videoUrl,
                "imageUrl" to args.imageUrl,
                "downloadedAt" to System.currentTimeMillis(),
                "downloadId" to downloadId
            )
            downloadsRef.child(movieKey).setValue(downloadRecord)
                .addOnSuccessListener {
                    Log.d("MovieDetailFragment", "Download logged in Firebase")
                }
                .addOnFailureListener {
                    Log.d("YourTag", "Please login to see more video...")
                }
        }

        checkIfFavorite()
        setupMoreMovies()
    }
    private fun setupNetworkCallback() {
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    snackbar?.dismiss()
                    if (!hasShownConnectedOnce) {
                        hasShownConnectedOnce = true
                        val rootView = binding.root
                        Snackbar.make(rootView, "Internet Connected", Snackbar.LENGTH_SHORT)
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

        val rootView = binding.root
        snackbar = Snackbar.make(rootView, "No Internet Connection", Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar?.show()
    }

    private fun resolveAndInitialize(videoUrlOrFilename: String) {
        if (videoUrlOrFilename.startsWith("https://")) {
            resolvedVideoUrl = videoUrlOrFilename
            initializePlayer(videoUrlOrFilename)
        } else {
            val storageRef = FirebaseStorage.getInstance().reference.child("video/$videoUrlOrFilename")
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                resolvedVideoUrl = uri.toString()
                initializePlayer(uri.toString())
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Please login to watch more video...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer(videoUrl: String) {
        player?.removeListener(playerListener)
        player?.stop()
        player?.clearMediaItems()

        Glide.with(this)
            .load(args.imageUrl)
            .placeholder(R.drawable.ic_placeholder)
            .into(binding.thumbnailImage)

        binding.thumbnailImage.visibility = View.VISIBLE
        binding.thumbnailImage.alpha = 1f
        binding.playButton.visibility = View.VISIBLE

        val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = false

        playerView.player = player
        playerView.useController = true
        playerView.controllerShowTimeoutMs = 2000
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)

        playerView.setOnClickListener {
            playerView.showController()
        }

        binding.playButton.setOnClickListener {
            binding.thumbnailImage.animate().alpha(0.3f).setDuration(150).start()
            binding.playButton.visibility = View.GONE
            player?.playWhenReady = true
        }

        player?.addListener(playerListener)

        binding.fullscreenOverlayButton.setOnClickListener {
            player?.let { exo ->
                val intent = Intent(requireContext(), FullscreenVideoActivity::class.java)
                intent.putExtra("videoUrl", resolvedVideoUrl)
                intent.putExtra("position", exo.currentPosition)
                intent.putExtra("isPlaying", exo.isPlaying)
                playerView.player = null
                exo.pause()
                fullscreenLauncher.launch(intent)
            }
        }
    }


    private fun checkIfFavorite() {
        val userFavRef = favRef
        if (uid == null || userFavRef == null) {
            if (_binding != null) binding.favoriteIcon.setImageResource(R.drawable.ic_favorite_border)
            return
        }
        userFavRef.child(movieKey).get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            isFavorite = snapshot.exists()
            binding.favoriteIcon.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )
        }
    }

    private fun toggleFavorite() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "Please log in to add favorites", Toast.LENGTH_SHORT).show()
            return
        }

        val userFavRef = FirebaseDatabase.getInstance().getReference("favorites").child(uid)

        if (!isFavorite) {
            val movie = Movie(
                title = args.title,
                description = args.description,
                imageUrl = args.imageUrl,
                videoUrl = args.videoUrl
            )
            userFavRef.child(movieKey).setValue(movie).addOnSuccessListener {
                Toast.makeText(requireContext(), "Added to Favorites", Toast.LENGTH_SHORT).show()
                isFavorite = true
                binding.favoriteIcon.setImageResource(R.drawable.ic_favorite_filled)
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add favorite", Toast.LENGTH_SHORT).show()
            }
        } else {
            userFavRef.child(movieKey).removeValue().addOnSuccessListener {
                Toast.makeText(requireContext(), "Removed from Favorites", Toast.LENGTH_SHORT).show()
                isFavorite = false
                binding.favoriteIcon.setImageResource(R.drawable.ic_favorite_border)
            }
        }
    }

    private fun setupMoreMovies() {
        val adapter = MovieAdapter { selectedMovie ->
            if (selectedMovie.title != args.title) {
                playerView.player = null
                val action = MovieDetailFragmentDirections.actionMovieDetailFragmentSelf(
                    title = selectedMovie.title,
                    description = selectedMovie.description,
                    imageUrl = selectedMovie.thumbnailUrl.ifEmpty { selectedMovie.imageUrl },
                    videoUrl = selectedMovie.videoUrl
                )
                findNavController().navigate(action)
            }
        }

        binding.moreMoviesRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.moreMoviesRecycler.adapter = adapter

        val videosRef = FirebaseDatabase.getInstance().getReference("videos")
        videosRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val movieList = mutableListOf<Movie>()
                for (child in snapshot.children) {
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val description = child.child("description").getValue(String::class.java) ?: ""
                    val thumbnailUrl = child.child("thumbnailUrl").getValue(String::class.java) ?: ""
                    val videoUrl = child.child("videoUrl").getValue(String::class.java) ?: ""

                    if (title != args.title) {
                        movieList.add(
                            Movie(
                                title = title,
                                description = description,
                                thumbnailUrl = thumbnailUrl,
                                videoUrl = videoUrl
                            )
                        )
                    }
                }
                adapter.submitList(movieList.shuffled().take(6))
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                Toast.makeText(requireContext(), "Please Login To Watch More Movies...", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        playerView.player = player
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        playerView.player = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.removeListener(playerListener)
        playerView.player = null
        _binding = null

        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (_: Exception) { }
    }

}
