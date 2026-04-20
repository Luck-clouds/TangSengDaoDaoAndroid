package com.chat.sticker.utils

import com.chat.base.msg.model.WKGifContent
import com.chat.base.utils.WKLogUtils
import com.chat.sticker.entity.StickerItem

object StickerTrace {
    const val TAG = "WK_STICKER_TRACE"

    fun d(message: String) {
        WKLogUtils.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            WKLogUtils.e(TAG, message)
        } else {
            WKLogUtils.e(TAG, message, throwable)
        }
    }

    fun itemSummary(item: StickerItem?): String {
        if (item == null) return "item=null"
        return "itemId=${item.itemId}, packageId=${item.packageId}, customId=${item.customId}, name=${item.name}, gifUrl=${item.gifUrl}, thumbUrl=${item.thumbUrl}, originUrl=${item.originUrl}, ext=${item.originExt}, mediaType=${item.sourceMediaType}, size=${item.width}x${item.height}"
    }

    fun gifSummary(content: WKGifContent?): String {
        if (content == null) return "gif=null"
        return "url=${content.url}, category=${content.category}, title=${content.title}, placeholder=${content.placeholder}, format=${content.format}, localPath=${content.localPath}, size=${content.width}x${content.height}"
    }
}
