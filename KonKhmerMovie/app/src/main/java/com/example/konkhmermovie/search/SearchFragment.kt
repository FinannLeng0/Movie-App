package com.example.konkhmermovie.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.konkhmermovie.databinding.FragmentSearchBinding
import com.example.konkhmermovie.home.Movie
import com.google.firebase.database.*

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ThumbnailMovieAdapter
    private val allMovies = mutableListOf<Movie>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

                    override fun onCancelled(error: DatabaseError) {
                        if (!isAdded || _binding == null) return
                        _binding?.let {
                            filterMovies(it.searchBar.text.toString())
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
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

                    override fun onCancelled(error: DatabaseError) {
                        if (!isAdded || _binding == null) return
                        _binding?.let {
                            filterMovies(it.searchBar.text.toString())
                        }
                    }
                })
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
