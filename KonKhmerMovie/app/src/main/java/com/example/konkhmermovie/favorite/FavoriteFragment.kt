package com.example.konkhmermovie.favorite

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.FragmentFavoriteBinding
import com.example.konkhmermovie.home.Movie
import com.example.konkhmermovie.home.MovieAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val uid: String? get() = auth.currentUser?.uid

    private val databaseRef: DatabaseReference
        get() = FirebaseDatabase.getInstance().getReference("favorites").child(uid ?: "")

    private lateinit var movieAdapter: MovieAdapter

    private val favoriteKeysMap = mutableMapOf<String, String>()

    // Network var
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var snackbar: Snackbar? = null
    private var hasShownConnectedOnce = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (auth.currentUser == null) {
            findNavController().navigate(R.id.profileFragment)
            return
        }

        setupNetworkCallback()
        setupFavoriteRecyclerView()
        loadFavorites()
    }

    private fun setupFavoriteRecyclerView() {
        movieAdapter = MovieAdapter { movie ->
            val action = FavoriteFragmentDirections.actionFavoriteFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" },
                movie.title.ifEmpty { "No Title" },
                movie.imageUrl.ifEmpty { "" },
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = movieAdapter
        }

        movieAdapter.setOnItemLongClickListener { movie ->
            showRemoveFavoriteDialog(movie)
        }
    }

    private fun loadFavorites() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                val favorites = mutableListOf<Movie>()
                favoriteKeysMap.clear()
                for (child in snapshot.children) {
                    val movie = child.getValue(Movie::class.java)
                    if (movie != null) {
                        favorites.add(movie)
                        favoriteKeysMap[movie.title ?: ""] = child.key ?: ""
                    }
                }
                movieAdapter.submitList(favorites)

                binding.emptyMessage.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                Toast.makeText(requireContext(), "Failed to load favorites: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showRemoveFavoriteDialog(movie: Movie) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Favorite")
            .setMessage("Do you want to remove \"${movie.title}\" from favorites?")
            .setPositiveButton("Yes") { _, _ ->
                removeFavorite(movie)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun removeFavorite(movie: Movie) {
        val key = favoriteKeysMap[movie.title ?: ""]
        if (key != null) {
            databaseRef.child(key).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Removed from favorites", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to remove favorite", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Cannot find favorite to remove", Toast.LENGTH_SHORT).show()
        }
    }

    // Setup network
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (e: Exception) {

        }
    }
}
