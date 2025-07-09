package com.example.konkhmermovie.home

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.FragmentDownloadBinding
import com.example.konkhmermovie.databinding.ItemDownloadBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DownloadFragment : Fragment() {

    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding!!

    private val downloadList = mutableListOf<DownloadItem>()
    private lateinit var adapter: DownloadAdapter

    private val userId: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    private val downloadsRef: DatabaseReference? get() =
        userId?.let { FirebaseDatabase.getInstance().getReference("downloads").child(it) }

    private var downloadManager: DownloadManager? = null
    private val downloadIdToPosition = mutableMapOf<Long, Int>()

    private val handler = Handler(Looper.getMainLooper())
    private val progressChecker = object : Runnable {
        override fun run() {
            val iterator = downloadIdToPosition.entries.iterator()
            while (iterator.hasNext()) {
                val (downloadId, position) = iterator.next()
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager?.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                    val progressPercent = if (bytesTotal > 0) (bytesDownloaded * 100L / bytesTotal).toInt() else 0

                    Log.d("DownloadFragment", "DownloadId: $downloadId, pos: $position, progress: $progressPercent%, status: $status")

                    adapter.updateProgress(position, progressPercent)

                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        iterator.remove()
                    }
                }
                cursor?.close()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔐 Redirect if not logged in
        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(requireContext(), "Please login to access downloads", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_downloadFragment_to_profileFragment)
            return
        }

        // 🔒 Storage permission for Android 9 and below
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE)
            }
        }

        downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        adapter = DownloadAdapter(downloadList) { position, isPaused ->
            if (isPaused) pauseDownload(position) else resumeDownload(position)
        }

        binding.recyclerViewDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDownloads.adapter = adapter

        loadDownloadsFromFirebase()
        handler.post(progressChecker)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(requireContext(), "Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Storage permission denied. Downloads may fail.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadDownloadsFromFirebase() {
        val uid = userId ?: return
        downloadsRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                downloadList.clear()
                downloadIdToPosition.clear()
                var pos = 0
                for (child in snapshot.children) {
                    try {
                        val item = child.getValue(DownloadItem::class.java)
                        if (item != null) {
                            item.id = child.key ?: ""
                            downloadList.add(item)
                            if (item.downloadId != -1L) {
                                downloadIdToPosition[item.downloadId] = pos
                            }
                            pos++
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadFragment", "Skipping invalid item: ${child.key}, error: ${e.message}")
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DownloadFragment", "Failed to read downloads", error.toException())
            }
        })
    }

    private fun pauseDownload(position: Int) {
        val item = downloadList.getOrNull(position) ?: return
        val downloadId = item.downloadId
        if (downloadId != -1L) {
            downloadManager?.remove(downloadId)
            downloadIdToPosition.remove(downloadId)
            adapter.setPaused(position, true)
            Toast.makeText(requireContext(), "Download paused", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeDownload(position: Int) {
        val item = downloadList.getOrNull(position) ?: return
        val validUrl = item.videoUrl

        if (!validUrl.startsWith("http")) {
            Toast.makeText(requireContext(), "Invalid download URL", Toast.LENGTH_SHORT).show()
            return
        }

        val oldDownloadId = item.downloadId
        if (oldDownloadId != -1L) {
            downloadIdToPosition.remove(oldDownloadId)
            adapter.resetPauseState(position)
            adapter.updateProgress(position, 0)
        }

        val request = DownloadManager.Request(Uri.parse(validUrl))
            .setTitle(item.title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${item.title}.mp4")

        val newDownloadId = downloadManager?.enqueue(request) ?: -1L
        if (newDownloadId != -1L) {
            item.downloadId = newDownloadId
            downloadsRef?.child(item.id)?.child("downloadId")?.setValue(newDownloadId)
            downloadIdToPosition[newDownloadId] = position

            Log.d("DownloadFragment", "Resumed download with id: $newDownloadId at position: $position")
            Toast.makeText(requireContext(), "Download resumed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Failed to resume download", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(progressChecker)
        _binding = null
    }

    inner class DownloadAdapter(
        private val downloads: MutableList<DownloadItem>,
        private val pauseResumeClickListener: (position: Int, isPaused: Boolean) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder>() {

        private val progressMap = mutableMapOf<Int, Int>()
        private val pausedMap = mutableMapOf<Int, Boolean>()

        inner class DownloadViewHolder(val itemBinding: ItemDownloadBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root)

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

            when {
                progress in 1..99 && !isPaused -> {
                    holder.itemBinding.textProgress.text = "Downloading... $progress%"
                    holder.itemBinding.textProgress.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                }
                progress >= 100 -> {
                    holder.itemBinding.textProgress.text = "Download successful"
                    holder.itemBinding.textProgress.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light))
                }
                isPaused -> {
                    holder.itemBinding.textProgress.text = "Paused"
                    holder.itemBinding.textProgress.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                }
                else -> {
                    holder.itemBinding.textProgress.text = "0%"
                    holder.itemBinding.textProgress.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                }
            }

            holder.itemBinding.progressBar.progress = progress
            holder.itemBinding.progressBar.visibility = if (!isPaused && progress < 100) View.VISIBLE else View.GONE

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
            if (progress > 0) pausedMap.remove(position)
            if (progress >= 100) pausedMap.remove(position)
            activity?.runOnUiThread { notifyItemChanged(position) }
        }

        fun setPaused(position: Int, paused: Boolean) {
            pausedMap[position] = paused
            activity?.runOnUiThread { notifyItemChanged(position) }
        }

        fun resetPauseState(position: Int) {
            pausedMap.remove(position)
            activity?.runOnUiThread { notifyItemChanged(position) }
        }
    }
}
