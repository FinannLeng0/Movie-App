package com.example.konkhmermovie.home

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*

class HomeViewModel : ViewModel() {

    val animeMovies = MutableLiveData<List<Movie>>()
    val banners = MutableLiveData<List<Banner>>()
    val khmerMovies = MutableLiveData<List<Movie>>()
    val popularMovies = MutableLiveData<List<Movie>>()
    val favoriteMovies = MutableLiveData<List<Movie>>()

    private val database = FirebaseDatabase.getInstance()

    init {
        loadMovies("movies/anime", animeMovies)
        loadMovies("movies/khmer", khmerMovies)
        loadMovies("movies/popular", popularMovies)
        loadMovies("favorites", favoriteMovies)
        loadBanners()
    }

    private fun loadMovies(path: String, targetLiveData: MutableLiveData<List<Movie>>) {
        val ref = database.getReference(path)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Movie>()
                for (child in snapshot.children) {
                    val movie = child.getValue(Movie::class.java)
                    if (movie != null) list.add(movie)
                }

                Log.d("FIREBASE", "Loaded ${list.size} movies from $path")
                targetLiveData.postValue(list)
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }


    private fun loadBanners() {
        val ref = database.getReference("banners")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Banner>()
                for (child in snapshot.children) {
                    val banner = child.getValue(Banner::class.java)
                    if (banner != null) list.add(banner)
                }
                banners.postValue(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeViewModel", "Failed to load banners", error.toException())
            }
        })
    }
}
