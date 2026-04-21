package com.chat.sticker.utils

import com.chat.base.msg.model.WKGifContent
import com.chat.sticker.entity.StickerItem

@Suppress("UNUSED_PARAMETER")
object StickerTrace {
    fun d(message: String) {
    }

    fun e(message: String, throwable: Throwable? = null) {
    }

    fun itemSummary(item: StickerItem?): String {
        if (item == null) return "item=null"
        return "itemId=${item.itemId}, packageId=${item.packageId}, customId=${item.customId}, targetType=${item.targetType}, targetId=${item.targetId}, name=${item.name}, gifUrl=${item.gifUrl}, thumbUrl=${item.thumbUrl}, originUrl=${item.originUrl}, ext=${item.originExt}, mediaType=${item.sourceMediaType}, size=${item.width}x${item.height}"
    }

    fun gifSummary(content: WKGifContent?): String {
        if (content == null) return "gif=null"
        return "url=${content.url}, category=${content.category}, title=${content.title}, placeholder=${content.placeholder}, format=${content.format}, localPath=${content.localPath}, size=${content.width}x${content.height}"
    }
}
