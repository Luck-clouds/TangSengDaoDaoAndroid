package com.chat.video.util

/**
 * 小视频 UI 工具集
 * Created by Luckclouds.
 */

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.chat.base.WKBaseApplication
import com.chat.base.utils.ImageUtils
import com.chat.base.utils.WKFileUtils
import java.io.File
import java.util.Locale

object VideoUiUtils {
    fun formatDuration(second: Int): String {
        val safeSecond = second.coerceAtLeast(0)
        val minute = safeSecond / 60
        val remain = safeSecond % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minute, remain)
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0KB"
        val kb = 1024f
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            size >= gb -> String.format(Locale.getDefault(), "%.1fGB", size / gb)
            size >= mb -> String.format(Locale.getDefault(), "%.1fMB", size / mb)
            size >= kb -> String.format(Locale.getDefault(), "%.0fKB", size / kb)
            else -> "${size}B"
        }
    }

    fun resolveBubbleSize(width: Int, height: Int): IntArray {
        val safeWidth = if (width > 0) width else 720
        val safeHeight = if (height > 0) height else 1280
        return ImageUtils.getInstance().getImageWidthAndHeightToTalk(safeWidth, safeHeight)
    }

    fun ensureParent(path: String) {
        File(path).parentFile?.let {
            WKFileUtils.getInstance().createFileDir(it.absolutePath)
        }
    }

    fun createPhotoPath(context: Context): String {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            WKBaseApplication.getInstance().fileDir + "/video_capture"
        )
        WKFileUtils.getInstance().createFileDir(dir.absolutePath)
        return File(dir, "IMG_${System.currentTimeMillis()}.jpg").absolutePath
    }

    fun createVideoPath(context: Context): String {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            WKBaseApplication.getInstance().fileDir + "/video_capture"
        )
        WKFileUtils.getInstance().createFileDir(dir.absolutePath)
        return File(dir, "VID_${System.currentTimeMillis()}.mp4").absolutePath
    }

    fun createDownloadVideoPath(context: Context, clientMsgNo: String): String {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            WKBaseApplication.getInstance().fileDir + "/video"
        )
        WKFileUtils.getInstance().createFileDir(dir.absolutePath)
        return File(dir, "$clientMsgNo.mp4").absolutePath
    }

    fun readImageSize(path: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        return Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
    }

    fun readVideoMeta(path: String): VideoMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            VideoMeta(width, height, duration)
        } finally {
            retriever.release()
        }
    }

    data class VideoMeta(
        val width: Int,
        val height: Int,
        val durationMs: Long,
    )
}
