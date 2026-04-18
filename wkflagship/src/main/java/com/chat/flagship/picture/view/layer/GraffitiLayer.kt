package com.chat.flagship.picture.view.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import com.chat.flagship.picture.bean.PaintPath
import com.chat.flagship.picture.view.FlagshipPictureEditorView
import java.util.Stack
import kotlin.math.abs

/**
 * 图片涂鸦图层
 * Created by Luckclouds .
 */
class GraffitiLayer(private val parent: View) : ILayer {

    companion object {
        private const val DEFAULT_PAINT_SIZE = 20.0f//涂鸦大小
        private const val DEFAULT_ERASER_SIZE = 50.0f//橡皮擦大小
        private const val TOUCH_TOLERANCE = 4f
    }

    private lateinit var graffitiBitmap: Bitmap
    private var graffitiCanvas = Canvas()
    private val paintPaths = Stack<PaintPath>()
    private val redoPaths = Stack<PaintPath>()
    private val paint = Paint()
    private val path = Path()
    private var touchX = 0f
    private var touchY = 0f

    var isEnabled = false

    init {
        parent.setLayerType(LAYER_TYPE_HARDWARE, null)
        paint.isAntiAlias = true
        paint.isDither = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        setPaintMode(FlagshipPictureEditorView.Mode.GRAFFITI)
    }

    fun setParentScale(scale: Float) {
        paint.strokeWidth = DEFAULT_PAINT_SIZE / scale
    }

    fun setPaintMode(mode: FlagshipPictureEditorView.Mode) {
        if (mode == FlagshipPictureEditorView.Mode.GRAFFITI) {
            paint.strokeWidth = DEFAULT_PAINT_SIZE
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        } else if (mode == FlagshipPictureEditorView.Mode.ERASER) {
            paint.strokeWidth = DEFAULT_ERASER_SIZE
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    fun setPaintColor(@ColorInt color: Int) {
        paint.color = color
    }

    fun undo(): Boolean {
        if (paintPaths.isNotEmpty()) {
            path.reset()
            graffitiCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
            redoPaths.push(paintPaths.pop())
            for (linePath in paintPaths) {
                graffitiCanvas.drawPath(linePath.path, linePath.paint)
            }
            parent.postInvalidate()
        }
        return paintPaths.isNotEmpty()
    }

    fun clear() {
        path.reset()
        paintPaths.clear()
        redoPaths.clear()
        graffitiCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
        parent.postInvalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEnabled) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    path.reset()
                    path.moveTo(event.x, event.y)
                    touchX = event.x
                    touchY = event.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - touchX)
                    val dy = abs(event.y - touchY)
                    if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                        val x = (event.x + touchX) * 0.5f
                        val y = (event.y + touchY) * 0.5f
                        path.quadTo(touchX, touchY, x, y)
                        touchX = event.x
                        touchY = event.y
                    }
                }

                MotionEvent.ACTION_UP -> {
                    path.lineTo(event.x, event.y)
                    paintPaths.push(PaintPath(path, paint))
                }
            }
            graffitiCanvas.drawPath(path, paint)
            parent.postInvalidate()
        }
        return isEnabled
    }

    override fun onSizeChanged(viewWidth: Int, viewHeight: Int, bitmapWidth: Int, bitmapHeight: Int) {
        graffitiBitmap = createBitmap(bitmapWidth, bitmapHeight)
        graffitiCanvas.setBitmap(graffitiBitmap)
        if (paintPaths.isNotEmpty()) {
            graffitiCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
            for (linePath in paintPaths) {
                graffitiCanvas.drawPath(linePath.path, linePath.paint)
            }
            parent.postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(graffitiBitmap, 0f, 0f, null)
    }
}
