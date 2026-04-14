package com.chat.video.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.chat.video.capture.VideoCaptureActivity

data class VideoCaptureRequest(
    val requestTag: String? = null
)

class VideoCaptureContract : ActivityResultContract<VideoCaptureRequest?, VideoCaptureResult?>() {
    override fun createIntent(context: Context, input: VideoCaptureRequest?): Intent {
        return VideoCaptureActivity.createIntent(
            context = context,
            outputMode = VideoCaptureActivity.OUTPUT_MODE_RESULT,
            requestTag = input?.requestTag
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): VideoCaptureResult? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }
        return VideoCaptureResult.fromIntent(intent)
    }
}
