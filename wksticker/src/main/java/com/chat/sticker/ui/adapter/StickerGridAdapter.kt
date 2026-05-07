package com.chat.sticker.ui.adapter

import android.view.View
import android.view.MotionEvent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.ui.Theme
import com.chat.base.ui.components.CheckBox
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.sticker.R
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.ui.StickerCustomActivity
import com.chat.sticker.ui.StickerFullScreenPreview
import com.chat.sticker.utils.StickerTrace

class StickerGridAdapter : BaseQuickAdapter<StickerItem, BaseViewHolder>(R.layout.item_sticker_grid) {
    var editMode: Boolean = false
    var showTitles: Boolean = true
    var previewMoveResolver: ((Float, Float) -> StickerItem?)? = null

    override fun convert(holder: BaseViewHolder, item: StickerItem) {
        val sectionHeaderLayout = holder.getView<View>(R.id.sectionHeaderLayout)
        val contentLayout = holder.getView<View>(R.id.contentLayout)
        val sectionAddIv = holder.getView<androidx.appcompat.widget.AppCompatImageView>(R.id.sectionAddIv)
        if (item.isSectionHeader) {
            sectionHeaderLayout.visibility = View.VISIBLE
            contentLayout.visibility = View.GONE
            holder.setText(R.id.sectionTitleTv, item.sectionName)
            sectionAddIv.visibility = if (item.showAddButton) View.VISIBLE else View.GONE
            sectionAddIv.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, com.chat.base.R.color.color999), PorterDuff.Mode.MULTIPLY)
            sectionAddIv.setOnClickListener {
                context.startActivity(Intent(context, StickerCustomActivity::class.java))
            }
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.setOnTouchListener(null)
            return
        }
        sectionHeaderLayout.visibility = View.GONE
        contentLayout.visibility = View.VISIBLE
        val imageView = holder.getView<androidx.appcompat.widget.AppCompatImageView>(R.id.imageView)
        val checkView = holder.getView<CheckBox>(R.id.checkIv)
        val titleView = holder.getView<androidx.appcompat.widget.AppCompatTextView>(R.id.nameTv)
        if (item.isAddCell) {
            imageView.background = null
            WKImageDisplayUtils.limitResourceInside(imageView, R.mipmap.sticker_plus_icon, 11f, 3f)
            imageView.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, com.chat.base.R.color.color999), PorterDuff.Mode.MULTIPLY)
            titleView.isVisible = showTitles
            titleView.text = context.getString(R.string.sticker_add)
            checkView.visibility = View.GONE
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.setOnTouchListener(null)
            return
        }
        imageView.clearColorFilter()
        imageView.background = null
        val previewUrl = when {
            item.gifUrl.isNotEmpty() -> WKApiConfig.getShowUrl(item.gifUrl)
            item.originUrl.isNotEmpty() && (item.originExt.contains("gif", true) || item.sourceMediaType.contains("gif", true) || item.originUrl.contains(".gif", true)) -> WKApiConfig.getShowUrl(item.originUrl)
            item.thumbUrl.isNotEmpty() -> WKApiConfig.getShowUrl(item.thumbUrl)
            else -> WKApiConfig.getShowUrl(item.originUrl)
        }
        val useGif = item.gifUrl.isNotEmpty()
            || item.sourceMediaType.contains("gif", true)
            || item.originExt.contains("gif", true)
            || previewUrl.contains(".gif", true)
        if (previewUrl.isEmpty()) {
            StickerTrace.e("STICKER_TRACE_GRID_BIND empty_url ${StickerTrace.itemSummary(item)}")
        } else {
            StickerTrace.d("STICKER_TRACE_GRID_BIND url=$previewUrl useGif=$useGif ${StickerTrace.itemSummary(item)}")
        }
        WKImageDisplayUtils.prepareImageSlot(imageView, 4f)
        if (useGif) {
            GlideUtils.getInstance().showGif(context, previewUrl, imageView, null)
        } else {
            GlideUtils.getInstance().showImg(context, previewUrl, imageView)
        }
        holder.itemView.setOnLongClickListener {
            if (item.isAddCell) return@setOnLongClickListener false
            StickerFullScreenPreview.show(context, item, previewMoveResolver)
            true
        }
        holder.itemView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> StickerFullScreenPreview.handleMove(event.rawX, event.rawY)
                MotionEvent.ACTION_UP -> StickerFullScreenPreview.handleRelease(true)
                MotionEvent.ACTION_CANCEL -> StickerFullScreenPreview.handleRelease(false)
            }
            false
        }
        titleView.isVisible = showTitles && item.name.isNotEmpty()
        titleView.text = item.name
        checkView.visibility = if (editMode) View.VISIBLE else View.GONE
        checkView.setResId(context, com.chat.base.R.mipmap.round_check2)
        checkView.setDrawBackground(true)
        checkView.setHasBorder(true)
        checkView.setStrokeWidth(AndroidUtilities.dp(2f))
        checkView.setBorderColor(ContextCompat.getColor(context, com.chat.base.R.color.layoutColor))
        checkView.setSize(24)
        checkView.setColor(Theme.colorAccount, ContextCompat.getColor(context, com.chat.base.R.color.white))
        checkView.setChecked(item.selected, true)
    }
}
