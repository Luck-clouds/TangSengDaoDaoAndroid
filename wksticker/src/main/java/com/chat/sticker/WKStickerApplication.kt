package com.chat.sticker

import android.app.Application
import android.content.Context
import android.content.Intent
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ChatItemPopupMenu
import com.chat.base.endpoint.entity.ChatToolBarMenu
import com.chat.base.endpoint.entity.MsgConfig
import com.chat.base.msg.IConversationContext
import com.chat.base.msg.model.WKGifContent
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKMsgItemViewManager
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKToastUtils
import com.chat.sticker.msg.WKEmojiStickerContent
import com.chat.sticker.msg.WKVectorStickerContent
import com.chat.sticker.ui.StickerMyStickersActivity
import com.chat.sticker.ui.StickerPackageDetailActivity
import com.chat.sticker.ui.StickerPanelView
import com.chat.sticker.ui.StickerStoreActivity
import com.chat.sticker.ui.provider.WKStickerProvider
import com.chat.sticker.utils.StickerTrace
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKMsg

/**
 * 表情商店模块入口
 * Created by Luckclouds .
 */
class WKStickerApplication private constructor() {
    companion object {
        fun getInstance(): WKStickerApplication = Holder.instance
    }

    private object Holder {
        val instance = WKStickerApplication()
    }

    private var application: Application? = null

