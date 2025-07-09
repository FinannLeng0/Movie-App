package com.example.konkhmermovie.home

object DownloadTracker {
    // Maps downloadId (from DownloadManager) to filename
    val downloads = mutableMapOf<Long, String>()

    // Maps filename to thumbnail URL (imageUrl)
    val thumbnails = mutableMapOf<String, String>()
}