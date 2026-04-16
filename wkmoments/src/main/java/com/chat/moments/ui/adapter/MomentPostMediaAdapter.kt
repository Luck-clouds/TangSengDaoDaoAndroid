package com.chat.moments.ui.adapter

import android.view.View
import android.widget.ImageView
import com.chat.base.utils.AndroidUtilities
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.moments.R
import com.chat.moments.entity.MomentMedia
import com.chat.moments.util.MomentUiUtils

class MomentPostMediaAdapter(
    private val totalCount: Int,
    private val onItemClick: (Int, MomentMedia, ImageView) -> Unit
) : BaseQuickAdapter<MomentMedia, BaseViewHolder>(R.layout.item_moment_post_media) {

    override fun convert(holder: BaseViewHolder, item: MomentMedia) {
        val mediaIv = holder.getView<ImageView>(R.id.mediaIv)
        val playIv = holder.getView<ImageView>(R.id.playIv)
        applyMediaSize(mediaIv, item)
        MomentUiUtils.showImage(context, if (item.coverUrl.isNotEmpty()) item.coverUrl else item.url, mediaIv)
        playIv.visibility = if (item.type == "video" || item.coverUrl.isNotEmpty()) View.VISIBLE else View.GONE
        mediaIv.setOnClickListener {
            onItemClick(holder.bindingAdapterPosition, item, mediaIv)
        }
    }

    private fun applyMediaSize(imageView: ImageView, media: MomentMedia) {
        val params = imageView.layoutParams
        if (totalCount != 1) {
            params.width = AndroidUtilities.dp(104f)
            params.height = AndroidUtilities.dp(104f)
            imageView.layoutParams = params
            return
        }
        val width = media.width.coerceAtLeast(1)
        val height = media.height.coerceAtLeast(1)
        when {
            height > width -> {
                params.width = AndroidUtilities.dp(134f)
                params.height = AndroidUtilities.dp(186f)
            }

            width > height -> {
                params.width = AndroidUtilities.dp(186f)
                params.height = AndroidUtilities.dp(134f)
            }

            else -> {
                params.width = AndroidUtilities.dp(156f)
                params.height = AndroidUtilities.dp(156f)
            }
        }
        imageView.layoutParams = params
    }
}
