package com.chat.moments

/**
 * 朋友圈模块入口
 * Created by Luckclouds.
 */

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ContactsMenu
import com.chat.base.endpoint.entity.MailListDot
import com.chat.base.endpoint.entity.UserDetailViewMenu
import com.chat.moments.entity.MomentNotice
import com.chat.moments.service.MomentModel
import com.chat.moments.store.MomentPrefs
import com.chat.moments.ui.MomentUserStateActivity
import com.chat.moments.ui.MomentNoticeActivity
import com.chat.moments.ui.MomentTimelineActivity
import com.chat.moments.util.MomentUiUtils
import com.xinbida.wukongim.WKIM

class WKMomentsApplication private constructor() {
    companion object {
        fun getInstance(): WKMomentsApplication = Holder.instance
    }

    private object Holder {
        val instance = WKMomentsApplication()
    }

    private var application: Application? = null
    private var isSyncingNotice = false
    private var rerunNoticeSync = false
    private var cmdListenerRegistered = false
    private var pendingNoticeSyncVersion = 0L
    private var forceNoticeSync = false
    private val noticeSyncCallbacks = mutableListOf<() -> Unit>()
    private val noticeStateListeners = linkedMapOf<String, () -> Unit>()
    private val noticeSyncHandler = Handler(Looper.getMainLooper())
    private val noticeSyncDebounceMs = 500L
    private val noticeSyncRunnable = Runnable {
        performMomentNoticeSync()
    }

    fun init(application: Application) {
        this.application = application
        registerContactsEntry()
        registerMailListDot()
        registerUserDetailEntries()
        registerMomentNoticeCmdListener()
    }

    private fun registerContactsEntry() {
        EndpointManager.getInstance().setMethod("moments_mail_list", EndpointCategory.mailList, 95) {
            ContactsMenu(
                "moments",
                R.drawable.icon_moment_entry,
                application?.getString(R.string.moment_title) ?: "朋友圈"
            ) {
                openTimeline(application, null, true)
            }.apply {
                badgeNum = MomentPrefs.unreadCount()
                showRedDot = badgeNum > 0
            }
        }
    }

    /**
     * Bottom tab "contacts" badge does not read the mailList items directly.
     * It aggregates MailListDot from every module, so moments must provide its
     * own numeric dot source here to align with the new-friend style badge.
     */
    private fun registerMailListDot() {
        EndpointManager.getInstance().setMethod("moments_mail_list_dot", EndpointCategory.wkGetMailListRedDot, 95) {
            val unread = MomentPrefs.unreadCount()
            MailListDot(unread, unread > 0)
        }
    }

    private fun registerUserDetailEntries() {
        EndpointManager.getInstance().setMethod("moments_user_detail", EndpointCategory.wkUserDetailView, 60) { obj ->
            if (obj !is UserDetailViewMenu || obj.context.get() == null) return@setMethod null
            createMomentUserEntry(obj)
        }
        EndpointManager.getInstance().setMethod("moments_user_state_detail", EndpointCategory.wkUserDetailView, 61) { obj ->
            if (obj !is UserDetailViewMenu || obj.context.get() == null) return@setMethod null
            createMomentStateEntry(obj)
        }
    }

    private fun createMomentUserEntry(menu: UserDetailViewMenu): LinearLayout {
        val context = menu.context.get()!!
        val container = createEntryView(context, context.getString(R.string.moment_title), true)
        container.setOnClickListener {
            openTimeline(
                context = context,
                uid = menu.uid,
                allFriends = false,
                displayName = resolveUserDetailName(context, menu.uid)
            )
        }
        bindMomentPreview(container, menu.uid)
        return container
    }

    private fun createMomentStateEntry(menu: UserDetailViewMenu): LinearLayout {
        val context = menu.context.get()!!
        val container = createEntryView(context, context.getString(R.string.moment_status_title), false)
        container.setOnClickListener {
            val intent = Intent(context, MomentUserStateActivity::class.java)
            intent.putExtra(MomentUserStateActivity.EXTRA_UID, menu.uid)
            context.startActivity(intent)
        }
        return container
    }

