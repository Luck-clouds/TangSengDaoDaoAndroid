package com.chat.sticker.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.sticker.R
import com.chat.sticker.entity.StickerPackage

class StickerPackageAdapter : BaseQuickAdapter<StickerPackage, BaseViewHolder>(R.layout.item_sticker_package) {
    var showAction: Boolean = true

    override fun convert(holder: BaseViewHolder, item: StickerPackage) {
        holder.setText(R.id.nameTv, item.name)
        holder.setText(R.id.descTv, if (item.description.isNotEmpty()) item.description else item.tags)
        holder.setText(R.id.actionTv, context.getString(if (item.isAdded) R.string.sticker_remove else R.string.sticker_add))
        holder.setGone(R.id.actionTv, !showAction)
        GlideUtils.getInstance().showImg(
            context,
            WKApiConfig.getShowUrl(if (item.icon.isNotEmpty()) item.icon else item.cover),
            holder.getView(R.id.iconIv)
        )
    }
}
