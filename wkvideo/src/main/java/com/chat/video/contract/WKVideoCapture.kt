package com.chat.video.contract

object WKVideoCapture {
    @JvmStatic
    fun contract(): VideoCaptureContract {
        return VideoCaptureContract()
    }

    @JvmStatic
    fun request(requestTag: String? = null): VideoCaptureRequest {
        return VideoCaptureRequest(requestTag)
    }

    @JvmStatic
    fun parseResult(data: android.content.Intent?): VideoCaptureResult? {
        return VideoCaptureResult.fromIntent(data)
    }
}
