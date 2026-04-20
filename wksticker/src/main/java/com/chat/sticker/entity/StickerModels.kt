package com.chat.sticker.entity

import android.os.Parcelable
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import kotlinx.parcelize.Parcelize

private fun JSONObject?.readStringCompat(vararg keys: String): String {
    if (this == null) return ""
    keys.forEach { key ->
        val value = this.getString(key)
        if (!value.isNullOrEmpty()) return value
    }
    return ""
}

private fun JSONObject?.readIntCompat(vararg keys: String): Int {
    if (this == null) return 0
    keys.forEach { key ->
        if (this.containsKey(key)) return this.getIntValue(key)
    }
    return 0
}

private fun JSONObject?.readLongCompat(vararg keys: String): Long {
    if (this == null) return 0L
    keys.forEach { key ->
        if (this.containsKey(key)) return this.getLongValue(key)
    }
    return 0L
}

@Parcelize
data class StickerPackage(
    val packageId: String = "",
    val name: String = "",
    val icon: String = "",
    val cover: String = "",
    val description: String = "",
    val tags: String = "",
    val itemCount: Int = 0,
    val sortNum: Int = 0,
    val status: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    var isAdded: Boolean = false,
) : Parcelable {
    companion object {
        fun fromJson(json: JSONObject?): StickerPackage {
            if (json == null) return StickerPackage()
            val source = json.getJSONObject("package") ?: json
            return StickerPackage(
                packageId = source.readStringCompat("package_id", "PackageID"),
                name = source.readStringCompat("name", "Name"),
                icon = source.readStringCompat("icon", "Icon"),
                cover = source.readStringCompat("cover", "Cover"),
                description = source.readStringCompat("description", "Description"),
                tags = source.readStringCompat("tags", "Tags"),
                itemCount = source.readIntCompat("item_count", "ItemCount"),
                sortNum = if (source.containsKey("sort_num") || source.containsKey("SortNum")) {
                    source.readIntCompat("sort_num", "SortNum")
                } else {
                    json.readIntCompat("sort_num", "SortNum")
                },
                status = source.readIntCompat("status", "Status"),
                createdAt = source.readStringCompat("created_at", "CreatedAt"),
                updatedAt = source.readStringCompat("updated_at", "UpdatedAt"),
            )
        }
    }
}

@Parcelize
data class StickerItem(
    val itemId: String = "",
    val packageId: String = "",
    val customId: String = "",
    val emojiId: String = "",
    val name: String = "",
    val keyword: String = "",
    val emojiCode: String = "",
    val groupNo: String = "",
    val sortNum: Int = 0,
    val status: Int = 0,
    val originUrl: String = "",
    val gifUrl: String = "",
    val thumbUrl: String = "",
    val originExt: String = "",
    val sourceMediaType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    var selected: Boolean = false,
    var isAddCell: Boolean = false,
) : Parcelable {
    companion object {
        fun fromJson(json: JSONObject?): StickerItem {
            if (json == null) return StickerItem()
            val rawUrl = json.readStringCompat("url", "Url")
            val rawFormat = json.readStringCompat("format", "Format")
            val rawTitle = json.readStringCompat("title", "Title")
            val rawPlaceholder = json.readStringCompat("placeholder", "Placeholder")
            return StickerItem(
                itemId = json.readStringCompat("item_id", "ItemID"),
                packageId = json.readStringCompat("package_id", "PackageID").ifEmpty { json.readStringCompat("category", "Category") },
                customId = json.readStringCompat("custom_id", "CustomID"),
                emojiId = json.readStringCompat("emoji_id", "EmojiID"),
                name = json.readStringCompat("name", "Name").ifEmpty { rawTitle.ifEmpty { rawPlaceholder } },
                keyword = json.readStringCompat("keyword", "Keyword"),
                emojiCode = json.readStringCompat("emoji_code", "EmojiCode"),
                groupNo = json.readStringCompat("group_no", "GroupNo"),
                sortNum = json.readIntCompat("sort_num", "SortNum"),
                status = json.readIntCompat("status", "Status"),
                originUrl = json.readStringCompat("origin_url", "OriginURL").ifEmpty { rawUrl },
                gifUrl = json.readStringCompat("gif_url", "GifURL").ifEmpty {
                    if (rawFormat.contains("gif", true) || rawUrl.contains(".gif", true)) rawUrl else ""
                },
                thumbUrl = json.readStringCompat("thumb_url", "ThumbURL"),
                originExt = json.readStringCompat("origin_ext", "OriginExt").ifEmpty { rawFormat },
                sourceMediaType = json.readStringCompat("source_media_type", "SourceMediaType").ifEmpty {
                    if (rawFormat.contains("gif", true) || rawUrl.contains(".gif", true)) "image/gif" else ""
                },
                width = json.readIntCompat("width", "Width"),
                height = json.readIntCompat("height", "Height"),
                size = json.readLongCompat("size", "Size"),
                createdAt = json.readStringCompat("created_at", "CreatedAt"),
                updatedAt = json.readStringCompat("updated_at", "UpdatedAt"),
            )
        }

        fun fromArray(array: JSONArray?): MutableList<StickerItem> {
            val list = mutableListOf<StickerItem>()
            if (array == null) return list
            for (i in 0 until array.size) {
                list += fromJson(array.getJSONObject(i))
            }
            return list
        }
    }
}

