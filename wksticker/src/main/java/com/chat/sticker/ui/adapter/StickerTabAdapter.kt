package com.chat.sticker.ui.adapter

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.sticker.R

data class StickerTabItem(
    val key: String,
    val title: String,
    val iconUrl: String = "",
    val iconRes: Int = 0,
    var selected: Boolean = false,
)

class StickerTabAdapter : BaseQuickAdapter<StickerTabItem, BaseViewHolder>(R.layout.item_sticker_tab) {
    override fun convert(holder: BaseViewHolder, item: StickerTabItem) {
        val iconIv = holder.getView<androidx.appcompat.widget.AppCompatImageView>(R.id.iconIv)
        holder.getView<View>(R.id.indicatorView).visibility = if (item.selected) View.VISIBLE else View.INVISIBLE
        iconIv.clearColorFilter()
        iconIv.background = null
        if (item.iconUrl.isNotEmpty()) {
            WKImageDisplayUtils.prepareImageSlot(iconIv, 2f)
            GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(item.iconUrl), iconIv)
        } else {
            WKImageDisplayUtils.limitResourceInside(iconIv, item.iconRes.takeIf { it != 0 } ?: R.drawable.ic_sticker_favorite_nav, 2f, 1f)
            iconIv.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(context, if (item.selected) com.chat.base.R.color.colorAccent else com.chat.base.R.color.color999),
                PorterDuff.Mode.SRC_IN
            )
        }
    }
}
