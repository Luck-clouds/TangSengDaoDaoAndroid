package com.chat.rtc.message

import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.RTCMenu
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.views.BubbleLayout
import com.chat.rtc.R
import com.chat.rtc.RtcManager
import org.json.JSONObject
import org.telegram.ui.Components.RLottieImageView
import java.util.Locale

open class RtcMessageProvider(private val rtcType: Int = WKContentType.rtcRecord) : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_rtc_message, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val bodyLayout = parentView.findViewById<LinearLayout>(R.id.bodyLayout)
        val headerLayout = parentView.findViewById<LinearLayout>(R.id.headerLayout)
        val bubbleLayout = parentView.findViewById<View>(R.id.bubbleLayout)
        val titleTv = parentView.findViewById<TextView>(R.id.titleTv)
        val subtitleTv = parentView.findViewById<TextView>(R.id.subtitleTv)
        val durationTv = parentView.findViewById<TextView>(R.id.durationTv)
        val joinTv = parentView.findViewById<TextView>(R.id.joinTv)
        val iconIv = parentView.findViewById<ImageView>(R.id.iconIv)
        val isSend = from == WKChatIteMsgFromType.SEND
        val payload = runCatching { JSONObject(uiChatMsgItemEntity.wkMsg.content ?: "{}") }.getOrNull()
        val callType = payload?.optString("call_type").orEmpty()
        val recordType = payload?.optString("record_type").orEmpty()
        val isVideo = callType == "video"
        val isOngoingNotice = isInviteAllNotice(payload, recordType)
        val titleTextColor = when {
            isOngoingNotice || isSend -> Color.BLACK
            else -> ContextCompat.getColor(context, android.R.color.black)
        }
        val metaTextColor = when {
            isOngoingNotice || isSend -> Color.BLACK
            else -> ContextCompat.getColor(context, com.chat.base.R.color.color999)
        }

        contentLayout.gravity = if (isSend) Gravity.END else Gravity.START
        bodyLayout.gravity = if (isSend) Gravity.END else Gravity.START
        bubbleLayout.minimumWidth = if (isOngoingNotice) dp(230) else dp(132)
        headerLayout.layoutDirection = if (isOngoingNotice || !isSend) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        headerLayout.gravity = if (isOngoingNotice || !isSend) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.END or Gravity.CENTER_VERTICAL

        iconIv.setImageResource(resolveIcon(isVideo, isSend))
        iconIv.setColorFilter(Color.BLACK)
        titleTv.text = if (isOngoingNotice) {
            "\u7fa4\u901a\u8bdd\u8fdb\u884c\u4e2d"
        } else if (rtcType == WKContentType.rtcNotice && TextUtils.isEmpty(recordType)) {
            if (isVideo) "\u53d1\u8d77\u89c6\u9891\u901a\u8bdd" else "\u53d1\u8d77\u8bed\u97f3\u901a\u8bdd"
        } else {
            recordText(recordType, isVideo, isSend)
        }
        titleTv.setTextColor(titleTextColor)
        subtitleTv.setTextColor(metaTextColor)
        durationTv.setTextColor(metaTextColor)
        joinTv.setTextColor(metaTextColor)

        val duration = payload?.optLong("duration", 0L) ?: 0L
        if (isOngoingNotice) {
            subtitleTv.text = if (isVideo) {
                "\u9080\u8bf7\u7fa4\u6210\u5458\u52a0\u5165\u89c6\u9891\u901a\u8bdd"
            } else {
                "\u9080\u8bf7\u7fa4\u6210\u5458\u52a0\u5165\u8bed\u97f3\u901a\u8bdd"
            }
            subtitleTv.visibility = View.VISIBLE
            durationTv.text = ""
            durationTv.visibility = View.GONE
            joinTv.text = "\u52a0\u5165"
            joinTv.visibility = View.VISIBLE
        } else {
            subtitleTv.text = ""
            subtitleTv.visibility = View.GONE
            durationTv.text = if (duration > 0) formatDuration(duration) else ""
            durationTv.visibility = if (duration > 0) View.VISIBLE else View.GONE
            joinTv.text = ""
            joinTv.visibility = View.GONE
        }

        resetCellBackground(parentView, uiChatMsgItemEntity, from)
    }

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        parentView.findViewById<BubbleLayout>(R.id.bubbleLayout)
            .apply {
                if (from == WKChatIteMsgFromType.SEND) {
                    setAll(
                        bgType,
                        from,
                        R.color.wkrtc_chat_send_bg_normal,
                        R.color.wkrtc_chat_send_bg_select
                    )
                    setBubbleBorderColor(Color.TRANSPARENT)
                    setShadowColor(Color.TRANSPARENT)
                    setShadowX(0)
                    setShadowY(0)
                } else {
                    setAll(bgType, from, uiChatMsgItemEntity.wkMsg.type)
                }
            }
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val bubbleLayout = parentView.findViewById<View>(R.id.bubbleLayout)
        addLongClick(bubbleLayout, uiChatMsgItemEntity)
        bubbleLayout.setOnClickListener {
            if (isOngoingNotice(uiChatMsgItemEntity)) {
                RtcManager.getInstance().openOngoingGroupCallFromMessage(uiChatMsgItemEntity.wkMsg)
            } else {
                restartCall(uiChatMsgItemEntity)
            }
        }
    }

    override fun setMsgTimeAndStatus(
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        parentView: View,
        fromType: WKChatIteMsgFromType
    ) {
        super.setMsgTimeAndStatus(uiChatMsgItemEntity, parentView, fromType)
        val msgTimeTv = parentView.findViewById<TextView>(R.id.msgTimeTv)
        val editedTv = parentView.findViewById<TextView>(R.id.editedTv)
        val statusIV = parentView.findViewById<RLottieImageView>(R.id.statusIV)
        val isSend = fromType == WKChatIteMsgFromType.SEND
        val payload = runCatching { JSONObject(uiChatMsgItemEntity.wkMsg.content ?: "{}") }.getOrNull()
        val recordType = payload?.optString("record_type").orEmpty()
        val isOngoingNotice = isInviteAllNotice(payload, recordType)
        val color = when {
            isOngoingNotice || isSend -> Color.BLACK
            else -> ContextCompat.getColor(context, com.chat.base.R.color.color999)
        }
        msgTimeTv.setTextColor(color)
        editedTv.setTextColor(color)
        statusIV.visibility = if (isSend) View.VISIBLE else View.GONE
    }

    override val itemViewType: Int
        get() = rtcType

    private fun restartCall(uiChatMsgItemEntity: WKUIChatMsgItemEntity) {
        val chatAdapter = getAdapter() as? ChatAdapter ?: return
        val conversationContext = chatAdapter.conversationContext ?: return
        val payload = runCatching { JSONObject(uiChatMsgItemEntity.wkMsg.content ?: "{}") }.getOrNull()
        val callType = if (payload?.optString("call_type") == "video") 1 else 0
        EndpointManager.getInstance().invoke("restart_rtc_call_from_msg", RTCMenu(conversationContext, callType))
    }

    private fun resolveIcon(isVideo: Boolean, isSend: Boolean): Int {
        return when {
            isVideo && isSend -> R.drawable.wkrtc_ic_video_msg_send
            isVideo -> R.drawable.wkrtc_ic_video_msg_received
            isSend -> R.drawable.wkrtc_ic_phone_msg_send
            else -> R.drawable.wkrtc_ic_phone_msg_received
        }
    }

    private fun recordText(recordType: String, isVideo: Boolean, isSend: Boolean): String {
        return when (recordType) {
            "answered" -> "\u901a\u8bdd\u5df2\u7ed3\u675f"
            "missed" -> if (isSend) "\u5bf9\u65b9\u672a\u63a5\u542c" else "\u672a\u63a5\u6765\u7535"
            "rejected" -> if (isSend) "\u5bf9\u65b9\u5df2\u62d2\u7edd" else "\u5df2\u62d2\u7edd"
            "cancelled" -> "\u901a\u8bdd\u5df2\u53d6\u6d88"
            else -> if (isVideo) "\u89c6\u9891\u901a\u8bdd" else "\u8bed\u97f3\u901a\u8bdd"
        }
    }

    private fun formatDuration(second: Long): String {
        val hour = second / 3600
        val minute = second % 3600 / 60
        val rest = second % 60
        return if (hour > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, rest)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minute, rest)
        }
    }

    private fun isOngoingNotice(uiChatMsgItemEntity: WKUIChatMsgItemEntity): Boolean {
        if (rtcType != WKContentType.rtcNotice) {
            return false
        }
        val payload = runCatching { JSONObject(uiChatMsgItemEntity.wkMsg.content ?: "{}") }.getOrNull()
        return isInviteAllNotice(payload, payload?.optString("record_type").orEmpty())
    }

    private fun isInviteAllNotice(payload: JSONObject?, recordType: String): Boolean {
        return rtcType == WKContentType.rtcNotice
                && TextUtils.isEmpty(recordType)
                && payload?.optBoolean("invite_all", false) == true
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}

class RtcNoticeProvider : RtcMessageProvider(WKContentType.rtcNotice)

class RtcRecordProvider : RtcMessageProvider(WKContentType.rtcRecord)