data class StickerFavoriteItem(
    val targetType: String = "",
    val targetId: String = "",
    val emojiCode: String = "",
    val sortNum: Int = 0,
    val name: String = "",
    val detail: StickerItem = StickerItem(),
) {
    companion object {
        fun fromJson(json: JSONObject?): StickerFavoriteItem {
            if (json == null) return StickerFavoriteItem()
            return StickerFavoriteItem(
                targetType = json.readStringCompat("target_type", "TargetType"),
                targetId = json.readStringCompat("target_id", "TargetID"),
                emojiCode = json.readStringCompat("emoji_code", "EmojiCode"),
                sortNum = json.readIntCompat("sort_num", "SortNum"),
                name = json.readStringCompat("name", "Name"),
                detail = StickerItem.fromJson(json.getJSONObject("detail") ?: json.getJSONObject("Detail"))
            )
        }

        fun toStickerItems(array: JSONArray?): MutableList<StickerItem> {
            val list = mutableListOf<StickerItem>()
            if (array == null) return list
            for (i in 0 until array.size) {
                val favorite = fromJson(array.getJSONObject(i))
                val detail = favorite.detail
                list += detail.copy(
                    itemId = if (detail.itemId.isNotEmpty()) detail.itemId else favorite.targetId,
                    emojiCode = if (detail.emojiCode.isNotEmpty()) detail.emojiCode else favorite.emojiCode
                )
            }
            return list
        }
    }
}

data class StickerPanelData(
    val tabs: JSONArray = JSONArray(),
    val myPackages: MutableList<StickerPackage> = mutableListOf(),
    val emojiGroups: JSONArray = JSONArray(),
    val favoriteCount: Int = 0,
    val customCount: Int = 0,
) {
    companion object {
        fun fromJson(json: JSONObject?): StickerPanelData {
            if (json == null) return StickerPanelData()
            val myPackages = mutableListOf<StickerPackage>()
            val packageArray = json.getJSONArray("my_packages")
            if (packageArray != null) {
                for (i in 0 until packageArray.size) {
                    myPackages += StickerPackage.fromJson(packageArray.getJSONObject(i)).copy(isAdded = true)
                }
            }
            return StickerPanelData(
                tabs = json.getJSONArray("tabs") ?: JSONArray(),
                myPackages = myPackages,
                emojiGroups = json.getJSONArray("emoji_groups") ?: JSONArray(),
                favoriteCount = json.readIntCompat("favorite_count", "favoriteCount", "FavoriteCount"),
                customCount = json.readIntCompat("custom_count", "customCount", "CustomCount"),
            )
        }
    }
}
