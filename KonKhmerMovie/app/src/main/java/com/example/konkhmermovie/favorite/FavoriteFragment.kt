package com.example.konkhmermovie.favorite

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.konkhmermovie.databinding.FragmentFavoriteBinding
import com.example.konkhmermovie.home.Movie
import com.example.konkhmermovie.home.MovieAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.konkhmermovie.R

class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val uid: String? get() = auth.currentUser?.uid

    private val databaseRef: DatabaseReference
        get() = FirebaseDatabase.getInstance().getReference("favorites").child(uid ?: "")

    private lateinit var movieAdapter: MovieAdapter

    private val favoriteKeysMap = mutableMapOf<String, String>()

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
                if (_binding == null) return  // <<== PREVENT CRASH HERE

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

                if (favorites.isEmpty()) {
                    binding.emptyMessage.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyMessage.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return  // <<== PREVENT CRASH HERE
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
