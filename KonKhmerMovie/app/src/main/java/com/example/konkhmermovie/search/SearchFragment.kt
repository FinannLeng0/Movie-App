package com.example.konkhmermovie.search

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup as AndroidViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.FragmentSearchBinding
import com.example.konkhmermovie.home.Movie
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ThumbnailMovieAdapter
    private val allMovies = mutableListOf<Movie>()

    private var snackbar: Snackbar? = null
    private var hasShownConnectedOnce = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyCustomFontToAllText(binding.root)
        setupNetworkCallback()

        adapter = ThumbnailMovieAdapter { movie ->
            val imageToShow = if (movie.thumbnailUrl.isNotEmpty()) movie.thumbnailUrl else movie.imageUrl
            val action = SearchFragmentDirections.actionSearchFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" },
                movie.title.ifEmpty { "No Title" },
                imageToShow,
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        binding.searchRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.searchRecyclerView.adapter = adapter

        binding.noResultLayout.visibility = View.GONE
        binding.searchRecyclerView.visibility = View.GONE
        binding.cancelText.visibility = View.GONE

        loadAllMovies()

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            binding.cancelText.isVisible = hasFocus
        }

        binding.cancelText.setOnClickListener {
            binding.searchBar.setText("")
            binding.searchBar.clearFocus()
            binding.cancelText.visibility = View.GONE
            binding.noResultLayout.visibility = View.GONE
            binding.searchRecyclerView.visibility = View.GONE
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(query: CharSequence?, start: Int, before: Int, count: Int) {
                filterMovies(query.toString())
            }
        })
    }

    private fun setupNetworkCallback() {
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    snackbar?.dismiss()

                    if (!hasShownConnectedOnce) {
                        hasShownConnectedOnce = true

                        val coordinatorLayout = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
                        Snackbar.make(coordinatorLayout, "Internet Connected", Snackbar.LENGTH_SHORT)
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

        val coordinatorLayout = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
        snackbar = Snackbar.make(coordinatorLayout, "No Internet Connection", Snackbar.LENGTH_LONG)
            .setDuration(5000) // 👈 5 seconds
            .setAnchorView(R.id.bottom_navigation)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar?.show()
    }

    private fun loadAllMovies() {
        allMovies.clear()
        val videosRef = FirebaseDatabase.getInstance().getReference("videos")
        val moviesRef = FirebaseDatabase.getInstance().getReference("movies")

        videosRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (videoSnapshot in snapshot.children) {
                    val video = videoSnapshot.getValue(Movie::class.java)
                    if (video != null) allMovies.add(video)
                }

                moviesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isAdded || _binding == null) return

                        for (categorySnapshot in snapshot.children) {
                            for (movieSnapshot in categorySnapshot.children) {
                                val movie = movieSnapshot.getValue(Movie::class.java)
                                if (movie != null) allMovies.add(movie)
                            }
                        }

                        _binding?.let {
                            filterMovies(it.searchBar.text.toString())
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun filterMovies(query: String) {
        val filtered = if (query.isEmpty()) {
            emptyList()
        } else {
            allMovies.filter {
                it.title.startsWith(query, ignoreCase = true)
            }
        }

        if (filtered.isNotEmpty()) {
            adapter.submitList(filtered)
            binding.noResultLayout.visibility = View.GONE
            binding.searchRecyclerView.visibility = View.VISIBLE
        } else {
            adapter.submitList(emptyList())
            binding.noResultLayout.visibility = View.VISIBLE
            binding.searchRecyclerView.visibility = View.GONE
        }
    }

    private fun applyCustomFontToAllText(view: View) {
        val typeface = ResourcesCompat.getFont(requireContext(), R.font.movie) ?: return
        when (view) {
            is TextView -> view.typeface = typeface
            is AndroidViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyCustomFontToAllText(view.getChildAt(i))
                }
            }
        }
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
