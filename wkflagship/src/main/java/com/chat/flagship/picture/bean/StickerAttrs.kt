package com.chat.flagship.picture.bean

import android.graphics.Bitmap

/**
 * 图片编辑贴纸属性
 * Created by Luckclouds and chatGPT.
 */
class StickerAttrs(
    var bitmap: Bitmap,
    var description: String = "",
    var rotation: Float = 0f,
    var scale: Float = 1f,
    var translateX: Float = 0f,
    var translateY: Float = 0f,
)
