package com.chat.moments.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chat.base.WKBaseApplication
import com.chat.base.act.PlayVideoActivity
import com.chat.base.entity.ImagePopupBottomSheetItem
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.ui.components.AvatarView
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKTimeUtils
import com.chat.moments.R
import com.chat.moments.entity.MomentComment
import com.chat.moments.entity.MomentLike
import com.chat.moments.entity.MomentMedia
import com.chat.moments.entity.MomentNotice
import com.chat.moments.entity.MomentTagChoice
import com.chat.moments.entity.MomentUserChoice
import com.chat.moments.entity.MomentVisibilityType
import com.chat.uikit.user.UserDetailActivity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object MomentUiUtils {
    private const val NAME_COLOR = "#556C94"

    fun resolveUrl(path: String?): String {
        if (path.isNullOrEmpty()) return ""
        return when {
            path.startsWith("http") -> path
            path.startsWith("content://") -> path
            path.startsWith("file://") -> path
            path.startsWith("/") -> path
            else -> WKApiConfig.getShowUrl(path)
        }
    }

    fun showImage(context: Context, path: String?, imageView: ImageView) {
        val showUrl = resolveUrl(path)
        if (showUrl.isNotEmpty()) {
            GlideUtils.getInstance().showImg(context, showUrl, imageView)
        } else {
            imageView.setImageDrawable(null)
        }
    }

    fun buildCacheBustedUrl(path: String?, cacheKey: Long): String {
        val showUrl = resolveUrl(path)
        if (showUrl.isEmpty() || cacheKey <= 0L) return showUrl
        val separator = if (showUrl.contains("?")) "&" else "?"
        return "$showUrl${separator}v=$cacheKey"
    }

    fun showAvatar(avatarView: AvatarView, uid: String?) {
        if (!uid.isNullOrEmpty()) {
            avatarView.showAvatar(uid, 1.toByte())
        }
    }

    fun showImagePopup(activity: Activity, imageView: ImageView, urls: List<String>, index: Int) {
        val valid = urls.filter { it.isNotEmpty() }.map { resolveUrl(it) }
        if (valid.isEmpty()) return
        val imageViews = ArrayList<ImageView>(valid.size)
        repeat(valid.size) {
            imageViews += imageView
        }
        val popupItems = ArrayList<Any>(valid)
        WKDialogUtils.getInstance().showImagePopup(
            activity,
            popupItems,
            imageViews,
            imageView,
            index.coerceAtMost(valid.lastIndex),
            arrayListOf<ImagePopupBottomSheetItem>(),
            null,
            null
        )
    }

    fun openVideo(context: Context, media: MomentMedia) {
        val intent = Intent(context, PlayVideoActivity::class.java)
        intent.putExtra("url", resolveUrl(media.url))
        intent.putExtra("coverImg", resolveUrl(if (media.coverUrl.isNotEmpty()) media.coverUrl else media.url))
        intent.putExtra("title", context.getString(R.string.moment_video))
        context.startActivity(intent)
    }

    fun openUserCard(context: Context, uid: String?) {
        if (uid.isNullOrEmpty()) return
        val intent = Intent(context, UserDetailActivity::class.java)
        intent.putExtra("uid", uid)
        context.startActivity(intent)
    }

    // Keep the ImageView size unchanged and only constrain the drawable inside it,
    // which helps reduce visible jagged edges on PNG icons.
    fun limitIconInside(
        imageView: ImageView,
        drawableRes: Int,
        insetDp: Float = 3f,
        paddingDp: Float = 0f
    ) {
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val inset = AndroidUtilities.dp(insetDp)
        val padding = AndroidUtilities.dp(paddingDp)
        imageView.setPadding(padding, padding, padding, padding)
        val drawable = ContextCompat.getDrawable(imageView.context, drawableRes)
        imageView.setImageDrawable(drawable?.let { InsetDrawable(it, inset) })
    }

    fun formatPublishTime(createdAt: String): String {
        if (createdAt.isEmpty()) return ""
        return try {
            val mills = parseCreatedAt(createdAt)
            if (mills <= 0L) return createdAt
            val diff = System.currentTimeMillis() - mills
            when {
                diff < 60_000L -> WKBaseApplication.getInstance().context.getString(com.chat.base.R.string.str_just)
                diff < 3_600_000L -> "${diff / 60_000L}${WKBaseApplication.getInstance().context.getString(R.string.moment_minutes_ago)}"
                diff < 86_400_000L -> "${diff / 3_600_000L}${WKBaseApplication.getInstance().context.getString(R.string.moment_hours_ago)}"
                diff < 30L * 86_400_000L -> "${diff / 86_400_000L}${WKBaseApplication.getInstance().context.getString(R.string.moment_days_ago)}"
                else -> WKTimeUtils.getInstance().getShowDate(mills)
            }
        } catch (_: Exception) {
            createdAt
        }
    }

    fun noticePreview(context: Context, notice: MomentNotice): String {
        if (notice.content.isNotEmpty()) return notice.content
        val name = notice.fromUser.name.ifEmpty { notice.fromUser.uid }
        return when (notice.noticeType.lowercase(Locale.getDefault())) {
            "like" -> context.getString(R.string.moment_notice_like, name)
            "comment" -> context.getString(R.string.moment_notice_comment, name)
            "reply" -> context.getString(R.string.moment_notice_reply, name)
            "mention", "at" -> context.getString(R.string.moment_notice_mention, name)
            else -> name
        }
    }

    fun noticeDetailContent(context: Context, notice: MomentNotice): String {
        val explicit = notice.content.trim()
        return when (notice.noticeType.lowercase(Locale.getDefault())) {
            "like" -> context.getString(R.string.moment_notice_like_simple)
            "comment" -> explicit.ifEmpty { context.getString(R.string.moment_notice_comment_simple) }
            "reply" -> explicit.ifEmpty { context.getString(R.string.moment_notice_reply_simple) }
            "mention", "at" -> explicit.ifEmpty { context.getString(R.string.moment_notice_mention_simple) }
            else -> explicit.ifEmpty { context.getString(R.string.moment_notice_comment_simple) }
        }
    }

    private fun parseCreatedAt(createdAt: String): Long {
        createdAt.toLongOrNull()?.let { value ->
            return if (createdAt.length <= 10) value * 1000 else value
        }
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd"
        )
        formats.forEach { pattern ->
            try {
                val format = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = format.parse(createdAt)
                if (parsed != null) {
                    return parsed.time
                }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun visibilityLabel(type: String): Int {
        return when (type) {
            MomentVisibilityType.PRIVATE -> R.string.moment_visibility_private
            MomentVisibilityType.PARTIAL_VISIBLE -> R.string.moment_visibility_partial
            MomentVisibilityType.EXCLUDE_VISIBLE -> R.string.moment_visibility_exclude
            else -> R.string.moment_visibility_public
        }
    }

    fun mentionsSummary(users: List<MomentUserChoice>, tags: List<MomentTagChoice>): String {
        val parts = mutableListOf<String>()
        if (users.isNotEmpty()) {
            parts += "${users.size} user"
        }
        if (tags.isNotEmpty()) {
            parts += "${tags.size} tag"
        }
        return parts.joinToString(" · ")
    }

    fun likesText(likes: List<MomentLike>): String {
        return likes.joinToString("、") { it.name.ifEmpty { it.uid } }
    }

    fun commentsText(comments: List<MomentComment>): String {
        return comments.joinToString("\n") {
            val name = it.user.name.ifEmpty { it.user.uid }
            if (it.replyUser == null || TextUtils.isEmpty(it.replyUser?.uid)) {
                String.format(Locale.getDefault(), "%s : %s", name, it.content)
            } else {
                val replyName = it.replyUser?.name?.ifEmpty { it.replyUser?.uid } ?: ""
                String.format(
                    Locale.getDefault(),
                    "%s %s %s : %s",
                    name,
                    WKBaseApplication.getInstance().context.getString(R.string.moment_reply),
                    replyName,
                    it.content
                )
            }
        }
    }

    fun bindLikesText(textView: TextView, likes: List<MomentLike>) {
        textView.highlightColor = Color.TRANSPARENT
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.text = buildLikesSpannable(likes) { uid ->
            openUserCard(textView.context, uid)
        }
    }

    fun bindCommentsText(
        textView: TextView,
        comments: List<MomentComment>,
        onCommentClick: ((MomentComment) -> Unit)? = null
    ) {
        textView.highlightColor = Color.TRANSPARENT
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.setLineSpacing(3f, 1f)
        textView.text = buildCommentsSpannable(
            context = textView.context,
            comments = comments,
            onUserClick = { uid -> openUserCard(textView.context, uid) },
            onCommentClick = onCommentClick
        )
    }

    private fun buildLikesSpannable(
        likes: List<MomentLike>,
        onUserClick: (String) -> Unit
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        likes.forEachIndexed { index, like ->
            if (index > 0) builder.append("、")
            appendUserName(builder, like.name.ifEmpty { like.uid }, like.uid, onUserClick)
        }
        return builder
    }

    private fun buildCommentsSpannable(
        context: Context,
        comments: List<MomentComment>,
        onUserClick: (String) -> Unit,
        onCommentClick: ((MomentComment) -> Unit)? = null
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        comments.forEachIndexed { index, comment ->
            if (index > 0) builder.append("\n")
            appendUserName(builder, comment.user.name.ifEmpty { comment.user.uid }, comment.user.uid, onUserClick)
            if (comment.replyUser == null || TextUtils.isEmpty(comment.replyUser?.uid)) {
                builder.append(" : ")
            } else {
                builder.append(" ")
                builder.append(context.getString(R.string.moment_reply))
                builder.append(" ")
                appendUserName(
                    builder,
                    comment.replyUser?.name?.ifEmpty { comment.replyUser?.uid } ?: "",
                    comment.replyUser?.uid.orEmpty(),
                    onUserClick
                )
                builder.append(" : ")
            }
            val replyStart = builder.length
            builder.append(comment.content)
            appendReplySpan(builder, replyStart, builder.length, comment, onCommentClick)
        }
        return builder
    }

    private fun appendReplySpan(
        builder: SpannableStringBuilder,
        start: Int,
        end: Int,
        comment: MomentComment,
        onCommentClick: ((MomentComment) -> Unit)?
    ) {
        if (onCommentClick == null || end <= start) return
        builder.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                onCommentClick(comment)
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.parseColor("#2F2F2F")
                ds.isUnderlineText = false
            }
        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendUserName(
        builder: SpannableStringBuilder,
        name: String,
        uid: String,
        onUserClick: (String) -> Unit
    ) {
        val start = builder.length
        builder.append(name)
        val end = builder.length
        val color = Color.parseColor(NAME_COLOR)
        builder.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                onUserClick(uid)
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = color
                ds.isUnderlineText = false
            }
        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun setVisible(view: View, isVisible: Boolean) {
        view.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
}
