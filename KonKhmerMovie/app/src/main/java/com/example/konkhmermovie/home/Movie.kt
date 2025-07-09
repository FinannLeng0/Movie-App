package com.example.konkhmermovie.home

data class Movie(
    val title: String = "",
    val description: String = "No description",
    val imageUrl: String = "",       // for classic movies
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val userId: String? = null,
    var timestamp: Long? = null,
    var id: String? = null
)
