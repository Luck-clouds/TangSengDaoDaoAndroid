package com.chat.video.session

/**
 * 小视频发送会话上下文
 * Created by Luckclouds.
 */

import androidx.appcompat.app.AppCompatActivity
import com.chat.base.msg.IConversationContext
import com.chat.base.utils.WKToastUtils
import com.chat.video.R
import com.chat.video.capture.VideoCaptureActivity
import com.xinbida.wukongim.msgmodel.WKImageContent
import com.xinbida.wukongim.msgmodel.WKVideoContent
import java.lang.ref.WeakReference

object VideoSendSession {
    // 仅聊天模式使用，保存当前会话上下文，预览页确认后继续往这个会话发送消息。
    private var conversationRef: WeakReference<IConversationContext>? = null

    fun openCapture(conversationContext: IConversationContext) {
        conversationRef = WeakReference(conversationContext)
        val activity = conversationContext.chatActivity
        activity.startActivity(VideoCaptureActivity.createIntent(activity, VideoCaptureActivity.OUTPUT_MODE_CHAT))
    }

    fun sendImage(activity: AppCompatActivity, imagePath: String): Boolean {
        val conversationContext = conversationRef?.get()
        if (conversationContext == null) {
            WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.video_preview_missing))
            return false
        }
        conversationContext.sendMessage(WKImageContent(imagePath))
        return true
    }

    fun sendVideo(activity: AppCompatActivity, videoContent: WKVideoContent): Boolean {
        val conversationContext = conversationRef?.get()
        if (conversationContext == null) {
            WKToastUtils.getInstance().showToastNormal(activity.getString(R.string.video_preview_missing))
            return false
        }
        conversationContext.sendMessage(videoContent)
        return true
    }
}
