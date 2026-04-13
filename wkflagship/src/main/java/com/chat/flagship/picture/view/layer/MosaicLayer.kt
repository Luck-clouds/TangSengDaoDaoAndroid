package com.chat.flagship.picture.view.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import com.chat.flagship.picture.bean.PaintPath
import java.util.Stack
import kotlin.math.abs

/**
 * 图片马赛克图层
 * Created by Luckclouds and chatGPT.
 */
class MosaicLayer(private val parent: View) : ILayer {

    companion object {
        private const val DEFAULT_PAINT_SIZE = 30.0f
        private const val TOUCH_TOLERANCE = 4f
    }

    private lateinit var mosaicBitmap: Bitmap
    private var mosaicCanvas = Canvas()
    private var parentBitmap: Bitmap? = null
    private val paintPaths = Stack<PaintPath>()
    private val rectF = RectF()
    private val paint = Paint()
    private val path = Path()
    private var touchX = 0f
    private var touchY = 0f

    var isEnabled = false

    init {
        paint.alpha = 0
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = DEFAULT_PAINT_SIZE
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun setParentBitmap(bitmap: Bitmap) {
        parentBitmap = bitmap
    }

    fun setParentScale(scale: Float) {
        paint.strokeWidth = DEFAULT_PAINT_SIZE / scale
    }

    fun undo(): Boolean {
        if (paintPaths.isNotEmpty()) {
            path.reset()
            parentBitmap?.let {
                mosaicCanvas.drawBitmap(it, null, rectF, null)
            }
            paintPaths.pop()
            for (linePath in paintPaths) {
                mosaicCanvas.drawPath(linePath.path, linePath.paint)
            }
            parent.postInvalidate()
        }
        return paintPaths.isNotEmpty()
    }

    fun clear() {
        path.reset()
        paintPaths.clear()
        parentBitmap?.let {
            mosaicCanvas.drawBitmap(it, null, rectF, null)
        } ?: mosaicCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
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
            mosaicCanvas.drawPath(path, paint)
            parent.postInvalidate()
        }
        return isEnabled
    }

    override fun onSizeChanged(viewWidth: Int, viewHeight: Int, bitmapWidth: Int, bitmapHeight: Int) {
        rectF.set(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat())
        mosaicBitmap = createBitmap(bitmapWidth, bitmapHeight)
        mosaicCanvas.setBitmap(mosaicBitmap)
        parentBitmap?.let {
            mosaicCanvas.drawBitmap(it, null, rectF, null)
        }
        if (paintPaths.isNotEmpty()) {
            for (linePath in paintPaths) {
                mosaicCanvas.drawPath(linePath.path, linePath.paint)
            }
            parent.postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(mosaicBitmap, 0f, 0f, null)
    }
}
