package com.chat.uikit.chat.provider

import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.R
import com.chat.base.act.WKWebViewActivity
import com.chat.base.msg.ChatAdapter
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.ui.components.SystemMsgBackgroundColorSpan
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKToastUtils
import com.chat.uikit.group.service.GroupModel

class ApproveGroupMemberProvider : WKChatBaseProvider() {
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

    override val itemViewType: Int
        get() = WKContentType.approveGroupMember

    override val layoutId: Int
        get() = R.layout.chat_system_layout

    override fun convert(helper: BaseViewHolder, item: WKUIChatMsgItemEntity) {
        super.convert(helper, item)
        helper.getView<View>(R.id.systemRootView).setOnClickListener {
            val chatAdapter = getAdapter() as ChatAdapter
            chatAdapter.conversationContext.hideSoftKeyboard()
        }

        val reviewText = context.getString(com.chat.uikit.R.string.group_invite_go_review)
        val content = getShowContent(item.wkMsg.content).orEmpty()
        val showReview = !TextUtils.isEmpty(resolveInviteNo(item))
        val fullText = if (showReview) content + reviewText else content

        val textView = helper.getView<TextView>(R.id.contentTv)
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = android.graphics.Color.TRANSPARENT
        textView.setShadowLayer(AndroidUtilities.dp(5f).toFloat(), 0f, 0f, 0)

        val str = SpannableString(fullText)
        str.setSpan(
            SystemMsgBackgroundColorSpan(
                ContextCompat.getColor(context, R.color.colorSystemBg),
                AndroidUtilities.dp(5f),
                AndroidUtilities.dp((2 * 5).toFloat())
            ),
            0,
            fullText.length,
            0
        )
        if (showReview) {
            val start = content.length
            str.setSpan(
                NormalClickableSpan(
                    false,
                    Theme.colorAccount,
                    NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, reviewText),
                    object : NormalClickableSpan.IClick {
                        override fun onClick(view: View) {
                            openApprovePage(item)
                        }
                    }),
                start,
                fullText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textView.text = str
    }

    private fun openApprovePage(item: WKUIChatMsgItemEntity) {
        val inviteNo = resolveInviteNo(item)
        if (TextUtils.isEmpty(inviteNo)) {
            WKToastUtils.getInstance().showToast(context.getString(com.chat.uikit.R.string.group_invite_missing_review_info))
            return
        }
        GroupModel.getInstance().getH5ConfirmUrl(item.wkMsg.channelID, inviteNo) { code, msg, url ->
            if (code == HttpResponseCode.success.toInt() && !TextUtils.isEmpty(url)) {
                val activity = (getAdapter() as ChatAdapter).conversationContext.chatActivity
                val intent = Intent(activity, WKWebViewActivity::class.java)
                intent.putExtra("url", url)
                activity.startActivity(intent)
            } else {
                WKToastUtils.getInstance().showToast(
                    if (!TextUtils.isEmpty(msg)) msg else context.getString(com.chat.uikit.R.string.group_invite_open_review_failed)
                )
            }
        }
    }

    private fun resolveInviteNo(item: WKUIChatMsgItemEntity): String {
        val fromContent = GroupModel.getInstance().getInviteNoFromMessageContent(item.wkMsg.content)
        if (!TextUtils.isEmpty(fromContent)) {
            return fromContent
        }
        return ""
    }
}
