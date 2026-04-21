package com.chat.sticker

import android.app.Application
import android.content.Context
import android.content.Intent
import com.chat.base.config.WKApiConfig
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
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.msg.WKEmojiStickerContent
import com.chat.sticker.msg.WKVectorStickerContent
import com.chat.sticker.service.StickerModel
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

    private data class FavoriteTarget(
        val targetType: String,
        val targetId: String = "",
        val emojiCode: String = "",
    )

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
                R.mipmap.sticker_album_icon,
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
            if (!hasFavoriteTargetSource(content)) return@setMethod null
            val target = resolveFavoriteTarget(content)
            val removable = isFavoriteSticker(content, target)
            ChatItemPopupMenu(
                if (removable) R.mipmap.sticker_remove_icon else R.mipmap.sticker_add_icon,
                application?.getString(if (removable) R.string.sticker_cancel_favorite else R.string.sticker_add) ?: if (removable) "取消" else "添加",
                object : ChatItemPopupMenu.IPopupItemClick {
                    override fun onClick(mMsg: WKMsg, iConversationContext: IConversationContext) {
                        toggleFavorite(iConversationContext.chatActivity, content, removable)
                    }
                }
            )
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

    private fun isFavoriteSticker(content: WKGifContent, target: FavoriteTarget): Boolean {
        if (content.placeholder.orEmpty().startsWith("favorite")) return true
        return StickerModel.instance.isFavorite(
            target.targetType,
            target.targetId,
            target.emojiCode,
            listOf(content.url.orEmpty(), extractPlaceholderHref(content.placeholder.orEmpty()))
        )
    }

    private fun isStickerMsgType(type: Int): Boolean {
        return type == WKContentType.WK_GIF
                || type == WKContentType.WK_VECTOR_STICKER
                || type == WKContentType.WK_EMOJI_STICKER
    }

    private fun toggleFavorite(context: Context, content: WKGifContent, removable: Boolean) {
        val target = resolveFavoriteTarget(content)
        StickerTrace.d("STICKER_TRACE_FAVORITE_TOGGLE removable=$removable targetType=${target.targetType} targetId=${target.targetId} emojiCode=${target.emojiCode} ${StickerTrace.gifSummary(content)}")
        if (target.targetType != "builtin_emoji" && target.targetId.isEmpty()) {
            resolveFavoriteTargetFromApi(content, target.targetType) { resolvedId ->
                if (resolvedId.isEmpty()) {
                    StickerTrace.e("STICKER_TRACE_FAVORITE_TOGGLE abort reason=empty_target_id_after_lookup targetType=${target.targetType} ${StickerTrace.gifSummary(content)}")
                    WKToastUtils.getInstance().showToastNormal(context.getString(R.string.sticker_request_failed))
                    return@resolveFavoriteTargetFromApi
                }
                performFavoriteToggle(context, target.copy(targetId = resolvedId), removable)
            }
            return
        }
        performFavoriteToggle(context, target, removable)
    }

    private fun performFavoriteToggle(context: Context, target: FavoriteTarget, removable: Boolean) {
        if (target.targetType != "builtin_emoji" && target.targetId.isEmpty()) {
            StickerTrace.e("STICKER_TRACE_FAVORITE_TOGGLE abort reason=empty_target_id targetType=${target.targetType}")
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.sticker_request_failed))
            return
        }
        val callback: (Int, String) -> Unit = { code, msg ->
            if (code == HttpResponseCode.success.toInt()) {
                StickerTrace.d("STICKER_TRACE_FAVORITE_TOGGLE success removable=$removable")
                WKToastUtils.getInstance().showToastNormal(
                    context.getString(if (removable) R.string.sticker_removed else R.string.sticker_added)
                )
                StickerModel.instance.getFavorites { _, _, _ -> }
                StickerPanelView.notifyDataChanged()
            } else {
                StickerTrace.e("STICKER_TRACE_FAVORITE_TOGGLE fail code=$code msg=$msg")
                WKToastUtils.getInstance().showToastNormal(msg.ifEmpty { context.getString(R.string.sticker_request_failed) })
            }
        }
        if (removable) {
            StickerModel.instance.removeFavorite(target.targetType, target.targetId, target.emojiCode, callback)
        } else {
            StickerModel.instance.addFavorite(target.targetType, target.targetId, target.emojiCode, callback)
        }
    }

    private fun hasFavoriteTargetSource(content: WKGifContent): Boolean {
        return content.url.orEmpty().isNotEmpty() ||
            content.placeholder.orEmpty().isNotEmpty() ||
            content.format.orEmpty().isNotEmpty() ||
            content.category.orEmpty().isNotEmpty()
    }

    private fun resolveFavoriteTarget(content: WKGifContent): FavoriteTarget {
        val placeholder = content.placeholder.orEmpty()
        val metaType = readSvgAttr(placeholder, "data-sticker-target-type")
        val metaId = readSvgAttr(placeholder, "data-sticker-target-id")
        val targetType = metaType.ifEmpty {
            when {
                placeholder == "favorite_custom" || placeholder == "custom" || content.category.orEmpty() == "custom" -> "custom_item"
                placeholder == "builtin_emoji" -> "builtin_emoji"
                else -> "platform_item"
            }
        }
        val format = content.format.orEmpty()
        val targetId = metaId.ifEmpty {
            if (targetType == "builtin_emoji") {
                ""
            } else {
                format.takeUnless { isFormatOnly(it) }.orEmpty()
            }
        }
        val emojiCode = if (targetType == "builtin_emoji") metaId.ifEmpty { format } else ""
        return FavoriteTarget(targetType, targetId, emojiCode)
    }

    private fun resolveFavoriteTargetFromApi(content: WKGifContent, targetType: String, callback: (String) -> Unit) {
        StickerTrace.d("STICKER_TRACE_FAVORITE_LOOKUP start targetType=$targetType category=${content.category.orEmpty()} url=${content.url.orEmpty()}")
        when (targetType) {
            "custom_item" -> {
                StickerModel.instance.getCustom { code, msg, list ->
                    if (code != HttpResponseCode.success.toInt()) {
                        StickerTrace.e("STICKER_TRACE_FAVORITE_LOOKUP custom_fail code=$code msg=$msg")
                        callback("")
                        return@getCustom
                    }
                    callback(findMatchingTargetId(content, list, targetType))
                }
            }
            "platform_item" -> {
                val packageId = content.category.orEmpty()
                if (packageId.isEmpty() || packageId == "favorites" || packageId == "custom") {
                    StickerTrace.e("STICKER_TRACE_FAVORITE_LOOKUP platform_abort reason=empty_or_invalid_category category=$packageId")
                    callback("")
                    return
                }
                StickerModel.instance.getPackageDetail(packageId) { code, msg, _, list ->
                    if (code != HttpResponseCode.success.toInt()) {
                        StickerTrace.e("STICKER_TRACE_FAVORITE_LOOKUP package_fail code=$code msg=$msg packageId=$packageId")
                        callback("")
                        return@getPackageDetail
                    }
                    callback(findMatchingTargetId(content, list, targetType))
                }
            }
            else -> callback("")
        }
    }

    private fun findMatchingTargetId(content: WKGifContent, list: MutableList<StickerItem>, targetType: String): String {
        val targetUrls = listOf(content.url.orEmpty(), extractPlaceholderHref(content.placeholder.orEmpty()))
            .filter { isUsefulMediaPath(it) }
        val match = list.firstOrNull { item ->
            val itemUrls = listOf(item.gifUrl, item.originUrl, item.thumbUrl).filter { isUsefulMediaPath(it) }
            targetUrls.any { targetUrl -> itemUrls.any { itemUrl -> sameMediaPath(targetUrl, itemUrl) } }
        }
        val resolvedId = match?.let { item ->
            when (targetType) {
                "custom_item" -> item.customId.ifEmpty { item.itemId }
                "dynamic_emoji" -> item.emojiId.ifEmpty { item.itemId }
                else -> item.itemId
            }
        }.orEmpty()
        StickerTrace.d("STICKER_TRACE_FAVORITE_LOOKUP result targetType=$targetType targetId=$resolvedId matched=${StickerTrace.itemSummary(match)}")
        return resolvedId
    }

    private fun readSvgAttr(value: String, attr: String): String {
        val raw = Regex("""\Q$attr\E="([^"]+)"""").find(value)?.groupValues?.getOrNull(1).orEmpty()
        return raw
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun extractPlaceholderHref(placeholder: String): String {
        return Regex("""href="([^"]+)"""").find(placeholder)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun isFormatOnly(value: String): Boolean {
        val cleanValue = value.trim().lowercase().removePrefix(".")
        return cleanValue.isEmpty() || cleanValue in setOf("gif", "png", "jpg", "jpeg", "webp", "apng", "svg", "tgs", "json", "lim")
    }

    private fun isUsefulMediaPath(value: String): Boolean {
        val cleanValue = value.trim()
        return cleanValue.length > 5 && (cleanValue.startsWith("http", true) || cleanValue.startsWith("/") || cleanValue.contains("/"))
    }

    private fun sameMediaPath(left: String, right: String): Boolean {
        val leftRaw = left.substringBefore("?").trim()
        val rightRaw = right.substringBefore("?").trim()
        val leftShow = WKApiConfig.getShowUrl(leftRaw).substringBefore("?")
        val rightShow = WKApiConfig.getShowUrl(rightRaw).substringBefore("?")
        return leftRaw == rightRaw ||
            leftShow == rightShow ||
            leftShow.endsWith(rightRaw) ||
            rightShow.endsWith(leftRaw)
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
