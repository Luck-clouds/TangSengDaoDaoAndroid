package com.chat.video.capture

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.WKMediaFileUtils
import com.chat.base.utils.WKToastUtils
import com.chat.video.R
import com.chat.video.contract.VideoCaptureResult
import com.chat.video.databinding.ActivityVideoPreviewBinding
import com.chat.video.session.VideoSendSession
import com.chat.video.util.VideoUiUtils
import com.xinbida.wukongim.msgmodel.WKVideoContent
import java.io.File

class VideoPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPreviewBinding

    private var mode: String = MODE_PHOTO
    private var path: String = ""
    private var outputMode: String = VideoCaptureActivity.OUTPUT_MODE_CHAT
    private var requestTag: String? = null
    private var isSending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        supportActionBar?.hide()

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PHOTO
        path = intent.getStringExtra(EXTRA_PATH) ?: ""
        outputMode = intent.getStringExtra(EXTRA_OUTPUT_MODE) ?: VideoCaptureActivity.OUTPUT_MODE_CHAT
        requestTag = intent.getStringExtra(EXTRA_REQUEST_TAG)
        if (!File(path).exists()) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_preview_missing))
            finish()
            return
        }
        initView()
        initListener()
    }

    override fun onPause() {
        super.onPause()
        if (mode == MODE_VIDEO && binding.videoView.isPlaying) {
            binding.videoView.pause()
            updateVideoToggle(false)
        }
    }

    override fun onDestroy() {
        binding.videoView.stopPlayback()
        super.onDestroy()
    }

    private fun initView() {
        if (mode == MODE_VIDEO) {
            binding.photoIv.visibility = View.GONE
            binding.videoView.visibility = View.VISIBLE
            binding.videoToggleIv.visibility = View.VISIBLE
            binding.videoView.setVideoURI(Uri.fromFile(File(path)))
            binding.videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true
                // 预览页里的视频按全屏铺满显示，只作用在这里，不影响聊天正式播放器。
                updateVideoLayout(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                binding.videoView.start()
                updateVideoToggle(true)
            }
        } else {
            binding.photoIv.visibility = View.VISIBLE
            binding.videoView.visibility = View.GONE
            binding.videoToggleIv.visibility = View.GONE
            GlideUtils.getInstance().showImg(this, path, binding.photoIv)
        }
    }

    private fun initListener() {
        binding.backIv.setOnClickListener { finish() }
        binding.retryLayout.setOnClickListener {
            if (outputMode == VideoCaptureActivity.OUTPUT_MODE_RESULT) {
                // 外部调用方需要知道用户选择了重拍，便于保留自己的页面状态。
                setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_RESULT_ACTION, RESULT_ACTION_RETAKE))
            }
            finish()
        }
        binding.sendLayout.setOnClickListener {
            if (!isSending) {
                sendCurrent()
            }
        }
        binding.videoToggleIv.setOnClickListener {
            if (mode == MODE_VIDEO) {
                toggleVideo()
            }
        }
        binding.videoView.setOnClickListener {
            if (mode == MODE_VIDEO) {
                toggleVideo()
            }
        }
    }

    private fun toggleVideo() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            updateVideoToggle(false)
        } else {
            binding.videoView.start()
            updateVideoToggle(true)
        }
    }

    private fun updateVideoToggle(isPlaying: Boolean) {
        binding.videoToggleIv.setImageResource(
            if (isPlaying) R.drawable.video_pause_overlay else R.drawable.video_play_overlay
        )
    }

    private fun updateVideoLayout(videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) {
            return
        }
        binding.root.post {
            val containerWidth = binding.root.width
            val containerHeight = binding.root.height
            if (containerWidth <= 0 || containerHeight <= 0) {
                return@post
            }
            val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
            val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val layoutParams = binding.videoView.layoutParams as FrameLayout.LayoutParams
            // 通过裁切而不是留黑边的方式铺满预览页。
            if (videoRatio > containerRatio) {
                layoutParams.height = containerHeight
                layoutParams.width = (containerHeight * videoRatio).toInt()
            } else {
                layoutParams.width = containerWidth
                layoutParams.height = (containerWidth / videoRatio).toInt()
            }
            layoutParams.gravity = android.view.Gravity.CENTER
            binding.videoView.layoutParams = layoutParams
        }
    }

    private fun sendCurrent() {
        isSending = true
        if (mode == MODE_VIDEO) {
            sendVideo()
        } else {
            sendPhoto()
        }
    }

    private fun sendPhoto() {
        if (outputMode == VideoCaptureActivity.OUTPUT_MODE_RESULT) {
            finishWithCaptureResult(buildPhotoResult(path))
            return
        }
        val sourcePath = path
        GlideUtils.getInstance().compressImg(this, sourcePath) { files ->
            val targetPath = if (files.isNullOrEmpty()) sourcePath else files[0].absolutePath
            if (VideoSendSession.sendImage(this, targetPath)) {
                finishAfterChatSent()
            } else {
                isSending = false
            }
        }
    }

    private fun sendVideo() {
        val file = File(path)
        if (!file.exists()) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_preview_missing))
            isSending = false
            return
        }
        val meta = VideoUiUtils.readVideoMeta(path)
        if (outputMode == VideoCaptureActivity.OUTPUT_MODE_RESULT) {
            // 外部调用方直接拿到本地文件路径和媒体元数据，后续由调用方自己处理上传或编辑。
            finishWithCaptureResult(
                VideoCaptureResult(
                    mode = VideoCaptureResult.MODE_VIDEO,
                    path = path,
                    coverPath = WKMediaFileUtils.getInstance().getVideoCover(path),
                    width = meta.width,
                    height = meta.height,
                    durationMs = meta.durationMs,
                    size = file.length(),
                    requestTag = requestTag
                )
            )
            return
        }
        // 聊天模式继续复用现有 WKVideoContent 发送链路。
        val videoContent = WKVideoContent().apply {
            localPath = path
            coverLocalPath = WKMediaFileUtils.getInstance().getVideoCover(path)
            width = meta.width
            height = meta.height
            second = meta.durationMs / 1000
            size = file.length()
        }
        if (VideoSendSession.sendVideo(this, videoContent)) {
            finishAfterChatSent()
        } else {
            isSending = false
        }
    }

    private fun buildPhotoResult(targetPath: String): VideoCaptureResult {
        val file = File(targetPath)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(targetPath, options)
        return VideoCaptureResult(
            mode = VideoCaptureResult.MODE_PHOTO,
            path = targetPath,
            coverPath = null,
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
            durationMs = 0L,
            size = file.length(),
            requestTag = requestTag
        )
    }

    private fun finishWithCaptureResult(result: VideoCaptureResult) {
        isSending = false
        setResult(RESULT_OK, result.toIntent())
        finish()
    }

    private fun finishAfterChatSent() {
        isSending = false
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_ACTION, RESULT_ACTION_SENT))
        finish()
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_OUTPUT_MODE = "extra_output_mode"
        const val EXTRA_REQUEST_TAG = "extra_request_tag"
        // 预览页只用结果动作告诉拍摄页或外部调用方发生了什么，不直接暴露内部页面状态。
        const val EXTRA_RESULT_ACTION = "extra_result_action"
        const val RESULT_ACTION_RETAKE = "retake"
        const val RESULT_ACTION_SENT = "sent"
        const val MODE_PHOTO = "photo"
        const val MODE_VIDEO = "video"
    }
}
