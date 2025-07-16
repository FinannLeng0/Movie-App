package com.example.konkhmermovie.home

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.ItemMovieBinding

class MovieAdapter(
    private val onMovieClick: ((Movie) -> Unit)? = null
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    private val movieList = mutableListOf<Movie>()
    private var onItemLongClickListener: ((Movie) -> Unit)? = null

    fun submitList(movies: List<Movie>) {
        movieList.clear()
        movieList.addAll(movies)
        notifyDataSetChanged()
    }

    fun setOnItemLongClickListener(listener: (Movie) -> Unit) {
        onItemLongClickListener = listener
    }

    private fun getItem(position: Int): Movie = movieList[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovieViewHolder(binding)
    }

    override fun getItemCount(): Int = movieList.size

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = getItem(position)
        holder.bind(movie)
    }

    inner class MovieViewHolder(private val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: Movie) {
            binding.movieTitle.text = movie.title

            val imageToLoad = if (movie.thumbnailUrl.isNotEmpty()) movie.thumbnailUrl else movie.imageUrl

            Glide.with(binding.root.context)
                .load(imageToLoad)
                .placeholder(R.drawable.placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(500))
                .error(android.R.color.holo_red_dark)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.movieImage)

            binding.titleOverlay.visibility = View.GONE
            binding.titleOverlay.alpha = 0f

            val overlay = binding.titleOverlay

            val touchListener = View.OnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        overlay.apply {
                            visibility = View.VISIBLE
                            animate().alpha(1f).setDuration(150).start()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        overlay.animate().alpha(0f).setDuration(150)
                            .withEndAction { overlay.visibility = View.GONE }
                            .start()
                        onMovieClick?.invoke(movie)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        overlay.animate().alpha(0f).setDuration(150)
                            .withEndAction { overlay.visibility = View.GONE }
                            .start()
                        true
                    }
                    else -> false
                }
            }

            binding.movieImage.setOnTouchListener(touchListener)
            overlay.setOnTouchListener(touchListener)

            binding.root.setOnClickListener {
                onMovieClick?.invoke(movie)
            }

            binding.root.setOnLongClickListener {
                onItemLongClickListener?.invoke(movie)
                true
            }
        }
    }
}
