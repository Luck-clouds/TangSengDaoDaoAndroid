package com.chat.moments.ui.adapter

/**
 * 朋友圈动态适配器
 * Created by Luckclouds.
 */

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.entity.PopupMenuItem
import com.chat.base.utils.WKDialogUtils
import com.chat.moments.R
import com.chat.moments.entity.MomentMedia
import com.chat.moments.entity.MomentPost
import com.chat.moments.util.MomentUiUtils
import com.chat.uikit.R as UIKitR

class MomentPostAdapter(
    private val onLikeClick: (MomentPost) -> Unit,
    private val onCommentClick: (MomentPost, com.chat.moments.entity.MomentComment?) -> Unit,
    private val onDeleteClick: (MomentPost) -> Unit,
    private val onMoreClick: (View, MomentPost) -> Unit,
    private val onFavoriteTextClick: (MomentPost) -> Unit,
    private val onFavoriteImageClick: (MomentPost, MomentMedia) -> Unit
) : BaseQuickAdapter<MomentPost, BaseViewHolder>(R.layout.item_moment_post) {

    companion object {
        private const val CONTENT_COLLAPSE_COUNT = 100
    }

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
        bindContentText(contentTv, item)
        bindContentLongClick(contentTv, item)
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
                    MomentUiUtils.showMomentImagePopup(
                        context as android.app.Activity,
                        imageView,
                        item.medias,
                        index
                    ) { favoriteMedia ->
                        onFavoriteImageClick(item, favoriteMedia)
                    }
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
        if (hasLikes) {
            likeIv.clearColorFilter()
        } else {
            likeIv.setColorFilter(
                ContextCompat.getColor(context, R.color.moment_icon_dark),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        likesLayout.setOnClickListener(null)
        commentsTv.setOnClickListener(null)
        holder.itemView.setOnLongClickListener {
            onMoreClick(moreTv, item)
            true
        }
    }

    private fun bindContentText(contentTv: TextView, item: MomentPost) {
        val fullText = item.text
        val actionColor = ContextCompat.getColor(contentTv.context, R.color.moment_link_text)
        if (fullText.length <= CONTENT_COLLAPSE_COUNT) {
            contentTv.movementMethod = null
            contentTv.highlightColor = android.graphics.Color.TRANSPARENT
            contentTv.text = fullText
            contentTv.setOnClickListener(null)
            return
        }
        val actionText = if (item.isContentExpanded) "收起" else "展开"
        val displayText = if (item.isContentExpanded) {
            "$fullText $actionText"
        } else {
            "${fullText.take(CONTENT_COLLAPSE_COUNT)}...$actionText"
        }
        val start = displayText.length - actionText.length
        val span = SpannableStringBuilder(displayText).apply {
            setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    item.isContentExpanded = !item.isContentExpanded
                    ((contentTv.parent) as? ViewGroup)?.let { parent ->
                        TransitionManager.beginDelayedTransition(parent, AutoTransition().apply {
                            duration = 180L
                        })
                    }
                    bindContentText(contentTv, item)
                }

                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.isUnderlineText = false
                    ds.color = actionColor
                }
            }, start, displayText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(actionColor),
                start,
                displayText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(StyleSpan(Typeface.BOLD), start, displayText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        contentTv.highlightColor = android.graphics.Color.TRANSPARENT
        contentTv.movementMethod = LinkMovementMethod.getInstance()
        contentTv.setText(span, TextView.BufferType.SPANNABLE)
    }

    private fun bindContentLongClick(contentTv: TextView, item: MomentPost) {
        if (item.text.isEmpty()) {
            contentTv.setOnLongClickListener(null)
            return
        }
        val list = arrayListOf(
            PopupMenuItem(
                contentTv.context.getString(com.chat.base.R.string.copy),
                UIKitR.mipmap.msg_copy
            ) {
                MomentUiUtils.copyText(contentTv.context, item.text)
            },
            PopupMenuItem(
                contentTv.context.getString(com.chat.base.R.string.favorite),
                UIKitR.mipmap.msg_fave
            ) {
                onFavoriteTextClick(item)
            }
        )
        WKDialogUtils.getInstance().setViewLongClickPopup(contentTv, list)
    }

}
