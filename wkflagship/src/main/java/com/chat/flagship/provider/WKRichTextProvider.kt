package com.chat.flagship.provider

/**
 * 富文本图文气泡
 * Created by Luckclouds and chatGPT.
 */

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.emoji2.widget.EmojiTextView
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.msg.ChatContentSpanType
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.Theme
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.base.views.BubbleLayout
import com.chat.flagship.R
import com.chat.flagship.msgmodel.WKRichTextContent
import com.chat.uikit.user.UserDetailActivity
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType
import java.io.File
import kotlin.math.max

class WKRichTextProvider : WKChatBaseProvider() {
    override val itemViewType: Int
        get() = WKContentType.richText

    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_rich_text, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val content = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKRichTextContent ?: return
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val richContainer = parentView.findViewById<LinearLayout>(R.id.richContentLayout)
        val nameTv = parentView.findViewById<TextView>(R.id.receivedRichNameTv)
        val bubbleCard = parentView.findViewById<LinearLayout>(R.id.richBubbleCard)
        val textColor: Int

        if (from == WKChatIteMsgFromType.SEND) {
            contentLayout.gravity = Gravity.END
            nameTv.visibility = View.GONE
            bubbleCard.setBackgroundResource(com.chat.uikit.R.drawable.send_chat_text_bg)
            textColor = ContextCompat.getColor(context, com.chat.uikit.R.color.colorDark)
        } else {
            contentLayout.gravity = Gravity.START
            bubbleCard.setBackgroundResource(com.chat.uikit.R.drawable.received_chat_text_bg)
            textColor = ContextCompat.getColor(context, com.chat.uikit.R.color.receive_text_color)
            setFromName(uiChatMsgItemEntity, from, nameTv)
        }

