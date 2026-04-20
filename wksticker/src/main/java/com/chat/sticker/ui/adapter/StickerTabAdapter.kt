package com.chat.sticker.ui.adapter

import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.sticker.R

data class StickerTabItem(
    val key: String,
    val title: String,
    var selected: Boolean = false,
)

class StickerTabAdapter : BaseQuickAdapter<StickerTabItem, BaseViewHolder>(R.layout.item_sticker_tab) {
    override fun convert(holder: BaseViewHolder, item: StickerTabItem) {
        val titleTv = holder.getView<androidx.appcompat.widget.AppCompatTextView>(R.id.titleTv)
        titleTv.text = item.title
        titleTv.background = ContextCompat.getDrawable(
            context,
            if (item.selected) R.drawable.bg_sticker_tab_selected else R.drawable.bg_sticker_tab
        )
        titleTv.setTextColor(
            ContextCompat.getColor(
                context,
                if (item.selected) com.chat.base.R.color.white else com.chat.base.R.color.colorDark
            )
        )
    }
}
