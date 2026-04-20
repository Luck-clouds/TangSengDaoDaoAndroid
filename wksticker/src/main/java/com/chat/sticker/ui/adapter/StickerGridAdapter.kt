package com.chat.sticker.ui.adapter

import android.view.View
import android.view.MotionEvent
import androidx.core.view.isVisible
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.sticker.R
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.ui.StickerFullScreenPreview
import com.chat.sticker.utils.StickerTrace

class StickerGridAdapter : BaseQuickAdapter<StickerItem, BaseViewHolder>(R.layout.item_sticker_grid) {
    var editMode: Boolean = false

    override fun convert(holder: BaseViewHolder, item: StickerItem) {
        val imageView = holder.getView<androidx.appcompat.widget.AppCompatImageView>(R.id.imageView)
        val checkView = holder.getView<androidx.appcompat.widget.AppCompatImageView>(R.id.checkIv)
        val titleView = holder.getView<androidx.appcompat.widget.AppCompatTextView>(R.id.nameTv)
        if (item.isAddCell) {
            WKImageDisplayUtils.limitResourceInside(imageView, R.mipmap.sticker_plus_icon, 4f, 2f)
            titleView.isVisible = true
            titleView.text = context.getString(R.string.sticker_add)
            checkView.visibility = View.GONE
            return
        }
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
            StickerFullScreenPreview.show(context, item)
            true
        }
        holder.itemView.setOnTouchListener { _, event ->
            if (!item.isAddCell && (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL)) {
                StickerFullScreenPreview.dismiss()
            }
            false
        }
        titleView.isVisible = item.name.isNotEmpty()
        titleView.text = item.name
        checkView.visibility = if (editMode) View.VISIBLE else View.GONE
        checkView.isSelected = item.selected
    }
}
