package com.chat.moments.ui.adapter

import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.moments.R
import com.chat.moments.entity.MomentNotice
import com.chat.moments.util.MomentUiUtils

class MomentNoticeAdapter : BaseQuickAdapter<MomentNotice, BaseViewHolder>(R.layout.item_moment_notice) {

    override fun convert(holder: BaseViewHolder, item: MomentNotice) {
        val avatarView = holder.getView<com.chat.base.ui.components.AvatarView>(R.id.avatarView)
        val titleTv = holder.getView<TextView>(R.id.titleTv)
        val contentTv = holder.getView<TextView>(R.id.contentTv)
        val timeTv = holder.getView<TextView>(R.id.timeTv)
        val thumbIv = holder.getView<ImageView>(R.id.thumbIv)
        avatarView.setSize(46f)
        MomentUiUtils.showAvatar(avatarView, item.fromUser.uid)
        titleTv.text = item.fromUser.name.ifEmpty { item.fromUser.uid }
        contentTv.text = MomentUiUtils.noticeDetailContent(context, item)
        timeTv.text = MomentUiUtils.formatPublishTime(item.createdAt)
        if (item.mediaThumb.isNotEmpty()) {
            thumbIv.visibility = android.view.View.VISIBLE
            MomentUiUtils.showImage(context, item.mediaThumb, thumbIv)
        } else {
            thumbIv.visibility = android.view.View.GONE
        }
    }
}
