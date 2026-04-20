package com.chat.sticker.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
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
        holder.setText(R.id.nameTv, item.name)
        holder.setText(R.id.descTv, item.desc)
        holder.setGone(R.id.actionTv, !item.removable)
        if (item.icon.isNotEmpty()) {
            GlideUtils.getInstance().showImg(context, WKApiConfig.getShowUrl(item.icon), holder.getView(R.id.iconIv))
        } else {
            holder.setImageResource(R.id.iconIv, R.mipmap.sticker_tab_icon)
        }
    }
}
