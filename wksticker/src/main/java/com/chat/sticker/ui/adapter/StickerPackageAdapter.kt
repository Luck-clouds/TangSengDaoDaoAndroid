package com.chat.sticker.ui.adapter

import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.sticker.R
import com.chat.sticker.entity.StickerPackage

class StickerPackageAdapter : BaseQuickAdapter<StickerPackage, BaseViewHolder>(R.layout.item_sticker_package) {
    var showAction: Boolean = true

    override fun convert(holder: BaseViewHolder, item: StickerPackage) {
        val actionView = holder.getView<AppCompatTextView>(R.id.actionTv)
        holder.setText(R.id.nameTv, item.name)
        holder.setText(R.id.descTv, if (item.description.isNotEmpty()) item.description else item.tags)
        actionView.text = context.getString(if (item.isAdded) R.string.sticker_remove else R.string.sticker_add)
        actionView.setBackgroundResource(if (item.isAdded) R.drawable.bg_sticker_action_remove else R.drawable.bg_sticker_action)
        actionView.setTextColor(
            ContextCompat.getColor(context, if (item.isAdded) com.chat.base.R.color.red else com.chat.base.R.color.white)
        )
        holder.setGone(R.id.actionTv, !showAction)
        GlideUtils.getInstance().showImg(
            context,
            WKApiConfig.getShowUrl(if (item.icon.isNotEmpty()) item.icon else item.cover),
            holder.getView(R.id.iconIv)
        )
    }
}
