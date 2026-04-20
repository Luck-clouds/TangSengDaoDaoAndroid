package com.chat.sticker.ui.provider

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKMsgBgType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.ui.Theme
import com.chat.base.ui.components.FilterImageView
import com.chat.base.ui.components.SecretDeleteTimer
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.ImageUtils
import com.chat.base.utils.LayoutHelper
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.base.utils.WKTimeUtils
import com.chat.base.views.CircularProgressView
import com.chat.base.views.blurview.ShapeBlurView
import com.chat.sticker.R
import com.chat.sticker.utils.StickerTrace
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.message.type.WKMsgContentType
import com.chat.base.msg.model.WKGifContent
import java.io.File

/**
 * 贴纸消息 provider
 * Created by Luckclouds .
 */
class WKStickerProvider(
    private val contentType: Int = WKMsgContentType.WK_GIF
) : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(com.chat.uikit.R.layout.chat_item_img, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(com.chat.uikit.R.id.contentLayout)
        val gifContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as? WKGifContent
        if (gifContent == null) {
            StickerTrace.e("STICKER_TRACE_PROVIDER_BIND abort reason=content_not_gif actual=${uiChatMsgItemEntity.wkMsg.baseContentMsgModel?.javaClass?.name}")
            return
        }
        val imageView = parentView.findViewById<FilterImageView>(com.chat.uikit.R.id.imageView)
        val blurView = parentView.findViewById<ShapeBlurView>(com.chat.uikit.R.id.blurView)
        val progressTv = parentView.findViewById<TextView>(com.chat.uikit.R.id.progressTv)
        val progressView = parentView.findViewById<CircularProgressView>(com.chat.uikit.R.id.progressView)
        val imageLayout = parentView.findViewById<View>(com.chat.uikit.R.id.imageLayout)
        val otherLayout = parentView.findViewById<FrameLayout>(com.chat.uikit.R.id.otherLayout)
        val deleteTimer = SecretDeleteTimer(context)

        progressTv.visibility = View.GONE
        progressView.visibility = View.GONE
        progressView.setProgColor(Theme.colorAccount)
        otherLayout.removeAllViews()
        otherLayout.addView(deleteTimer, LayoutHelper.createFrame(35, 35, Gravity.CENTER))
        contentLayout.gravity = if (from == WKChatIteMsgFromType.RECEIVED) Gravity.START else Gravity.END

        val layoutParams = imageView.layoutParams as FrameLayout.LayoutParams
        val blurViewLayoutParams = blurView.layoutParams as FrameLayout.LayoutParams
        val layoutParams1 = imageLayout.layoutParams as LinearLayout.LayoutParams
        val ints = ImageUtils.getInstance().getImageWidthAndHeightToTalk(gifContent.width, gifContent.height)
        val width = if (ints[0] > 0) ints[0] else AndroidUtilities.dp(120f)
        val height = if (ints[1] > 0) ints[1] else AndroidUtilities.dp(120f)
        StickerTrace.d(
            "STICKER_TRACE_PROVIDER_BIND msgId=${uiChatMsgItemEntity.wkMsg.messageID} clientMsgNo=${uiChatMsgItemEntity.wkMsg.clientMsgNO} from=$from type=${uiChatMsgItemEntity.wkMsg.type} showSize=${width}x${height} ${StickerTrace.gifSummary(gifContent)}"
        )

        layoutParams.width = width
        layoutParams.height = height
        blurViewLayoutParams.width = width
        blurViewLayoutParams.height = height
        layoutParams1.width = width
        layoutParams1.height = height
        imageView.layoutParams = layoutParams
        blurView.layoutParams = blurViewLayoutParams
        imageLayout.layoutParams = layoutParams1
        WKImageDisplayUtils.prepareImageSlot(imageView, 4f)

        blurView.visibility = if (uiChatMsgItemEntity.wkMsg.flame == 1) View.VISIBLE else View.GONE
        if (uiChatMsgItemEntity.wkMsg.flame == 1) {
            otherLayout.visibility = View.VISIBLE
            deleteTimer.setSize(35)
            if (uiChatMsgItemEntity.wkMsg.viewedAt > 0 && uiChatMsgItemEntity.wkMsg.flameSecond > 0) {
                deleteTimer.setDestroyTime(
                    uiChatMsgItemEntity.wkMsg.clientMsgNO,
                    uiChatMsgItemEntity.wkMsg.flameSecond,
                    uiChatMsgItemEntity.wkMsg.viewedAt,
                    false
                )
            }
        } else {
            otherLayout.visibility = View.GONE
        }

        val showUrl = getShowURL(gifContent)
        val format = resolveFormat(gifContent)
        val useGif = format.contains("gif", true) || showUrl.contains(".gif", true)
        StickerTrace.d("STICKER_TRACE_PROVIDER_LOAD url=$showUrl format=$format useGif=$useGif")
        if (useGif) {
            GlideUtils.getInstance().showGif(context, showUrl, imageView, null)
        } else {
            GlideUtils.getInstance().showImg(context, showUrl, imageView)
        }
        setCorners(from, uiChatMsgItemEntity, imageView, blurView)
        addLongClick(imageView, uiChatMsgItemEntity)
        imageView.setOnClickListener {
            if (uiChatMsgItemEntity.wkMsg.flame == 1 && uiChatMsgItemEntity.wkMsg.viewed == 0) {
                for (i in 0 until getAdapter()!!.data.size) {
                    if (getAdapter()!!.data[i].wkMsg.clientMsgNO == uiChatMsgItemEntity.wkMsg.clientMsgNO) {
                        getAdapter()!!.data[i].wkMsg.viewed = 1
                        getAdapter()!!.data[i].wkMsg.viewedAt = WKTimeUtils.getInstance().currentMills
                        getAdapter()!!.notifyItemChanged(adapterPosition)
                        WKIM.getInstance().msgManager.updateViewedAt(
                            1,
                            getAdapter()!!.data[i].wkMsg.viewedAt,
                            getAdapter()!!.data[i].wkMsg.clientMsgNO
                        )
                        break
                    }
                }
            }
        }
    }

    override val itemViewType: Int
        get() = contentType

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val imageView = parentView.findViewById<FilterImageView>(com.chat.uikit.R.id.imageView)
        addLongClick(imageView, uiChatMsgItemEntity)
    }

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val imageView = parentView.findViewById<FilterImageView>(com.chat.uikit.R.id.imageView)
        val blurView = parentView.findViewById<ShapeBlurView>(com.chat.uikit.R.id.blurView)
        setCorners(from, uiChatMsgItemEntity, imageView, blurView)
    }

    private fun getShowURL(content: WKGifContent): String {
        if (!TextUtils.isEmpty(content.localPath)) {
            val file = File(content.localPath)
            if (file.exists() && file.length() > 0L) {
                StickerTrace.d("STICKER_TRACE_PROVIDER_RESOLVE localPath=${file.absolutePath} fileSize=${file.length()}")
                return file.absolutePath
            }
        }
        if (!TextUtils.isEmpty(content.url)) {
            val showUrl = WKApiConfig.getShowUrl(content.url)
            StickerTrace.d("STICKER_TRACE_PROVIDER_RESOLVE remoteUrl=$showUrl raw=${content.url}")
            return showUrl
        }
        val placeholderUrl = resolvePlaceholder(content.placeholder)
        StickerTrace.d("STICKER_TRACE_PROVIDER_RESOLVE placeholderUrl=$placeholderUrl placeholder=${content.placeholder}")
        return placeholderUrl
    }

    private fun resolvePlaceholder(placeholder: String?): String {
        if (placeholder.isNullOrEmpty()) {
            return ""
        }
        val trimmed = placeholder.trim()
        if (trimmed.startsWith("<svg")) {
            val match = Regex("""href="([^"]+)"""").find(trimmed)
            val raw = match?.groupValues?.getOrNull(1).orEmpty()
            return if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("data:")) {
                raw
            } else {
                WKApiConfig.getShowUrl(raw)
            }
        }
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:")) {
            trimmed
        } else {
            WKApiConfig.getShowUrl(trimmed)
        }
    }

    private fun resolveFormat(content: WKGifContent): String {
        if (!content.format.isNullOrEmpty()) {
            return content.format.lowercase()
        }
        val cleanUrl = content.url.orEmpty().substringBefore('?')
        return cleanUrl.substringAfterLast('.', "").lowercase()
    }

    private fun setCorners(
        from: WKChatIteMsgFromType,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        imageView: FilterImageView,
        blurView: ShapeBlurView
    ) {
        imageView.strokeWidth = 0f
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        when (bgType) {
            WKMsgBgType.center -> {
                if (from == WKChatIteMsgFromType.SEND) {
                    imageView.setCorners(10, 5, 10, 5)
                    blurView.setCornerRadius(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat())
                } else {
                    imageView.setCorners(5, 10, 5, 10)
                    blurView.setCornerRadius(AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat())
                }
            }
            WKMsgBgType.top -> {
                if (from == WKChatIteMsgFromType.SEND) {
                    imageView.setCorners(10, 10, 10, 5)
                    blurView.setCornerRadius(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat())
                } else {
                    imageView.setCorners(10, 10, 5, 10)
                    blurView.setCornerRadius(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat())
                }
            }
            WKMsgBgType.bottom -> {
                if (from == WKChatIteMsgFromType.SEND) {
                    imageView.setCorners(10, 5, 10, 10)
                    blurView.setCornerRadius(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat())
                } else {
                    imageView.setCorners(5, 10, 10, 10)
                    blurView.setCornerRadius(AndroidUtilities.dp(5f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat())
                }
            }
            else -> {
                imageView.setAllCorners(10)
                blurView.setCornerRadius(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(10f).toFloat())
            }
        }
    }
}