    private fun createEntryView(context: Context, title: String, showPreview: Boolean): LinearLayout {
        val root = LayoutInflater.from(context).inflate(R.layout.item_moment_user_detail_entry, null) as LinearLayout
        val titleView = root.findViewById<TextView>(R.id.titleTv)
        val previewLayout = root.findViewById<LinearLayout>(R.id.previewLayout)
        titleView.text = title
        previewLayout.visibility = if (showPreview) android.view.View.VISIBLE else android.view.View.GONE
        return root
    }

    private fun bindMomentPreview(container: LinearLayout, uid: String) {
        val previewLayout = container.findViewById<LinearLayout>(R.id.previewLayout)
        val imageViews = mutableListOf<ImageView>()
        for (index in 0 until previewLayout.childCount) {
            val child = previewLayout.getChildAt(index)
            if (child is ImageView) {
                imageViews += child
            }
        }
        if (imageViews.isEmpty()) {
            return
        }
        MomentModel.instance.loadTimeline(uid, 1, 4) { code, _, page ->
            if (code != com.chat.base.net.HttpResponseCode.success.toInt()) {
                return@loadTimeline
            }
            val thumbs = page.list.flatMap { post ->
                post.medias.mapNotNull { media ->
                    when {
                        media.coverUrl.isNotEmpty() -> media.coverUrl
                        media.url.isNotEmpty() -> media.url
                        else -> null
                    }
                }
            }.take(imageViews.size)
            imageViews.forEachIndexed { index, imageView ->
                if (index < thumbs.size) {
                    imageView.visibility = android.view.View.VISIBLE
                    MomentUiUtils.showImage(container.context, thumbs[index], imageView)
                } else {
                    imageView.visibility = android.view.View.GONE
                }
            }
        }
    }

    fun refreshMomentEntry() {
        // New-friend red dot uses invokes(category, ...) to notify every observer
        // under wkRefreshMailList. Moments must follow the same broadcast path so
        // contacts header and bottom tab badge refresh in real time together.
        noticeSyncHandler.post {
            EndpointManager.getInstance().invokes<Any>(EndpointCategory.wkRefreshMailList, null)
        }
    }

    /**
     * Active pages observe the unified notice cache through this listener,
     * so banner and notice detail list stay in sync with the same local data.
     */
    fun addNoticeStateListener(tag: String, listener: () -> Unit) {
        noticeStateListeners[tag] = listener
    }

    fun removeNoticeStateListener(tag: String) {
        noticeStateListeners.remove(tag)
    }

    fun cachedNotices(): List<MomentNotice> {
        return MomentPrefs.noticeList().sortedByDescending { it.version }
    }

    /**
     * Request an incremental notice sync.
     * CMDs only act as a signal. The final content always comes from sync API.
     */
    fun requestMomentNoticeSync(
        cmdVersion: Long = 0L,
        debounce: Boolean = true,
        force: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        if (onComplete != null) {
            noticeSyncCallbacks += onComplete
        }
        val localVersion = MomentPrefs.noticeVersion()
        if (!force && cmdVersion > 0L && cmdVersion <= localVersion && !isSyncingNotice) {
            flushNoticeSyncCallbacks()
            return
        }
        if (cmdVersion > pendingNoticeSyncVersion) {
            pendingNoticeSyncVersion = cmdVersion
        }
        forceNoticeSync = forceNoticeSync || force
        if (debounce) {
            noticeSyncHandler.removeCallbacks(noticeSyncRunnable)
            noticeSyncHandler.postDelayed(noticeSyncRunnable, noticeSyncDebounceMs)
        } else {
            noticeSyncHandler.removeCallbacks(noticeSyncRunnable)
            performMomentNoticeSync()
        }
    }

