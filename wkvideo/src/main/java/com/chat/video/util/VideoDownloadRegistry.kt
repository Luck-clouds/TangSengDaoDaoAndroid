package com.chat.video.util

/**
 * 小视频下载状态注册器
 * Created by Luckclouds.
 */

import java.util.concurrent.ConcurrentHashMap

object VideoDownloadRegistry {
    private val downloadingUrls = ConcurrentHashMap.newKeySet<String>()

    fun markDownloading(url: String): Boolean = downloadingUrls.add(url)

    fun isDownloading(url: String): Boolean = downloadingUrls.contains(url)

    fun clear(url: String) {
        downloadingUrls.remove(url)
    }
}
