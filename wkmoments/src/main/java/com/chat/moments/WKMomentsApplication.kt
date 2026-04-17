package com.chat.moments

/**
 * 朋友圈模块入口
 * Created by Luckclouds.
 */

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ContactsMenu
import com.chat.base.endpoint.entity.UserDetailViewMenu
import com.chat.moments.service.MomentModel
import com.chat.moments.store.MomentPrefs
import com.chat.moments.ui.MomentUserStateActivity
import com.chat.moments.ui.MomentNoticeActivity
import com.chat.moments.ui.MomentTimelineActivity
import com.chat.moments.util.MomentUiUtils

class WKMomentsApplication private constructor() {
    companion object {
        fun getInstance(): WKMomentsApplication = Holder.instance
    }

    private object Holder {
        val instance = WKMomentsApplication()
    }

    private var application: Application? = null
    private var startedActivityCount = 0
    private var isSyncingNotice = false

    fun init(application: Application) {
        this.application = application
        registerContactsEntry()
        registerUserDetailEntries()
        // Temporarily disable app-foreground sync. Keep homepage-triggered sync only
        // so the moment notice entry logic can be tested independently.
        // registerForegroundSync(application)
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
        EndpointManager.getInstance().invoke(EndpointCategory.wkRefreshMailList, null)
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

    private fun registerForegroundSync(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                val wasBackground = startedActivityCount == 0
                startedActivityCount += 1
                if (wasBackground) {
                    syncMomentNoticeBadge()
                }
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun syncMomentNoticeBadge() {
        if (isSyncingNotice) return
        if ((com.chat.base.config.WKConfig.getInstance().uid ?: "").isEmpty()) return
        val app = application ?: return
        isSyncingNotice = true
        MomentModel.instance.syncNotices(0, 50) { code, _, list, version ->
            isSyncingNotice = false
            if (code != com.chat.base.net.HttpResponseCode.success.toInt()) return@syncNotices
            val unreadList = list.filter { !it.isRead }
            MomentPrefs.saveUnreadCount(unreadList.size)
            MomentPrefs.saveNoticeVersion(version)
            MomentPrefs.saveLatestNoticePreview(
                unreadList.firstOrNull()?.let { MomentUiUtils.noticePreview(app, it) }.orEmpty()
            )
            refreshMomentEntry()
        }
    }

}
