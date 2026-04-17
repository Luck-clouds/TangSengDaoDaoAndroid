package com.chat.moments.entity

import android.content.Context
import android.os.Parcelable
import com.chat.base.WKBaseApplication
import com.chat.moments.R
import kotlinx.parcelize.Parcelize

object MomentVisibilityType {
    const val PUBLIC = "public"
    const val PRIVATE = "private"
    const val PARTIAL_VISIBLE = "partial_visible"
    const val EXCLUDE_VISIBLE = "exclude_visible"
}

@Parcelize
data class MomentUserChoice(
    val uid: String,
    val name: String,
    val avatar: String? = null
) : Parcelable

@Parcelize
data class MomentTagChoice(
    val id: String,
    val name: String,
    val memberNames: ArrayList<String> = arrayListOf()
) : Parcelable

@Parcelize
data class MomentAudienceSelection(
    var type: String = MomentVisibilityType.PUBLIC,
    val users: ArrayList<MomentUserChoice> = arrayListOf(),
    val tags: ArrayList<MomentTagChoice> = arrayListOf()
) : Parcelable {
    fun summary(): String {
        return when (type) {
            MomentVisibilityType.PRIVATE -> "private"
            MomentVisibilityType.PARTIAL_VISIBLE, MomentVisibilityType.EXCLUDE_VISIBLE -> buildSelectionSummary()
            else -> "public"
        }
    }

    fun buildSelectionSummary(): String {
        val parts = mutableListOf<String>()
        if (users.isNotEmpty()) {
            parts += "${users.size} users"
        }
        if (tags.isNotEmpty()) {
            parts += "${tags.size} tags"
        }
        return parts.joinToString(" · ")
    }

    fun buildSelectionSummaryText(context: Context = WKBaseApplication.getInstance().context): String {
        val parts = mutableListOf<String>()
        if (users.isNotEmpty()) {
            parts += context.getString(R.string.moment_selected_users, users.size)
        }
        if (tags.isNotEmpty()) {
            parts += context.getString(R.string.moment_selected_tags, tags.size)
        }
        return parts.joinToString(" ")
    }
}

@Parcelize
data class MomentComposeMedia(
    val type: String,
    val localPath: String,
    val coverPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val size: Long = 0L
) : Parcelable {
    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
    }
}

data class MomentFeedPage(
    var uid: String = "",
    var cover: String = "",
    var coverVersion: Long = 0L,
    val list: MutableList<MomentPost> = mutableListOf()
)

data class MomentPost(
    var postId: String = "",
    var text: String = "",
    var createdAt: String = "",
    var canDelete: Boolean = false,
    var likedByMe: Boolean = false,
    var user: MomentUser = MomentUser(),
    var locationTitle: String = "",
    var mentionUsers: MutableList<MomentUserChoice> = mutableListOf(),
    var medias: MutableList<MomentMedia> = mutableListOf(),
    var likes: MutableList<MomentLike> = mutableListOf(),
    var comments: MutableList<MomentComment> = mutableListOf()
)

data class MomentUser(
    var uid: String = "",
    var name: String = "",
    var avatar: String = ""
)

data class MomentMedia(
    var type: String = "",
    var url: String = "",
    var coverUrl: String = "",
    var width: Int = 0,
    var height: Int = 0,
    var duration: Long = 0L,
    var size: Long = 0L,
    var sortIndex: Int = 0
)

data class MomentLike(
    var uid: String = "",
    var name: String = ""
)

data class MomentComment(
    var commentId: String = "",
    var content: String = "",
    var createdAt: String = "",
    var user: MomentUser = MomentUser(),
    var replyUser: MomentUser? = null
)

data class MomentNotice(
    var id: Long = 0L,
    var noticeType: String = "",
    var isRead: Boolean = true,
    var version: Long = 0L,
    var createdAt: String = "",
    var fromUser: MomentUser = MomentUser(),
    var postId: String = "",
    var commentId: String = "",
    var content: String = "",
    var mediaThumb: String = ""
)

data class MomentProfile(
    var uid: String = "",
    var cover: String = "",
    var version: Long = 0L
)

data class MomentUserStateSetting(
    var toUid: String = "",
    var hideMyMoment: Boolean = false,
    var hideHisMoment: Boolean = false
)
