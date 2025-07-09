package com.example.konkhmermovie.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konkhmermovie.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val dbRef = FirebaseDatabase.getInstance().getReference("videos")

    private lateinit var userMovieAdapter: UserMovieAdapter
    private val movieList = mutableListOf<Movie>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userMovieAdapter = UserMovieAdapter(requireContext(), movieList,
            onItemClick = { movie ->
                Toast.makeText(requireContext(), "Clicked: ${movie.title}", Toast.LENGTH_SHORT).show()
                // Handle item click (navigate, play video etc.)
            },
            onDeleteClick = { movie ->
                confirmDeleteMovie(movie)
            }
        )

        binding.recyclerViewMovies.apply {
            adapter = userMovieAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            isNestedScrollingEnabled = true
        }

        loadUserVideos()
    }

    private fun loadUserVideos() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            Log.w("DashboardFragment", "User not logged in")
            return
        }
        Log.i("DashboardFragment", "Loading videos for userId=$currentUserId")

        dbRef.orderByChild("userId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    movieList.clear()
                    for (videoSnapshot in snapshot.children) {
                        val movie = videoSnapshot.getValue(Movie::class.java)
                        if (movie != null) {
                            movie.id = videoSnapshot.key
                            movieList.add(movie)
                        }
                    }

                    binding.tvNoVideos.visibility = if (movieList.isEmpty()) View.VISIBLE else View.GONE

                    userMovieAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load videos: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("DashboardFragment", "Firebase load cancelled", error.toException())
                }
            })
    }

    private fun confirmDeleteMovie(movie: Movie) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Video")
            .setMessage("Are you sure you want to delete \"${movie.title}\"?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteMovie(movie)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteMovie(movie: Movie) {
        val videoId = movie.id ?: return

        // Remove from Firebase Realtime Database
        val videoRef = dbRef.child(videoId)
        videoRef.removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Video deleted", Toast.LENGTH_SHORT).show()

                // Remove from local list and notify adapter
                movieList.remove(movie)
                userMovieAdapter.notifyDataSetChanged()

                binding.tvNoVideos.visibility = if (movieList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to delete: ${it.message}", Toast.LENGTH_SHORT).show()
            }

        // Remove video and thumbnail from Firebase Storage if available
        movie.videoUrl?.let { url ->
            FirebaseStorage.getInstance().getReferenceFromUrl(url).delete()
        }
        movie.thumbnailUrl?.let { url ->
            FirebaseStorage.getInstance().getReferenceFromUrl(url).delete()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