    fun init(application: Application) {
        this.application = application
        StickerTrace.d("STICKER_TRACE_INIT start")
        WKIM.getInstance().getMsgManager().registerContentMsg(WKGifContent::class.java)
        WKIM.getInstance().getMsgManager().registerContentMsg(WKVectorStickerContent::class.java)
        WKIM.getInstance().getMsgManager().registerContentMsg(WKEmojiStickerContent::class.java)
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_GIF, WKStickerProvider(WKContentType.WK_GIF))
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_VECTOR_STICKER, WKStickerProvider(WKContentType.WK_VECTOR_STICKER))
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_EMOJI_STICKER, WKStickerProvider(WKContentType.WK_EMOJI_STICKER))
        registerMessageMenus()
        registerToolbar()
        EndpointManager.getInstance().setMethod("is_register_sticker") { true }
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_GIF) { MsgConfig(true) }
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_VECTOR_STICKER) { MsgConfig(true) }
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_EMOJI_STICKER) { MsgConfig(true) }
        StickerTrace.d("STICKER_TRACE_INIT done gif=${WKContentType.WK_GIF} vector=${WKContentType.WK_VECTOR_STICKER} emoji=${WKContentType.WK_EMOJI_STICKER}")
    }

    private fun registerToolbar() {
        EndpointManager.getInstance().setMethod("chat_toolbar_sticker", EndpointCategory.wkChatToolBar, 100) { obj ->
            val context = obj as? IConversationContext ?: return@setMethod null
            val panelView = StickerPanelView(context.chatActivity, context)
            ChatToolBarMenu(
                "chat_toolbar_sticker",
                com.chat.uikit.R.mipmap.icon_chat_toolbar_emoji,
                com.chat.uikit.R.mipmap.icon_chat_toolbar_emoji,
                panelView
            ) { _, _ -> }
        }
    }

    private fun registerMessageMenus() {
        EndpointManager.getInstance().setMethod("sticker_view_album", EndpointCategory.wkChatPopupItem, 82) { obj ->
            val msg = obj as? WKMsg ?: return@setMethod null
            if (!isStickerMsgType(msg.type)) return@setMethod null
            val content = msg.baseContentMsgModel as? WKGifContent ?: return@setMethod null
            if (content.category.isEmpty() || content.category == "custom" || content.category == "favorites") return@setMethod null
            ChatItemPopupMenu(
                R.mipmap.sticker_settings_icon,
                application?.getString(R.string.sticker_view_album) ?: "查看专辑",
                object : ChatItemPopupMenu.IPopupItemClick {
                    override fun onClick(mMsg: WKMsg, iConversationContext: IConversationContext) {
                        val intent = Intent(iConversationContext.chatActivity, StickerPackageDetailActivity::class.java)
                        intent.putExtra(StickerPackageDetailActivity.EXTRA_PACKAGE_ID, content.category)
                        iConversationContext.chatActivity.startActivity(intent)
                    }
                }
            )
        }
        EndpointManager.getInstance().setMethod("sticker_toggle_favorite", EndpointCategory.wkChatPopupItem, 81) { obj ->
            val msg = obj as? WKMsg ?: return@setMethod null
            if (!isStickerMsgType(msg.type)) return@setMethod null
            val content = msg.baseContentMsgModel as? WKGifContent ?: return@setMethod null
            if (content.format.isEmpty()) return@setMethod null
            val removable = content.placeholder.startsWith("favorite")
            ChatItemPopupMenu(
                if (removable) R.mipmap.sticker_remove_icon else R.mipmap.sticker_add_icon,
                application?.getString(if (removable) R.string.sticker_remove else R.string.sticker_add) ?: if (removable) "移除" else "添加",
                object : ChatItemPopupMenu.IPopupItemClick {
                    override fun onClick(mMsg: WKMsg, iConversationContext: IConversationContext) {
                        toggleFavorite(iConversationContext.chatActivity, content, removable)
                    }
                }
            )
        }
        EndpointManager.getInstance().setMethod("sticker_personal_center", EndpointCategory.personalCenter, 5) {
            com.chat.base.endpoint.entity.PersonalInfoMenu(
                R.mipmap.sticker_tab_icon,
                application?.getString(R.string.sticker_my_title) ?: "我的表情"
            ) {
                openMySticker(application)
            }
        }
        EndpointManager.getInstance().setMethod("sticker_open_store") { obj ->
            openStore(obj as? Context ?: application)
            null
        }
        EndpointManager.getInstance().setMethod("sticker_open_my") { obj ->
            openMySticker(obj as? Context ?: application)
            null
        }
    }

    private fun isStickerMsgType(type: Int): Boolean {
        return type == WKContentType.WK_GIF
                || type == WKContentType.WK_VECTOR_STICKER
                || type == WKContentType.WK_EMOJI_STICKER
    }

    private fun toggleFavorite(context: Context, content: WKGifContent, removable: Boolean) {
        val targetType = when {
            content.placeholder == "favorite_custom" || content.placeholder == "custom" -> "custom_item"
            content.placeholder == "builtin_emoji" -> "builtin_emoji"
            else -> "platform_item"
        }
        val targetId = content.format
        val emojiCode = if (targetType == "builtin_emoji") content.format else ""
        StickerTrace.d("STICKER_TRACE_FAVORITE_TOGGLE removable=$removable targetType=$targetType targetId=$targetId emojiCode=$emojiCode ${StickerTrace.gifSummary(content)}")
        if (targetType != "builtin_emoji" && targetId.isEmpty()) {
            StickerTrace.e("STICKER_TRACE_FAVORITE_TOGGLE abort reason=empty_target_id")
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.sticker_request_failed))
            return
        }
        val callback: (Int, String) -> Unit = { code, msg ->
            if (code == HttpResponseCode.success.toInt()) {
                StickerTrace.d("STICKER_TRACE_FAVORITE_TOGGLE success removable=$removable")
                WKToastUtils.getInstance().showToastNormal(
                    context.getString(if (removable) R.string.sticker_removed else R.string.sticker_added)
                )
                StickerPanelView.notifyDataChanged()
            } else {
                StickerTrace.e("STICKER_TRACE_FAVORITE_TOGGLE fail code=$code msg=$msg")
                WKToastUtils.getInstance().showToastNormal(msg.ifEmpty { context.getString(R.string.sticker_request_failed) })
            }
        }
        if (removable) {
            com.chat.sticker.service.StickerModel.instance.removeFavorite(targetType, targetId, emojiCode, callback)
        } else {
            com.chat.sticker.service.StickerModel.instance.addFavorite(targetType, targetId, emojiCode, callback)
        }
    }

    fun openStore(context: Context?) {
        if (context == null) return
        val intent = Intent(context, StickerStoreActivity::class.java)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openMySticker(context: Context?) {
        if (context == null) return
        val intent = Intent(context, StickerMyStickersActivity::class.java)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
