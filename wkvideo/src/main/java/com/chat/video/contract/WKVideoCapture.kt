package com.chat.video.contract

// 给非聊天模块使用的公开门面，避免外部直接依赖拍摄页 Activity 细节。
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
