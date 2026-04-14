package com.chat.video.contract

import android.content.Intent

data class VideoCaptureResult(
    val mode: String,
    val path: String,
    val coverPath: String?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val size: Long,
    val requestTag: String?
) {
    fun toIntent(): Intent {
        return Intent().apply {
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_PATH, path)
            putExtra(EXTRA_COVER_PATH, coverPath)
            putExtra(EXTRA_WIDTH, width)
            putExtra(EXTRA_HEIGHT, height)
            putExtra(EXTRA_DURATION_MS, durationMs)
            putExtra(EXTRA_SIZE, size)
            putExtra(EXTRA_REQUEST_TAG, requestTag)
        }
    }

    companion object {
        const val MODE_PHOTO = "photo"
        const val MODE_VIDEO = "video"
        const val EXTRA_MODE = "video_capture_result_mode"
        const val EXTRA_PATH = "video_capture_result_path"
        const val EXTRA_COVER_PATH = "video_capture_result_cover_path"
        const val EXTRA_WIDTH = "video_capture_result_width"
        const val EXTRA_HEIGHT = "video_capture_result_height"
        const val EXTRA_DURATION_MS = "video_capture_result_duration_ms"
        const val EXTRA_SIZE = "video_capture_result_size"
        const val EXTRA_REQUEST_TAG = "video_capture_result_request_tag"

        fun fromIntent(intent: Intent?): VideoCaptureResult? {
            if (intent == null) {
                return null
            }
            val mode = intent.getStringExtra(EXTRA_MODE) ?: return null
            val path = intent.getStringExtra(EXTRA_PATH) ?: return null
            return VideoCaptureResult(
                mode = mode,
                path = path,
                coverPath = intent.getStringExtra(EXTRA_COVER_PATH),
                width = intent.getIntExtra(EXTRA_WIDTH, 0),
                height = intent.getIntExtra(EXTRA_HEIGHT, 0),
                durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L),
                size = intent.getLongExtra(EXTRA_SIZE, 0L),
                requestTag = intent.getStringExtra(EXTRA_REQUEST_TAG)
            )
        }
    }
}
