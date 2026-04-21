package com.chat.sticker.ui.adapter

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.sticker.R
import com.chat.sticker.entity.StickerPackage

class StickerPackageAdapter : BaseQuickAdapter<StickerPackage, BaseViewHolder>(R.layout.item_sticker_package) {
    var showAction: Boolean = true
    var showSortHandle: Boolean = false
    var compactMode: Boolean = false

    override fun convert(holder: BaseViewHolder, item: StickerPackage) {
        val actionView = holder.getView<AppCompatTextView>(R.id.actionTv)
        val iconView = holder.getView<AppCompatImageView>(R.id.iconIv)
        val sortHandleView = holder.getView<AppCompatImageView>(R.id.sortHandleIv)
        val verticalPadding = AndroidUtilities.dp(if (compactMode) 7f else 12f)
        holder.itemView.setPadding(
            AndroidUtilities.dp(14f),
            verticalPadding,
            AndroidUtilities.dp(14f),
            verticalPadding
        )
        val iconSize = AndroidUtilities.dp(if (compactMode) 44f else 56f)
        iconView.layoutParams = iconView.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        holder.setText(R.id.nameTv, item.name)
        holder.setText(R.id.descTv, if (item.description.isNotEmpty()) item.description else item.tags)
        actionView.text = context.getString(if (item.isAdded) R.string.sticker_remove else R.string.sticker_add)
        actionView.setBackgroundResource(if (item.isAdded) R.drawable.bg_sticker_action_remove else R.drawable.bg_sticker_action)
        actionView.setTextColor(
            ContextCompat.getColor(context, if (item.isAdded) com.chat.base.R.color.red else com.chat.base.R.color.white)
        )
        holder.setGone(R.id.actionTv, !showAction)
        sortHandleView.visibility = if (showSortHandle) View.VISIBLE else View.GONE
        if (showSortHandle) {
            sortHandleView.clearColorFilter()
            WKImageDisplayUtils.limitResourceInside(sortHandleView, R.drawable.sticker_sort_handle_icon, 4f, 2f)
            sortHandleView.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, com.chat.base.R.color.color999), PorterDuff.Mode.MULTIPLY)
        }
        GlideUtils.getInstance().showImg(
            context,
            WKApiConfig.getShowUrl(if (item.icon.isNotEmpty()) item.icon else item.cover),
            iconView
        )
    }
}
