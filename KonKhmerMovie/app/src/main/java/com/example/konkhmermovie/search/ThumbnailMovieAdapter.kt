package com.example.konkhmermovie.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.ItemThumbnailMovieBinding
import com.example.konkhmermovie.home.Movie

class ThumbnailMovieAdapter(
    private val onItemClick: (Movie) -> Unit
) : ListAdapter<Movie, ThumbnailMovieAdapter.MovieViewHolder>(DiffCallback()) {

    inner class MovieViewHolder(private val binding: ItemThumbnailMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: Movie) {
            binding.movieTitle.text = movie.title

            val imageUrlToLoad = if (movie.thumbnailUrl.isNotEmpty()) {
                movie.thumbnailUrl
            } else {
                movie.imageUrl
            }

            Glide.with(binding.movieImage.context)
                .load(movie.thumbnailUrl.ifEmpty { movie.imageUrl })
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .into(binding.movieImage)

            binding.root.setOnClickListener {
                onItemClick(movie)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie) =
            oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: Movie, newItem: Movie) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = ItemThumbnailMovieBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MovieViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
