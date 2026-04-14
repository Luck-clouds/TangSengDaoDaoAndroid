package com.chat.video.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.chat.base.utils.WKToastUtils
import com.chat.video.R
import com.chat.video.databinding.ActivityVideoCaptureBinding
import com.chat.video.util.VideoUiUtils
import java.io.File

class VideoCaptureActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoCaptureBinding

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = FlashMode.OFF
    private var pendingVideoPath: String? = null
    private var pressStartedRecording = false
    private var ignoreNextRelease = false
    private var hasRecordingStarted = false
    private var pendingStopAfterStart = false
    private var isStopRequested = false
    private var recordingStartedAtMs = 0L
    private var currentMinRecordMs = MIN_RECORD_MS
    private var discardRecordingOnFinalize = false
    private var outputMode = OUTPUT_MODE_CHAT
    private var requestTag: String? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private val previewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                if (outputMode == OUTPUT_MODE_RESULT) {
                    setResult(RESULT_OK, result.data)
                    finish()
                } else if (
                    result.data?.getStringExtra(VideoPreviewActivity.EXTRA_RESULT_ACTION) ==
                    VideoPreviewActivity.RESULT_ACTION_SENT
                ) {
                    finish()
                }
            }
        }

    private val startRecordingRunnable = Runnable {
        startRecording()
    }
    private val hideRecordHintRunnable = Runnable {
        binding.recordHintTv.animate()
            .alpha(0f)
            .translationY(0f)
            .setDuration(220)
            .withEndAction {
                binding.recordHintTv.visibility = View.INVISIBLE
            }
            .start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        outputMode = intent.getStringExtra(EXTRA_OUTPUT_MODE) ?: OUTPUT_MODE_CHAT
        requestTag = intent.getStringExtra(EXTRA_REQUEST_TAG)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        supportActionBar?.hide()

        initListeners()
        startCamera()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(hideRecordHintRunnable)
        if (recording != null) {
            requestStopRecording()
        }
    }

    private fun initListeners() {
        binding.backIv.setOnClickListener { finish() }
        binding.switchCameraIv.setOnClickListener {
            if (recording != null || pressStartedRecording) {
                return@setOnClickListener
            }
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            if (lensFacing == CameraSelector.LENS_FACING_FRONT && flashMode == FlashMode.ON) {
                flashMode = FlashMode.OFF
            }
            startCamera()
        }
        binding.flashIv.setOnClickListener {
            flashMode = when (flashMode) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.ON -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            }
            applyFlashState()
        }
        binding.captureBtn.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    hideRecordHintImmediately()
                    pressStartedRecording = false
                    ignoreNextRelease = false
                    binding.captureBtn.removeCallbacks(startRecordingRunnable)
                    binding.captureBtn.postDelayed(
                        startRecordingRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    true
                }

                MotionEvent.ACTION_UP -> {
                    binding.captureBtn.removeCallbacks(startRecordingRunnable)
                    when {
                        ignoreNextRelease -> {
                            ignoreNextRelease = false
                        }

                        recording != null || pressStartedRecording -> requestStopRecording()
                        else -> takePhoto()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    binding.captureBtn.removeCallbacks(startRecordingRunnable)
                    if (recording != null) {
                        requestStopRecording()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(resolveImageFlashMode())
                .build()
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.FHD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
            applyFlashState()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (recording != null) return
        val outputPath = VideoUiUtils.createVideoPath(this)
        pendingVideoPath = outputPath
        hasRecordingStarted = false
        pendingStopAfterStart = false
        isStopRequested = false
        currentMinRecordMs = MIN_RECORD_MS
        discardRecordingOnFinalize = false
        recordingStartedAtMs = 0L
        val outputOptions = FileOutputOptions.Builder(File(outputPath)).build()
        val pendingRecording = videoCapture?.output
            ?.prepareRecording(this, outputOptions)
        if (pendingRecording == null) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_record_failed))
            return
        }
        var preparedRecording = pendingRecording
        if (canRecordAudio()) {
            preparedRecording = try {
                currentMinRecordMs = AUDIO_MIN_RECORD_MS
                preparedRecording.withAudioEnabled()
            } catch (_: Exception) {
                currentMinRecordMs = MIN_RECORD_MS
                preparedRecording
            }
        }
        pressStartedRecording = true
        binding.captureCenterView.setBackgroundResource(R.drawable.video_capture_center_recording_bg)
        binding.recordProgress.progress = 0
        applyFlashState()
        updateRecordingUiState(true)
        try {
            recording = preparedRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        hasRecordingStarted = true
                        recordingStartedAtMs = SystemClock.elapsedRealtime()
                        binding.recordProgress.progress = 0
                        if (pendingStopAfterStart) {
                            requestStopRecording()
                        }
                    }

                    is VideoRecordEvent.Status -> {
                        val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                        binding.recordProgress.progress = durationMs.toInt().coerceIn(0, MAX_RECORD_MS)
                        if (durationMs >= MAX_RECORD_MS) {
                            ignoreNextRelease = true
                            requestStopRecording()
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        val outputFile = pendingVideoPath
                        recording = null
                        pressStartedRecording = false
                        hasRecordingStarted = false
                        pendingStopAfterStart = false
                        isStopRequested = false
                        currentMinRecordMs = MIN_RECORD_MS
                        val shouldDiscard = discardRecordingOnFinalize
                        discardRecordingOnFinalize = false
                        recordingStartedAtMs = 0L
                        binding.captureCenterView.setBackgroundResource(R.drawable.video_capture_center_bg)
                        binding.recordProgress.progress = 0
                        applyFlashState()
                        updateRecordingUiState(false)
                        if (event.hasError() || outputFile.isNullOrEmpty()) {
                            outputFile?.let { File(it).delete() }
                            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_record_failed))
                            return@start
                        }
                        val file = File(outputFile)
                        if (shouldDiscard) {
                            file.delete()
                            showRecordHint(R.string.video_record_too_short)
                            return@start
                        }
                        if (!file.exists() || file.length() == 0L) {
                            file.delete()
                            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_record_failed))
                            return@start
                        }
                        try {
                            openPreview(VideoPreviewActivity.MODE_VIDEO, outputFile)
                        } catch (_: Exception) {
                            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_record_failed))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            recording = null
            pressStartedRecording = false
            hasRecordingStarted = false
            pendingStopAfterStart = false
            isStopRequested = false
            currentMinRecordMs = MIN_RECORD_MS
            discardRecordingOnFinalize = false
            recordingStartedAtMs = 0L
            binding.captureCenterView.setBackgroundResource(R.drawable.video_capture_center_bg)
            binding.recordProgress.progress = 0
            applyFlashState()
            updateRecordingUiState(false)
            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_record_failed))
        }
    }

    private fun requestStopRecording() {
        val activeRecording = recording ?: return
        if (isStopRequested) return
        if (!hasRecordingStarted) {
            pendingStopAfterStart = true
            return
        }
        val recordDuration = SystemClock.elapsedRealtime() - recordingStartedAtMs
        if (recordDuration in 1 until currentMinRecordMs) {
            discardRecordingOnFinalize = true
        }
        isStopRequested = true
        try {
            activeRecording.stop()
        } catch (_: Exception) {
            recording = null
            pressStartedRecording = false
            hasRecordingStarted = false
            pendingStopAfterStart = false
            isStopRequested = false
            currentMinRecordMs = MIN_RECORD_MS
            discardRecordingOnFinalize = false
            recordingStartedAtMs = 0L
            binding.captureCenterView.setBackgroundResource(R.drawable.video_capture_center_bg)
            binding.recordProgress.progress = 0
            applyFlashState()
            updateRecordingUiState(false)
            WKToastUtils.getInstance().showToastNormal(getString(R.string.video_record_failed))
        }
    }

    private fun takePhoto() {
        hideRecordHintImmediately()
        val outputPath = VideoUiUtils.createPhotoPath(this)
        val imageOutput = ImageCapture.OutputFileOptions.Builder(File(outputPath)).build()
        imageCapture?.takePicture(
            imageOutput,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (!File(outputPath).exists()) {
                        WKToastUtils.getInstance().showToastNormal(getString(R.string.video_capture_failed))
                        return
                    }
                    openPreview(VideoPreviewActivity.MODE_PHOTO, outputPath)
                }

                override fun onError(exception: ImageCaptureException) {
                    WKToastUtils.getInstance().showToastNormal(getString(R.string.video_capture_failed))
                }
            }
        )
    }

    private fun openPreview(mode: String, path: String) {
        hideRecordHintImmediately()
        val intent = Intent(this, VideoPreviewActivity::class.java)
        intent.putExtra(VideoPreviewActivity.EXTRA_MODE, mode)
        intent.putExtra(VideoPreviewActivity.EXTRA_PATH, path)
        intent.putExtra(VideoPreviewActivity.EXTRA_OUTPUT_MODE, outputMode)
        intent.putExtra(VideoPreviewActivity.EXTRA_REQUEST_TAG, requestTag)
        previewLauncher.launch(intent)
    }

    private fun applyFlashState() {
        binding.flashIv.setImageResource(flashMode.iconRes)
        val hasFlash = lensFacing == CameraSelector.LENS_FACING_BACK && (camera?.cameraInfo?.hasFlashUnit() == true)
        binding.flashIv.alpha = if (hasFlash) 1f else 0.45f
        imageCapture?.flashMode = resolveImageFlashMode()
        if (hasFlash) {
            camera?.cameraControl?.enableTorch(flashMode == FlashMode.ON)
        } else {
            camera?.cameraControl?.enableTorch(false)
        }
    }

    private fun updateRecordingUiState(isRecording: Boolean) {
        binding.switchCameraIv.isEnabled = !isRecording
        binding.switchCameraIv.alpha = if (isRecording) 0.45f else 1f
    }

    private fun resolveImageFlashMode(): Int {
        return when (flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    private fun canRecordAudio(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showRecordHint(textResId: Int) {
        uiHandler.removeCallbacks(hideRecordHintRunnable)
        binding.recordHintTv.text = getString(textResId)
        binding.recordHintTv.visibility = View.VISIBLE
        binding.recordHintTv.alpha = 0f
        binding.recordHintTv.translationY = binding.tipsTv.height.coerceAtLeast(12).toFloat() / 2f
        binding.recordHintTv.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180)
            .start()
        uiHandler.postDelayed(hideRecordHintRunnable, 1600)
    }

    private fun hideRecordHintImmediately() {
        uiHandler.removeCallbacks(hideRecordHintRunnable)
        binding.recordHintTv.animate().cancel()
        binding.recordHintTv.alpha = 0f
        binding.recordHintTv.translationY = 0f
        binding.recordHintTv.visibility = View.INVISIBLE
    }

    private enum class FlashMode(val iconRes: Int) {
        OFF(R.drawable.video_flash_off),
        ON(R.drawable.video_flash_on),
        AUTO(R.drawable.video_flash_auto),
    }

    companion object {
        const val EXTRA_OUTPUT_MODE = "extra_output_mode"
        const val EXTRA_REQUEST_TAG = "extra_request_tag"
        const val OUTPUT_MODE_CHAT = "chat"
        const val OUTPUT_MODE_RESULT = "result"
        private const val MAX_RECORD_MS = 15_000
        private const val MIN_RECORD_MS = 1_000L
        private const val AUDIO_MIN_RECORD_MS = 1_500L

        fun createIntent(
            context: Context,
            outputMode: String = OUTPUT_MODE_CHAT,
            requestTag: String? = null
        ): Intent {
            return Intent(context, VideoCaptureActivity::class.java).apply {
                putExtra(EXTRA_OUTPUT_MODE, outputMode)
                putExtra(EXTRA_REQUEST_TAG, requestTag)
            }
        }
    }
}
