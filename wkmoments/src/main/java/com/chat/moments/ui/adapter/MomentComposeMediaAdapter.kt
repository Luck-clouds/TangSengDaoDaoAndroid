package com.chat.moments.ui.adapter

/**
 * 朋友圈编辑页媒体适配器
 * Created by Luckclouds.
 */

import android.view.View
import android.widget.ImageView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.moments.R
import com.chat.moments.entity.MomentComposeMedia
import com.chat.moments.util.MomentUiUtils

class MomentComposeMediaAdapter(
    private val onAddClick: () -> Unit,
    private val onDeleteClick: (Int, MomentComposeMedia) -> Unit,
    private val onPreviewClick: (MomentComposeMedia, ImageView) -> Unit
) : BaseQuickAdapter<MomentComposeMedia?, BaseViewHolder>(R.layout.item_moment_compose_media) {

    companion object {
        private const val ITEM_ADD = 1001
        private const val ITEM_MEDIA = 1002
    }

    override fun getDefItemViewType(position: Int): Int {
        return if (getItem(position) == null) ITEM_ADD else ITEM_MEDIA
    }

    override fun convert(holder: BaseViewHolder, item: MomentComposeMedia?) {
        holder.itemView.post {
            val width = holder.itemView.width
            if (width > 0 && holder.itemView.layoutParams.height != width) {
                holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
                    height = width
                }
            }
        }
        val mediaIv = holder.getView<ImageView>(R.id.mediaIv)
        val addIv = holder.getView<ImageView>(R.id.addIv)
        val playIv = holder.getView<ImageView>(R.id.playIv)
        val deleteIv = holder.getView<ImageView>(R.id.deleteIv)
        val selectedIv = holder.getView<ImageView>(R.id.selectedIv)
        if (item == null) {
            mediaIv.visibility = View.INVISIBLE
            addIv.visibility = View.VISIBLE
            playIv.visibility = View.GONE
            deleteIv.visibility = View.GONE
            selectedIv.visibility = View.GONE
            addIv.setOnClickListener { onAddClick() }
            return
        }
        mediaIv.visibility = View.VISIBLE
        addIv.visibility = View.GONE
        deleteIv.visibility = View.VISIBLE
        selectedIv.visibility = View.VISIBLE
        MomentUiUtils.showImage(context, item.coverPath ?: item.localPath, mediaIv)
        playIv.visibility = if (item.type == MomentComposeMedia.TYPE_VIDEO) View.VISIBLE else View.GONE
        deleteIv.setOnClickListener { onDeleteClick(holder.bindingAdapterPosition, item) }
        mediaIv.setOnClickListener { onPreviewClick(item, mediaIv) }
    }
}
