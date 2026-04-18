package com.chat.flagship.picture.bean

import android.graphics.Paint
import android.graphics.Path

/**
 * 图片编辑画笔轨迹
 * Created by Luckclouds .
 */
class PaintPath(path: Path, paint: Paint) {
    val paint = Paint(paint)
    val path = Path(path)
}
