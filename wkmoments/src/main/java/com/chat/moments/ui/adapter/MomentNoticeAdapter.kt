package com.chat.moments.ui.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.moments.R
import com.chat.moments.entity.MomentNotice
import com.chat.moments.util.MomentUiUtils

class MomentNoticeAdapter(
    private val onItemClick: (MomentNotice) -> Unit
) : BaseQuickAdapter<MomentNotice, BaseViewHolder>(R.layout.item_moment_notice) {

    override fun convert(holder: BaseViewHolder, item: MomentNotice) {
        val avatarView = holder.getView<com.chat.base.ui.components.AvatarView>(R.id.avatarView)
        val titleTv = holder.getView<TextView>(R.id.titleTv)
        val contentTv = holder.getView<TextView>(R.id.contentTv)
        val timeTv = holder.getView<TextView>(R.id.timeTv)
        val thumbIv = holder.getView<ImageView>(R.id.thumbIv)
        val unreadDotView = holder.getView<View>(R.id.unreadDotView)
        avatarView.setSize(44f)
        MomentUiUtils.showAvatar(avatarView, item.fromUser.uid)
        titleTv.text = item.fromUser.name.ifEmpty { item.fromUser.uid }
        contentTv.text = item.content
        timeTv.text = MomentUiUtils.formatPublishTime(item.createdAt)
        unreadDotView.visibility = if (item.isRead) View.GONE else View.VISIBLE
        if (item.mediaThumb.isNotEmpty()) {
            thumbIv.visibility = View.VISIBLE
            MomentUiUtils.showImage(context, item.mediaThumb, thumbIv)
        } else {
            thumbIv.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }
}
