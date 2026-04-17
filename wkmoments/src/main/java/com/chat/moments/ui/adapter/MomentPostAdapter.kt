package com.chat.moments.ui.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.moments.R
import com.chat.moments.entity.MomentPost
import com.chat.moments.util.MomentUiUtils

class MomentPostAdapter(
    private val onLikeClick: (MomentPost) -> Unit,
    private val onCommentClick: (MomentPost, com.chat.moments.entity.MomentComment?) -> Unit,
    private val onDeleteClick: (MomentPost) -> Unit,
    private val onMoreClick: (View, MomentPost) -> Unit
) : BaseQuickAdapter<MomentPost, BaseViewHolder>(R.layout.item_moment_post) {

    override fun convert(holder: BaseViewHolder, item: MomentPost) {
        val avatarView = holder.getView<com.chat.base.ui.components.AvatarView>(R.id.avatarView)
        val nameTv = holder.getView<TextView>(R.id.nameTv)
        val contentTv = holder.getView<TextView>(R.id.contentTv)
        val mentionTv = holder.getView<TextView>(R.id.mentionTv)
        val locationTv = holder.getView<TextView>(R.id.locationTv)
        val timeTv = holder.getView<TextView>(R.id.timeTv)
        val deleteTv = holder.getView<TextView>(R.id.deleteTv)
        val moreTv = holder.getView<TextView>(R.id.moreTv)
        val socialLayout = holder.getView<View>(R.id.socialLayout)
        val likesLayout = holder.getView<View>(R.id.likesLayout)
        val socialDivider = holder.getView<View>(R.id.socialDivider)
        val likeIv = holder.getView<ImageView>(R.id.likeIv)
        val likesTv = holder.getView<TextView>(R.id.likesTv)
        val commentsTv = holder.getView<TextView>(R.id.commentsTv)
        val mediaRecyclerView = holder.getView<RecyclerView>(R.id.mediaRecyclerView)

        avatarView.setSize(46f)
        MomentUiUtils.showAvatar(avatarView, item.user.uid)
        nameTv.text = item.user.name.ifEmpty { item.user.uid }
        avatarView.setOnClickListener { MomentUiUtils.openUserCard(context, item.user.uid) }
        nameTv.setOnClickListener { MomentUiUtils.openUserCard(context, item.user.uid) }
        contentTv.visibility = if (item.text.isEmpty()) View.GONE else View.VISIBLE
        contentTv.text = item.text
        mentionTv.visibility = if (item.mentionUsers.isEmpty()) View.GONE else View.VISIBLE
        mentionTv.text = context.getString(R.string.moment_mention_title) + " · " + item.mentionUsers.joinToString("、") { it.name }
        locationTv.visibility = if (item.locationTitle.isEmpty()) View.GONE else View.VISIBLE
        locationTv.text = item.locationTitle
        timeTv.text = MomentUiUtils.formatPublishTime(item.createdAt)
        deleteTv.visibility = if (item.canDelete) View.VISIBLE else View.GONE
        deleteTv.setOnClickListener { onDeleteClick(item) }
        moreTv.setOnClickListener { onMoreClick(it, item) }

        if (item.medias.isEmpty()) {
            mediaRecyclerView.visibility = View.GONE
        } else {
            mediaRecyclerView.visibility = View.VISIBLE
            val spanCount = if (item.medias.size == 1) 1 else 3
            mediaRecyclerView.layoutManager = GridLayoutManager(context, spanCount)
            mediaRecyclerView.adapter = MomentPostMediaAdapter(item.medias.size) { index, media, imageView ->
                if (media.type == "video" || media.coverUrl.isNotEmpty()) {
                    MomentUiUtils.openVideo(context, media)
                } else {
                    MomentUiUtils.showImagePopup(context as android.app.Activity, imageView, item.medias.map { it.url }, index)
                }
            }.apply {
                setList(item.medias)
            }
        }

        val hasLikes = item.likes.isNotEmpty()
        val hasComments = item.comments.isNotEmpty()
        socialLayout.visibility = if (hasLikes || hasComments) View.VISIBLE else View.GONE
        likesLayout.visibility = if (hasLikes) View.VISIBLE else View.GONE
        socialDivider.visibility = if (hasLikes && hasComments) View.VISIBLE else View.GONE
        commentsTv.visibility = if (hasComments) View.VISIBLE else View.GONE
        MomentUiUtils.bindLikesText(likesTv, item.likes)
        MomentUiUtils.bindCommentsText(commentsTv, item.comments) { comment ->
            onCommentClick(item, comment)
        }
        MomentUiUtils.limitIconInside(
            likeIv,
            if (hasLikes) R.drawable.icon_moment_like_active else R.drawable.icon_moment_like_outline,
            insetDp = 2f
        )
        likeIv.clearColorFilter()
        likesLayout.setOnClickListener(null)
        commentsTv.setOnClickListener(null)
        holder.itemView.setOnLongClickListener {
            onMoreClick(moreTv, item)
            true
        }
    }
}
