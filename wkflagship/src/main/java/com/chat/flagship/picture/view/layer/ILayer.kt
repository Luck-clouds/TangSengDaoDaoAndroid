package com.chat.flagship.picture.view.layer

import android.graphics.Canvas
import android.view.MotionEvent

/**
 * 图片编辑图层接口
 * Created by Luckclouds and chatGPT.
 */
interface ILayer {
    fun onTouchEvent(event: MotionEvent): Boolean
    fun onSizeChanged(viewWidth: Int, viewHeight: Int, bitmapWidth: Int, bitmapHeight: Int)
    fun onDraw(canvas: Canvas)
}