    /**
     * Opening the notice page should immediately clear badge and banner locally,
     * then the read API keeps server state aligned.
     */
    fun markAllMomentNoticesRead() {
        val notices = MomentPrefs.noticeList()
        val hasUnread = notices.any { !it.isRead }
        if (!hasUnread) {
            MomentPrefs.saveUnreadCount(0)
            MomentPrefs.saveLatestNoticePreview("")
            refreshMomentEntry()
            dispatchNoticeStateChanged()
            return
        }
        notices.forEach { it.isRead = true }
        MomentPrefs.saveNoticeList(notices.sortedByDescending { it.version })
        MomentPrefs.saveUnreadCount(0)
        MomentPrefs.saveLatestNoticePreview("")
        refreshMomentEntry()
        dispatchNoticeStateChanged()
        try {
            MomentModel.instance.markNoticesRead(emptyList(), true) { _, _ -> }
        } catch (_: Exception) {
        }
    }

    fun openNotice(context: Context) {
        context.startActivity(Intent(context, MomentNoticeActivity::class.java))
    }

    fun openTimeline(context: Context?, uid: String?, allFriends: Boolean = false, displayName: String = "") {
        if (context == null) return
        val intent = Intent(context, MomentTimelineActivity::class.java)
        if (!uid.isNullOrEmpty()) {
            intent.putExtra(MomentTimelineActivity.EXTRA_UID, uid)
        }
        intent.putExtra(MomentTimelineActivity.EXTRA_ALL_FRIENDS, allFriends)
        if (displayName.isNotEmpty()) {
            intent.putExtra(MomentTimelineActivity.EXTRA_DISPLAY_NAME, displayName)
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun resolveUserDetailName(context: Context, uid: String): String {
        if (context is Activity) {
            val nameTv = context.findViewById<TextView?>(com.chat.uikit.R.id.nameTv)?.text?.toString()?.trim().orEmpty()
            val nickNameTv = context.findViewById<TextView?>(com.chat.uikit.R.id.nickNameTv)?.text?.toString()?.trim().orEmpty()
            val resolved = nameTv.takeIf { it.isNotEmpty() } ?: nickNameTv.takeIf { it.isNotEmpty() }
            if (!resolved.isNullOrEmpty()) {
                return resolved
            }
        }
        val channel = com.xinbida.wukongim.WKIM.getInstance().channelManager.getChannel(uid, com.xinbida.wukongim.entity.WKChannelType.PERSONAL)
        return channel?.channelRemark?.takeIf { it.isNotEmpty() }
            ?: channel?.channelName.orEmpty()
    }

    /**
     * Realtime moment notice follows the same system CMD channel as "new friend",
     * but CMD only means "you have new notices". The actual notice list still
     * comes from /v1/moment/notices/sync with local version incremental pull.
     */
    private fun registerMomentNoticeCmdListener() {
        if (cmdListenerRegistered) return
        cmdListenerRegistered = true
        try {
            WKIM.getInstance().getCMDManager().addCmdListener("moments_notice") { cmd ->
                try {
                    if (cmd == null || cmd.cmdKey != "momentNoticeSync") {
                        return@addCmdListener
                    }
                    val version = cmd.paramJsonObject?.optLong("version") ?: 0L
                    requestMomentNoticeSync(version, debounce = true)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun performMomentNoticeSync() {
        if (isSyncingNotice) {
            rerunNoticeSync = true
            return
        }
        if ((com.chat.base.config.WKConfig.getInstance().uid ?: "").isEmpty()) {
            flushNoticeSyncCallbacks()
            return
        }
        val localVersion = MomentPrefs.noticeVersion()
        if (!forceNoticeSync && pendingNoticeSyncVersion > 0L && pendingNoticeSyncVersion <= localVersion) {
            pendingNoticeSyncVersion = 0L
            flushNoticeSyncCallbacks()
            return
        }
        isSyncingNotice = true
        val requestVersion = MomentPrefs.noticeVersion()
        val requestedSignalVersion = pendingNoticeSyncVersion
        pendingNoticeSyncVersion = 0L
        forceNoticeSync = false
        try {
            MomentModel.instance.syncNotices(requestVersion, 50) { code, _, list, version ->
                isSyncingNotice = false
                if (code == com.chat.base.net.HttpResponseCode.success.toInt()) {
                    mergeAndStoreNotices(list, version)
                }
                flushNoticeSyncCallbacks()
                if (rerunNoticeSync || requestedSignalVersion > version) {
                    rerunNoticeSync = false
                    requestMomentNoticeSync(debounce = false)
                } else {
                    rerunNoticeSync = false
                }
            }
        } catch (_: Exception) {
            isSyncingNotice = false
            flushNoticeSyncCallbacks()
        }
    }

    /**
     * Keep a single local notice cache so entry badge, banner, and notice page
     * all read from the same source of truth.
     */
    private fun mergeAndStoreNotices(incoming: List<MomentNotice>, version: Long) {
        val app = application ?: return
        val mergedMap = linkedMapOf<Long, MomentNotice>()
        MomentPrefs.noticeList().sortedByDescending { it.version }.forEach { notice ->
            mergedMap[notice.id] = notice.copy()
        }
        incoming.forEach { notice ->
            val old = mergedMap[notice.id]
            if (old == null) {
                mergedMap[notice.id] = notice.copy()
            } else {
                mergedMap[notice.id] = old.copy(
                    noticeType = notice.noticeType.ifEmpty { old.noticeType },
                    isRead = old.isRead || notice.isRead,
                    version = maxOf(old.version, notice.version),
                    createdAt = notice.createdAt.ifEmpty { old.createdAt },
                    fromUser = mergeNoticeUser(old.fromUser, notice.fromUser),
                    postId = notice.postId.ifEmpty { old.postId },
                    commentId = notice.commentId.ifEmpty { old.commentId },
                    content = notice.content.ifEmpty { old.content },
                    mediaThumb = notice.mediaThumb.ifEmpty { old.mediaThumb }
                )
            }
        }
        val mergedList = mergedMap.values
            .sortedByDescending { it.version }
            .take(200)
            .toMutableList()
        MomentPrefs.saveNoticeList(mergedList)
        MomentPrefs.saveNoticeVersion(
            maxOf(version, mergedList.maxOfOrNull { it.version } ?: MomentPrefs.noticeVersion())
        )
        val unreadList = mergedList.filter { !it.isRead }
        MomentPrefs.saveUnreadCount(unreadList.size)
        MomentPrefs.saveLatestNoticePreview(
            unreadList.firstOrNull()?.let { MomentUiUtils.noticePreview(app, it) }.orEmpty()
        )
        refreshMomentEntry()
        dispatchNoticeStateChanged()
    }

    private fun mergeNoticeUser(
        oldUser: com.chat.moments.entity.MomentUser,
        newUser: com.chat.moments.entity.MomentUser
    ): com.chat.moments.entity.MomentUser {
        return oldUser.copy(
            uid = newUser.uid.ifEmpty { oldUser.uid },
            name = newUser.name.ifEmpty { oldUser.name },
            avatar = newUser.avatar.ifEmpty { oldUser.avatar }
        )
    }

    private fun dispatchNoticeStateChanged() {
        noticeStateListeners.values.toList().forEach { listener ->
            try {
                listener()
            } catch (_: Exception) {
            }
        }
    }

    private fun flushNoticeSyncCallbacks() {
        if (noticeSyncCallbacks.isEmpty()) return
        val callbacks = noticeSyncCallbacks.toList()
        noticeSyncCallbacks.clear()
        callbacks.forEach { callback ->
            try {
                callback()
            } catch (_: Exception) {
            }
        }
    }

}
