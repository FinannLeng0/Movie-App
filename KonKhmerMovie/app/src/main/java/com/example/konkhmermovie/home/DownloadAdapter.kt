package com.example.konkhmermovie.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.ItemDownloadBinding

class DownloadAdapter(
    private val downloads: MutableList<DownloadItem>,
    private val pauseResumeClickListener: (position: Int, isPaused: Boolean) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder>() {

    private val progressMap = mutableMapOf<Int, Int>()
    private val pausedMap = mutableMapOf<Int, Boolean>()

    inner class DownloadViewHolder(val itemBinding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(itemBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun getItemCount(): Int = downloads.size

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        val item = downloads[position]
        holder.itemBinding.textTitle.text = item.title

        Glide.with(holder.itemView)
            .load(item.imageUrl)
            .placeholder(R.drawable.ic_placeholder)
            .into(holder.itemBinding.imageThumbnail)

        val progress = progressMap[position] ?: 0
        val isPaused = pausedMap[position] ?: false

        holder.itemBinding.textProgress.text = when {
            progress in 1..99 && !isPaused -> "Downloading... $progress%"
            progress >= 100 -> "Download successful"
            isPaused -> "Paused"
            else -> "0%"
        }

        holder.itemBinding.progressBar.visibility =
            if (progress in 1..99 && !isPaused) View.VISIBLE else View.GONE
        holder.itemBinding.progressBar.progress = progress

        if (progress < 100) {
            holder.itemBinding.textPause.visibility = View.VISIBLE
            holder.itemBinding.textPause.text = if (isPaused) "Resume" else "Pause"
        } else {
            holder.itemBinding.textPause.visibility = View.GONE
        }

        holder.itemBinding.textPause.setOnClickListener {
            val newPausedState = !isPaused
            pausedMap[position] = newPausedState
            notifyItemChanged(position)
            pauseResumeClickListener(position, newPausedState)
        }
    }

    fun updateProgress(position: Int, progress: Int) {
        progressMap[position] = progress
        if (progress >= 100) pausedMap.remove(position)
        if (position in downloads.indices) {
            notifyItemChanged(position)
        }
    }

    fun setPaused(position: Int, paused: Boolean) {
        pausedMap[position] = paused
        if (position in downloads.indices) {
            notifyItemChanged(position)
        }
    }
}
