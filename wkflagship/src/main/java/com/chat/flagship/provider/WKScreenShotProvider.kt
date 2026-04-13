package com.chat.flagship.provider

/**
 * 截屏通知系统提示气泡
 * Created by Luckclouds and chatGPT.
 */

import android.text.SpannableString
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKConfig
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.components.SystemMsgBackgroundColorSpan
import com.chat.base.utils.AndroidUtilities
import com.chat.flagship.R
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKMsg

class WKScreenShotProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return null
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
    }

    override val layoutId: Int
        get() = com.chat.base.R.layout.chat_system_layout

    override val itemViewType: Int
        get() = WKContentType.screenshot

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        helper.getView<View>(com.chat.base.R.id.systemRootView).setOnClickListener {
            val chatAdapter = getAdapter() as ChatAdapter
            chatAdapter.conversationContext.hideSoftKeyboard()
        }
        // 截屏消息沿用系统提示样式，只替换展示文案。
        val content = showScreenshotMsg(item.wkMsg)
        val textView = helper.getView<TextView>(com.chat.base.R.id.contentTv)
        textView.setShadowLayer(AndroidUtilities.dp(5f).toFloat(), 0f, 0f, 0)
        val str = SpannableString(content)
        str.setSpan(
            SystemMsgBackgroundColorSpan(
                ContextCompat.getColor(
                    context,
                    com.chat.base.R.color.colorSystemBg
                ), AndroidUtilities.dp(5f), AndroidUtilities.dp((2 * 5).toFloat())
            ), 0, content.length, 0
        )
        textView.text = str
    }

    companion object {
        fun showScreenshotMsg(msg: WKMsg?): String {
            if (msg == null) return ""
            if (msg.fromUID == WKConfig.getInstance().uid) {
                return WKBaseApplication.getInstance().application.getString(R.string.flagship_screenshot_notice_self)
            }
            // 优先使用备注名，其次昵称，保持和撤回提示一类系统消息的命名习惯一致。
            var showName = ""
            val from = msg.from
            if (from != null) {
                showName = from.channelRemark
                if (TextUtils.isEmpty(showName)) {
                    showName = from.channelName
                }
            }
            if (TextUtils.isEmpty(showName)) {
                val member = msg.memberOfFrom
                if (member != null) {
                    showName = if (TextUtils.isEmpty(member.memberRemark)) member.memberName else member.memberRemark
                }
            }
            if (TextUtils.isEmpty(showName) && !TextUtils.isEmpty(msg.fromUID)) {
                val channel = WKIM.getInstance().channelManager.getChannel(msg.fromUID, WKChannelType.PERSONAL)
                if (channel != null) {
                    showName = if (TextUtils.isEmpty(channel.channelRemark)) channel.channelName else channel.channelRemark
                }
            }
            if (TextUtils.isEmpty(showName)) {
                showName = WKBaseApplication.getInstance().application.getString(R.string.flagship_screenshot_notice_other_fallback)
            }
            return WKBaseApplication.getInstance().application.getString(
                R.string.flagship_screenshot_notice_other,
                showName
            )
        }
    }
}