        richContainer.removeAllViews()
        bindRichNodes(content, richContainer, textColor, uiChatMsgItemEntity)
        if (richContainer.childCount == 0) {
            val emptyTv = createTextView(textColor)
            emptyTv.text = content.displayContent
            richContainer.addView(emptyTv)
        }
    }

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val richTextLayout = parentView.findViewById<View>(R.id.richTextLayout)
        val msgTimeView = parentView.findViewById<View>(R.id.msgTimeView)
        if (richTextLayout != null && msgTimeView != null) {
            richTextLayout.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)
            val bubbleLayout = parentView.findViewById<BubbleLayout>(R.id.contentTvLayout)
            val bgType = getMsgBgType(
                uiChatMsgItemEntity.previousMsg,
                uiChatMsgItemEntity.wkMsg,
                uiChatMsgItemEntity.nextMsg
            )
            bubbleLayout.setAll(bgType, from, WKContentType.richText)
            if (richTextLayout.layoutParams.width < msgTimeView.layoutParams.width) {
                richTextLayout.layoutParams.width = msgTimeView.layoutParams.width
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
        addLongClick(parentView.findViewById(R.id.richBubbleCard), uiChatMsgItemEntity)
    }

    private fun bindRichNodes(
        content: WKRichTextContent,
        parent: LinearLayout,
        textColor: Int,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity
    ) {
        val nodes = content.nodes ?: return
        for (node in nodes) {
            if (node == null) {
                continue
            }
            if (node.kind == WKRichTextContent.NODE_KIND_TEXT) {
                if (!TextUtils.isEmpty(node.text)) {
                    val textView = createTextView(textColor)
                    textView.text = buildRichSpans(node.text ?: "", node.entities, uiChatMsgItemEntity)
                    parent.addView(textView)
                }
                continue
            }
            if (node.kind == WKRichTextContent.NODE_KIND_IMAGE && !TextUtils.isEmpty(node.path)) {
                val imageView = ImageView(context)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                val maxBubbleWidth = AndroidUtilities.dp(220f)
                val bubbleWidth = max(AndroidUtilities.dp(72f), maxBubbleWidth * max(25, node.widthPercent) / 100)
                val bubbleHeight = if (node.width > 0 && node.height > 0) {
                    max(AndroidUtilities.dp(54f), bubbleWidth * node.height / max(1, node.width))
                } else {
                    AndroidUtilities.dp(140f)
                }
                val layoutParams = LinearLayout.LayoutParams(bubbleWidth, bubbleHeight)
                layoutParams.bottomMargin = AndroidUtilities.dp(8f)
                imageView.layoutParams = layoutParams
                WKImageDisplayUtils.prepareImageSlot(imageView, 8f)
                val showPath = if (File(node.path).exists()) node.path else WKApiConfig.getShowUrl(node.path)
                GlideUtils.getInstance().showImg(context, showPath, bubbleWidth, bubbleHeight, imageView)
                imageView.setOnClickListener {
                    openImagePreview(imageView, showPath)
                }
                parent.addView(imageView)
            }
        }
    }

    private fun openImagePreview(imageView: ImageView, showPath: String) {
        if (TextUtils.isEmpty(showPath)) {
            return
        }
        val imgList = mutableListOf<ImageView>()
        imgList.add(imageView)
        val paths = mutableListOf<Any>()
        paths.add(showPath)
        WKDialogUtils.getInstance().showImagePopup(
            context,
            paths,
            imgList,
            imageView,
            0,
            null,
            null,
            null
        )
    }

    private fun createTextView(textColor: Int): EmojiTextView {
        val textView = EmojiTextView(context)
        textView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        textView.setTextColor(textColor)
        textView.textSize = 16f
        textView.setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1f)
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
        return textView
    }

    private fun buildRichSpans(
        text: String,
        entities: List<com.xinbida.wukongim.msgmodel.WKMsgEntity>?,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        entities ?: return builder
        for (entity in entities) {
            if (entity == null) {
                continue
            }
            val start = entity.offset
            val end = entity.offset + entity.length
            if (start < 0 || end > builder.length || end <= start) {
                continue
            }
            when (entity.type) {
                ChatContentSpanType.richBold -> builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                ChatContentSpanType.richItalic -> builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                ChatContentSpanType.richUnderline -> builder.setSpan(
                    UnderlineSpan(),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                ChatContentSpanType.richStrike -> builder.setSpan(
                    StrikethroughSpan(),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                ChatContentSpanType.richColor -> {
                    if (!TextUtils.isEmpty(entity.value)) {
                        try {
                            builder.setSpan(
                                ForegroundColorSpan(Color.parseColor(entity.value)),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

                ChatContentSpanType.richSize -> {
                    if (!TextUtils.isEmpty(entity.value)) {
                        try {
                            builder.setSpan(
                                AbsoluteSizeSpan(entity.value.toInt(), true),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

                ChatContentSpanType.mention -> applyMentionSpan(builder, entity, uiChatMsgItemEntity)
            }
        }
        return builder
    }

    private fun applyMentionSpan(
        builder: SpannableStringBuilder,
        entity: com.xinbida.wukongim.msgmodel.WKMsgEntity,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity
    ) {
        val start = entity.offset
        val end = entity.offset + entity.length
        if (start < 0 || end > builder.length || end <= start) {
            return
        }
        val uid = entity.value ?: return
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (uid == "-1") {
            builder.setSpan(
                ForegroundColorSpan(Theme.colorAccount),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return
        }
        val groupId = if (uiChatMsgItemEntity.wkMsg.channelType == WKChannelType.GROUP) {
            uiChatMsgItemEntity.wkMsg.channelID
        } else ""
        val clickableContent = if (groupId.isEmpty()) uid else "$uid|$groupId"
        val clickableSpan = NormalClickableSpan(
            false,
            Theme.colorAccount,
            NormalClickableContent(NormalClickableContent.NormalClickableTypes.Remind, clickableContent),
            object : NormalClickableSpan.IClick {
                override fun onClick(view: View) {
                    val intent = Intent(context, UserDetailActivity::class.java)
                    intent.putExtra("uid", uid)
                    if (groupId.isNotEmpty()) {
                        intent.putExtra("groupID", groupId)
                    }
                    context.startActivity(intent)
                }
            }
        )
        builder.setSpan(
            clickableSpan,
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}
