package com.chat.moments.ui

import android.Manifest
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKConfig
import com.chat.base.entity.PopupMenuItem
import com.chat.base.glide.ChooseMimeType
import com.chat.base.glide.ChooseResult
import com.chat.base.glide.GlideUtils
import com.chat.base.glide.ChooseResultModel
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKToastUtils
import com.chat.base.utils.singleclick.SingleClickUtil
import com.chat.moments.R
import com.chat.moments.WKMomentsApplication
import com.chat.moments.databinding.ActMomentTimelineLayoutBinding
import com.chat.moments.entity.MomentComment
import com.chat.moments.entity.MomentPost
import com.chat.moments.service.MomentModel
import com.chat.moments.store.MomentPrefs
import com.chat.moments.ui.adapter.MomentPostAdapter
import com.chat.moments.util.MomentPullRefreshHelper
import com.chat.moments.util.MomentUiUtils
import com.scwang.smart.refresh.layout.api.RefreshHeader
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.RefreshState
import com.scwang.smart.refresh.layout.simple.SimpleMultiListener
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannelType

class MomentTimelineActivity : WKBaseActivity<ActMomentTimelineLayoutBinding>() {

    companion object {
        const val EXTRA_UID = "moment_uid"
        const val EXTRA_ALL_FRIENDS = "moment_all_friends"
        const val EXTRA_DISPLAY_NAME = "moment_display_name"
    }

    private val adapter by lazy {
        MomentPostAdapter(
            onLikeClick = ::toggleLike,
            onCommentClick = ::showCommentInput,
            onDeleteClick = ::deletePost,
            onMoreClick = ::showActionPopup
        )
    }
    private var pageIndex = 1
    private val pageSize = 20
    private var currentUid: String? = null
    private var headerUid: String? = null
    private var headerDisplayName: String = ""
    private var isAllFriendsTimeline = false
    private var isNoMoreData = false
    private var isSelfTimeline = true
    private lateinit var headerView: View
    private lateinit var coverIv: ImageView
    private lateinit var headerCameraIv: ImageView
    private lateinit var headerNameTv: TextView
    private lateinit var headerAvatarView: com.chat.base.ui.components.AvatarView
    private lateinit var titleWrap: View
    private lateinit var backIv: ImageView
    private lateinit var titleTv: TextView
    private lateinit var pullRefreshBgLayout: View
    private lateinit var pullRefreshLoadingIv: ImageView
    private lateinit var pullRefreshHelper: MomentPullRefreshHelper
    private var currentHeaderOffset = 0

