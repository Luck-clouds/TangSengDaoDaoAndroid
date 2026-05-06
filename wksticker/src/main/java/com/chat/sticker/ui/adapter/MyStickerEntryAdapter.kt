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

data class MyStickerEntry(
    val type: String,
    val id: String,
    val name: String,
    val desc: String,
    val icon: String,
    val removable: Boolean,
)

class MyStickerEntryAdapter : BaseQuickAdapter<MyStickerEntry, BaseViewHolder>(R.layout.item_my_sticker_entry) {
    override fun convert(holder: BaseViewHolder, item: MyStickerEntry) {
        val sectionTitleView = holder.getView<androidx.appcompat.widget.AppCompatTextView>(R.id.sectionTitleTv)
        val rowLayout = holder.getView<View>(R.id.rowLayout)
        if (item.type == "section") {
            sectionTitleView.visibility = View.VISIBLE
            sectionTitleView.text = item.name
            rowLayout.visibility = View.GONE
            return
        }
        sectionTitleView.visibility = View.GONE
        rowLayout.visibility = View.VISIBLE
        val iconView = holder.getView<androidx.appcompat.widget.AppCompatImageView>(R.id.iconIv)
        holder.setText(R.id.nameTv, item.name)
        holder.setText(R.id.descTv, item.desc)
        holder.setGone(R.id.actionTv, !item.removable)
        iconView.clearColorFilter()
        iconView.background = null
        if (item.type == "custom") {
            WKImageDisplayUtils.limitResourceInside(iconView, R.mipmap.sticker_add_icon, 8f, 4f)
            iconView.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, com.chat.base.R.color.colorDark), PorterDuff.Mode.MULTIPLY)
        } else if (item.icon.isNotEmpty()) {
            WKImageDisplayUtils.prepareImageSlot(iconView, 3f)
            GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(item.icon), iconView)
        } else {
            WKImageDisplayUtils.limitResourceInside(iconView, R.mipmap.sticker_tab_icon, 4f, 4f)
        }
    }
}
