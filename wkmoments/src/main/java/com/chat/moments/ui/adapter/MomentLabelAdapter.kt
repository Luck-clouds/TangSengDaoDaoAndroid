package com.chat.moments.ui.adapter

import android.widget.CheckBox
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.moments.R
import com.chat.moments.entity.MomentTagChoice

data class MomentLabelItem(
    val label: MomentTagChoice,
    var selected: Boolean = false
)

class MomentLabelAdapter : BaseQuickAdapter<MomentLabelItem, BaseViewHolder>(R.layout.item_moment_label_picker) {
    override fun convert(holder: BaseViewHolder, item: MomentLabelItem) {
        val checkBox = holder.getView<CheckBox>(R.id.checkBox)
        val nameTv = holder.getView<TextView>(R.id.nameTv)
        val memberTv = holder.getView<TextView>(R.id.memberTv)
        checkBox.setOnCheckedChangeListener(null)
        checkBox.isChecked = item.selected
        nameTv.text = item.label.name
        memberTv.text = item.label.memberNames.joinToString("、")
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            item.selected = isChecked
        }
        holder.itemView.setOnClickListener {
            item.selected = !item.selected
            checkBox.isChecked = item.selected
        }
    }
}