    private val composeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            refreshTimeline()
        }
    }

    override fun getViewBinding(): ActMomentTimelineLayoutBinding {
        return ActMomentTimelineLayoutBinding.inflate(layoutInflater)
    }

    override fun getTitleBg(titleView: View): Int {
        return com.chat.base.R.color.transparent
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.text = getString(R.string.moment_title)
    }

    override fun initPresenter() {
        val selfUid = WKConfig.getInstance().uid
        val targetUid = intent.getStringExtra(EXTRA_UID)
        headerDisplayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        isAllFriendsTimeline = intent.getBooleanExtra(EXTRA_ALL_FRIENDS, false)
        when {
            !targetUid.isNullOrEmpty() -> {
                currentUid = targetUid
                headerUid = targetUid
                isSelfTimeline = targetUid == selfUid
            }

            isAllFriendsTimeline -> {
                currentUid = null
                headerUid = selfUid
                isSelfTimeline = true
            }

            else -> {
                currentUid = selfUid
                headerUid = selfUid
                isSelfTimeline = true
            }
        }
    }

    override fun initView() {
        titleWrap = findViewById(R.id.titleBarLayout)
        backIv = findViewById(R.id.backIv)
        titleTv = findViewById(R.id.titleCenterTv)
        pullRefreshBgLayout = findViewById(R.id.pullRefreshBgLayout)
        pullRefreshLoadingIv = findViewById(R.id.pullRefreshLoadingIv)
        pullRefreshHelper = MomentPullRefreshHelper(this, pullRefreshBgLayout, pullRefreshLoadingIv)
        findViewById<View>(R.id.statusBarView).background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        titleWrap.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        titleWrap.alpha = 1f
        titleTv.alpha = 0f
        wkVBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        headerView = LayoutInflater.from(this).inflate(R.layout.view_moment_header, wkVBinding.recyclerView, false)
        coverIv = headerView.findViewById(R.id.coverIv)
        headerCameraIv = headerView.findViewById(R.id.headerCameraIv)
        headerNameTv = headerView.findViewById(R.id.nameTv)
        headerAvatarView = headerView.findViewById(R.id.avatarView)
        headerAvatarView.setSize(84f, 16f)
        headerNameTv.text = if (isSelfTimeline) getString(R.string.moment_title) else headerDisplayName
        adapter.addHeaderView(headerView)
        wkVBinding.recyclerView.adapter = adapter
        wkVBinding.refreshLayout.setEnableRefresh(true)
        wkVBinding.refreshLayout.setEnableLoadMore(true)
        wkVBinding.refreshLayout.setEnableHeaderTranslationContent(false)
        wkVBinding.refreshLayout.setHeaderHeightPx(pullRefreshHelper.getFixedAndRefreshOffsetPx())
        wkVBinding.refreshLayout.setHeaderMaxDragRate(pullRefreshHelper.getHeaderMaxDragRate())
        wkVBinding.refreshLayout.setHeaderTriggerRate(1f)
        wkVBinding.refreshLayout.setDragRate(1f)
        wkVBinding.refreshLayout.setReboundDuration(520)
        headerCameraIv.visibility = if (isSelfTimeline) View.VISIBLE else View.GONE
        updateTitleBar(0f)
    }

    override fun initListener() {
        wkVBinding.refreshLayout.setOnRefreshListener {
            refreshTimeline()
        }
        wkVBinding.refreshLayout.setOnLoadMoreListener {
            if (isNoMoreData) {
                wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData()
            } else {
                loadTimeline(false)
            }
        }
        wkVBinding.refreshLayout.setOnMultiListener(object : SimpleMultiListener() {
            override fun onHeaderMoving(
                header: RefreshHeader?,
                isDragging: Boolean,
                percent: Float,
                offset: Int,
                headerHeight: Int,
                maxDragHeight: Int
            ) {
                currentHeaderOffset = offset
                wkVBinding.recyclerView.translationY = pullRefreshHelper.resolveContentTranslationY(offset)
                pullRefreshHelper.onPull(offset, isDragging)
            }

            override fun onStateChanged(
                refreshLayout: RefreshLayout,
                oldState: RefreshState,
                newState: RefreshState
            ) {
                when (newState) {
                    RefreshState.Refreshing -> pullRefreshHelper.startReleaseSpinIfNeeded(currentHeaderOffset)
                    RefreshState.PullDownCanceled,
                    RefreshState.None -> {
                        pullRefreshHelper.cancelPull()
                        if (newState == RefreshState.None) {
                            wkVBinding.recyclerView.translationY = 0f
                        }
                    }
                    RefreshState.RefreshFinish -> pullRefreshHelper.finishRefreshing()
                    else -> Unit
                }
            }
        })
        wkVBinding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val progress = ((-headerView.top).toFloat() / headerView.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                updateTitleBar(progress)
            }
        })
        SingleClickUtil.onSingleClick(headerCameraIv) {
            showTimelineMenu(headerCameraIv)
        }
    }

    override fun initData() {
        refreshTimeline()
        if (isSelfTimeline) {
            syncNoticeBadge()
        }
    }

    private fun refreshTimeline() {
        pageIndex = 1
        isNoMoreData = false
        wkVBinding.refreshLayout.resetNoMoreData()
        loadTimeline(true)
    }

    private fun loadTimeline(isRefresh: Boolean) {
        MomentModel.instance.loadTimeline(currentUid, pageIndex, pageSize) { code, msg, page ->
            if (isRefresh) {
                pullRefreshHelper.markRefreshDataReady {
                    wkVBinding.refreshLayout.finishRefresh(0)
                }
            } else {
                wkVBinding.refreshLayout.finishLoadMore()
            }
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg)
                return@loadTimeline
            }
            headerAvatarView.showAvatar(headerUid, WKChannelType.PERSONAL)
            val fallbackName = page.list.firstOrNull()?.user?.name?.takeIf { it.isNotEmpty() }
                ?: page.uid.takeIf { it.isNotEmpty() }
                ?: headerUid.orEmpty()
            val channel = WKIM.getInstance().channelManager.getChannel(headerUid, WKChannelType.PERSONAL)
            val remarkName = channel?.channelRemark?.takeIf { it.isNotEmpty() }
            val channelName = channel?.channelName?.takeIf { it.isNotEmpty() }
            val displayName = headerDisplayName.takeIf { it.isNotEmpty() }
            headerNameTv.text = if (isSelfTimeline) {
                channelName
                    ?: fallbackName.ifEmpty { getString(R.string.moment_title) }
            } else {
                remarkName
                    ?: channelName
                    ?: displayName
                    ?: fallbackName.ifEmpty { getString(R.string.moment_title) }
            }
            MomentUiUtils.showImage(this, page.cover, coverIv)
            if (isRefresh) {
                adapter.setList(page.list)
            } else {
                adapter.addData(page.list)
            }
            isNoMoreData = page.list.size < pageSize
            if (isNoMoreData) {
                wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData()
            } else {
                pageIndex += 1
            }
        }
    }

    private fun syncNoticeBadge() {
        MomentModel.instance.syncNotices(0, 50) { code, _, list, version ->
            if (code != HttpResponseCode.success.toInt()) return@syncNotices
            val unread = list.count { !it.isRead }
            MomentPrefs.saveUnreadCount(unread)
            MomentPrefs.saveNoticeVersion(version)
            WKMomentsApplication.getInstance().refreshMomentEntry()
        }
    }

    private fun updateTitleBar(progress: Float) {
        titleWrap.alpha = 1f
        titleTv.alpha = progress
        val iconColor = android.R.color.white
        backIv.setColorFilter(ContextCompat.getColor(this, iconColor), PorterDuff.Mode.SRC_IN)
        titleTv.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun showTimelineMenu(anchor: View) {
        val list = arrayListOf(
            PopupMenuItem(getString(R.string.moment_take_media), R.drawable.icon_moment_camera_white) {
                val intent = Intent(this, MomentComposeActivity::class.java)
                intent.putExtra(MomentComposeActivity.EXTRA_AUTO_ACTION, MomentComposeActivity.ACTION_CAPTURE)
                composeLauncher.launch(intent)
            },
            PopupMenuItem(getString(R.string.moment_choose_album), R.drawable.icon_moment_add) {
                val intent = Intent(this, MomentComposeActivity::class.java)
                intent.putExtra(MomentComposeActivity.EXTRA_AUTO_ACTION, MomentComposeActivity.ACTION_ALBUM)
                composeLauncher.launch(intent)
            },
            PopupMenuItem(getString(R.string.moment_text_post), R.drawable.icon_moment_mention) {
                composeLauncher.launch(Intent(this, MomentComposeActivity::class.java))
            },
            PopupMenuItem(getString(R.string.moment_action_cover), R.drawable.icon_moment_camera_white) {
                chooseCover()
            },
            PopupMenuItem(getString(R.string.moment_action_notice), R.drawable.icon_moment_notice) {
                startActivity(Intent(this, MomentNoticeActivity::class.java))
            }
        )
        WKDialogUtils.getInstance().showScreenPopup(anchor, list)
    }

    private fun chooseCover() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val desc = getString(com.chat.base.R.string.album_permissions_desc, getString(com.chat.base.R.string.app_name))
        WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
            override fun onResult(result: Boolean) {
                if (!result) return
                GlideUtils.getInstance().chooseIMG(this@MomentTimelineActivity, 1, false, ChooseMimeType.img, false, object : GlideUtils.ISelectBack {
                    override fun onBack(paths: List<ChooseResult>) {
                        val first = paths.firstOrNull() ?: return
                        if (first.model == ChooseResultModel.video) {
                            WKToastUtils.getInstance().showToastFail(getString(R.string.moment_only_one_video))
                            return
                        }
                        MomentModel.instance.updateCover(first.path) { code, msg, remotePath ->
                            if (code == HttpResponseCode.success.toInt()) {
                                MomentUiUtils.showImage(this@MomentTimelineActivity, remotePath, coverIv)
                                WKToastUtils.getInstance().showToastNormal(getString(R.string.moment_cover_update_success))
                            } else {
                                showToast(msg)
                            }
                        }
                    }

                    override fun onCancel() {
                    }
                })
            }

            override fun clickResult(isCancel: Boolean) {
            }
        }, this, desc, *permissions)
    }

    private fun showActionPopup(anchor: View, post: MomentPost) {
        val list = arrayListOf(
            PopupMenuItem(
                getString(if (post.likedByMe) R.string.moment_cancel_action else R.string.moment_like_action),
                if (post.likedByMe) R.drawable.icon_moment_like_active else R.drawable.icon_moment_like_outline
            ) {
                toggleLike(post)
            },
            PopupMenuItem(getString(R.string.moment_comment_action), R.drawable.icon_moment_comment) {
                showCommentInput(post, null)
            }
        )
        WKDialogUtils.getInstance().showScreenPopup(anchor, list)
    }

    private fun toggleLike(post: MomentPost) {
        MomentModel.instance.toggleLike(post.postId) { code, msg, liked ->
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg)
                return@toggleLike
            }
            post.likedByMe = liked
            val selfUid = WKConfig.getInstance().uid ?: ""
            val selfName = WKIM.getInstance().channelManager.getChannel(selfUid, WKChannelType.PERSONAL)?.channelName ?: selfUid
            if (liked) {
                if (post.likes.none { it.uid == selfUid }) {
                    post.likes.add(com.chat.moments.entity.MomentLike(selfUid, selfName))
                }
            } else {
                post.likes.removeAll { it.uid == selfUid }
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun showCommentInput(post: MomentPost, replyComment: MomentComment? = null) {
        val selfUid = WKConfig.getInstance().uid ?: ""
        if (replyComment != null && replyComment.user.uid == selfUid) {
            showDeleteCommentDialog(post, replyComment)
            return
        }
        val hint = if (replyComment == null) {
            getString(R.string.moment_comment_hint)
        } else {
            val replyName = replyComment.user.name.ifEmpty { replyComment.user.uid }
            "${getString(R.string.moment_reply)} $replyName"
        }
        WKDialogUtils.getInstance().showInputDialog(
            this,
            getString(if (replyComment == null) R.string.moment_comment_title else R.string.moment_reply),
            hint,
            "",
            hint,
            100
        ) { text ->
            val content = text.trim()
            if (content.isEmpty()) {
                showToast(R.string.moment_comment_empty)
                return@showInputDialog
            }
            MomentModel.instance.addComment(post.postId, content, replyComment?.commentId) { code, msg, commentId ->
                if (code != HttpResponseCode.success.toInt()) {
                    showToast(msg)
                    return@addComment
                }
                val selfName = WKIM.getInstance().channelManager.getChannel(selfUid, WKChannelType.PERSONAL)?.channelName ?: selfUid
                post.comments.add(
                    com.chat.moments.entity.MomentComment(
                        commentId = commentId,
                        content = content,
                        createdAt = "",
                        user = com.chat.moments.entity.MomentUser(selfUid, selfName, ""),
                        replyUser = replyComment?.user
                    )
                )
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showDeleteCommentDialog(post: MomentPost, comment: MomentComment) {
        WKDialogUtils.getInstance().showDialog(
            this,
            "",
            getString(R.string.moment_delete_comment_confirm),
            true,
            "",
            "",
            0,
            0
        ) { index ->
            if (index != 1) return@showDialog
            MomentModel.instance.deleteComment(post.postId, comment.commentId) { code, msg ->
                if (code != HttpResponseCode.success.toInt()) {
                    showToast(msg)
                    return@deleteComment
                }
                post.comments.removeAll { it.commentId == comment.commentId }
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun deletePost(post: MomentPost) {
        WKDialogUtils.getInstance().showDialog(
            this,
            "",
            getString(R.string.moment_delete_confirm),
            true,
            "",
            "",
            0,
            0
        ) { index ->
            if (index != 1) return@showDialog
            MomentModel.instance.deletePost(post.postId) { code, msg ->
                if (code != HttpResponseCode.success.toInt()) {
                    showToast(msg)
                    return@deletePost
                }
                val index = adapter.data.indexOfFirst { it.postId == post.postId }
                if (index >= 0) {
                    adapter.removeAt(index)
                }
            }
        }
    }
}
