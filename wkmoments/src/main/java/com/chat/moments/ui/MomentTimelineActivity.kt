package com.chat.moments.ui

/**
 * 朋友圈主页
 * Created by Luckclouds.
 */

import android.Manifest
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKConfig
import com.chat.base.entity.BottomSheetItem
import com.chat.base.entity.PopupMenuItem
import com.chat.base.ui.components.BottomSheet
import com.chat.base.utils.AndroidUtilities
import com.chat.base.glide.ChooseMimeType
import com.chat.base.glide.ChooseResult
import com.chat.base.glide.GlideUtils
import com.chat.base.glide.ChooseResultModel
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKMediaFileUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKToastUtils
import com.chat.base.utils.singleclick.SingleClickUtil
import com.chat.moments.R
import com.chat.moments.WKMomentsApplication
import com.chat.moments.databinding.ActMomentTimelineLayoutBinding
import com.chat.moments.entity.MomentComment
import com.chat.moments.entity.MomentMedia
import com.chat.moments.entity.MomentComposeMedia
import com.chat.moments.entity.MomentPost
import com.chat.moments.service.MomentModel
import com.chat.moments.store.MomentPrefs
import com.chat.moments.ui.adapter.MomentPostAdapter
import com.chat.moments.util.MomentPullRefreshHelper
import com.chat.moments.util.MomentUiUtils
import com.chat.video.contract.VideoCaptureResult
import com.chat.video.contract.WKVideoCapture
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
            onMoreClick = ::showActionPopup,
            onFavoriteTextClick = ::toggleFavoriteText,
            onFavoriteImageClick = ::toggleFavoriteImage
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
    private lateinit var coverScrimView: View
    private lateinit var headerCameraIv: ImageView
    private lateinit var headerNameTv: TextView
    private lateinit var headerAvatarView: com.chat.base.ui.components.AvatarView
    private lateinit var noticeBannerLayout: View
    private lateinit var noticeBannerAvatarView: com.chat.base.ui.components.AvatarView
    private lateinit var noticeBannerTv: TextView
    private lateinit var titleWrap: View
    private lateinit var backIv: ImageView
    private lateinit var titleTv: TextView
    private lateinit var pullRefreshBgLayout: View
    private lateinit var pullRefreshLoadingIv: ImageView
    private lateinit var pullRefreshHelper: MomentPullRefreshHelper
    private var currentHeaderOffset = 0
    private var currentHeaderCoverPath: String = ""
    private var currentHeaderCoverVersion: Long = 0L
    private var latestNoticeUid: String = ""
    private val noticePollHandler = Handler(Looper.getMainLooper())
    private val noticePollIntervalMs = 10_000L
    private val noticePollRunnable = object : Runnable {
        override fun run() {
            if (!isSelfTimeline) return
            syncNoticeBadge()
            noticePollHandler.postDelayed(this, noticePollIntervalMs)
        }
    }

    private val composeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            refreshTimeline()
        }
    }
    private val mediaCaptureLauncher = registerForActivityResult(WKVideoCapture.contract()) { result ->
        result ?: return@registerForActivityResult
        openComposeWithMedias(arrayListOf(buildCapturedMedia(result)))
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
        coverScrimView = headerView.findViewById(R.id.coverScrimView)
        headerCameraIv = headerView.findViewById(R.id.headerCameraIv)
        headerNameTv = headerView.findViewById(R.id.nameTv)
        headerAvatarView = headerView.findViewById(R.id.avatarView)
        noticeBannerLayout = headerView.findViewById(R.id.noticeBannerLayout)
        noticeBannerAvatarView = headerView.findViewById(R.id.noticeBannerAvatarView)
        noticeBannerTv = headerView.findViewById(R.id.noticeBannerTv)
        headerAvatarView.setSize(84f, 16f)
        noticeBannerAvatarView.setSize(26f)
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
        MomentUiUtils.limitIconInside(headerCameraIv, R.drawable.icon_moment_camera_white, insetDp = 2.5f)
        noticeBannerLayout.visibility = View.GONE
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
            showComposeBottomSheet()
        }
        if (isAllFriendsTimeline) {
            SingleClickUtil.onSingleClick(headerAvatarView) {
                val selfUid = WKConfig.getInstance().uid
                if (!selfUid.isNullOrEmpty()) {
                    WKMomentsApplication.getInstance().openTimeline(this, selfUid, false)
                }
            }
        }
        headerCameraIv.setOnLongClickListener {
            val intent = Intent(this, MomentComposeActivity::class.java)
            intent.putExtra(MomentComposeActivity.EXTRA_TEXT_ONLY, true)
            composeLauncher.launch(intent)
            true
        }
        if (isSelfTimeline) {
            SingleClickUtil.onSingleClick(coverIv) {
                showCoverBottomSheet()
            }
            SingleClickUtil.onSingleClick(coverScrimView) {
                showCoverBottomSheet()
            }
            SingleClickUtil.onSingleClick(noticeBannerLayout) {
                WKMomentsApplication.getInstance().openNotice(this)
            }
        }
    }

    override fun initData() {
        refreshTimeline()
        if (isSelfTimeline) {
            syncNoticeBadge()
            renderNoticeBanner()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isSelfTimeline) {
            renderNoticeBanner()
            // Temporarily disable polling. Keep only the one-shot sync when entering
            // the timeline page so the banner entry logic can be verified first.
            // startNoticePolling()
        }
    }

    override fun onPause() {
        super.onPause()
        // stopNoticePolling()
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
            try {
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
                // 主页封面现在统一只认 profile 接口，不再使用时间轴返回的 cover。
                refreshHeaderCoverFromProfile()
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
            } catch (_: Exception) {
            }
        }
    }

    private fun syncNoticeBadge() {
        MomentModel.instance.syncNotices(0, 50) { code, _, list, version ->
            if (code != HttpResponseCode.success.toInt()) return@syncNotices
            val unreadList = list.filter { !it.isRead }
            val unread = unreadList.size
            MomentPrefs.saveUnreadCount(unread)
            MomentPrefs.saveNoticeVersion(version)
            MomentPrefs.saveLatestNoticePreview(
                unreadList.firstOrNull()?.let { MomentUiUtils.noticePreview(this, it) }.orEmpty()
            )
            latestNoticeUid = unreadList.firstOrNull()?.fromUser?.uid.orEmpty()
            WKMomentsApplication.getInstance().refreshMomentEntry()
            renderNoticeBanner()
        }
    }

    private fun renderNoticeBanner() {
        if (!isSelfTimeline) {
            noticeBannerLayout.visibility = View.GONE
            return
        }
        val unreadCount = MomentPrefs.unreadCount()
        if (unreadCount <= 0) {
            noticeBannerLayout.visibility = View.GONE
            return
        }
        if (latestNoticeUid.isNotEmpty()) {
            noticeBannerAvatarView.showAvatar(latestNoticeUid, WKChannelType.PERSONAL)
        }
        noticeBannerTv.text = getString(R.string.moment_notice_banner_count, unreadCount)
        noticeBannerLayout.visibility = View.VISIBLE
    }

    private fun startNoticePolling() {
        if (!isSelfTimeline) return
        noticePollHandler.removeCallbacks(noticePollRunnable)
        noticePollHandler.postDelayed(noticePollRunnable, noticePollIntervalMs)
    }

    private fun stopNoticePolling() {
        noticePollHandler.removeCallbacks(noticePollRunnable)
    }

    private fun updateTitleBar(progress: Float) {
        titleWrap.alpha = 1f
        titleTv.alpha = progress
        val iconColor = android.R.color.white
        backIv.setColorFilter(ContextCompat.getColor(this, iconColor), PorterDuff.Mode.SRC_IN)
        titleTv.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun showComposeBottomSheet() {
        val items = arrayOf(
            getString(R.string.moment_take_media),
            getString(R.string.moment_choose_album)
        )
        val icons = intArrayOf(
            R.drawable.icon_moment_menu_capture,
            R.drawable.icon_moment_menu_album
        )
        showMomentBottomSheet(getString(R.string.moment_action_publish), items, icons) { index ->
            when (index) {
                0 -> mediaCaptureLauncher.launch(WKVideoCapture.request("moment_compose"))
                1 -> chooseComposeMediaFromAlbum()
            }
        }
    }

    private fun showCoverBottomSheet() {
        showMomentBottomSheet(
            getString(R.string.moment_cover_title),
            arrayOf(getString(R.string.moment_action_cover)),
            intArrayOf(R.drawable.icon_moment_menu_album)
        ) {
            chooseCover()
        }
    }

    private fun showMomentBottomSheet(
        title: String,
        items: Array<String>,
        icons: IntArray,
        onClick: (Int) -> Unit
    ) {
        val builder = BottomSheet.Builder(this, false)
        builder.setTitle(title, false)
        builder.setItems(items, icons) { _, which ->
            onClick(which)
        }
        val bottomSheet = builder.create()
        bottomSheet.show()
        bottomSheet.itemViews.forEachIndexed { index, cell ->
            val imageView = cell.imageView
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setPadding(0, 0, 0, 0)
            val drawable = ContextCompat.getDrawable(this, icons[index]) ?: return@forEachIndexed
            imageView.setImageDrawable(drawable)
            (imageView.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.width = AndroidUtilities.dp(16f)
                params.height = AndroidUtilities.dp(16f)
                params.marginStart = AndroidUtilities.dp(20f)
                params.topMargin = 0
                params.bottomMargin = 0
                imageView.layoutParams = params
            }
        }
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
                                refreshHeaderCover(remotePath)
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

    private fun chooseComposeMediaFromAlbum() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val desc = getString(com.chat.base.R.string.album_permissions_desc, getString(com.chat.base.R.string.app_name))
        WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
            override fun onResult(result: Boolean) {
                if (!result) return
                GlideUtils.getInstance().chooseIMG(this@MomentTimelineActivity, 9, false, ChooseMimeType.all, true, object : GlideUtils.ISelectBack {
                    override fun onBack(paths: List<ChooseResult>) {
                        val medias = buildAlbumComposeMedias(paths) ?: return
                        openComposeWithMedias(ArrayList(medias))
                    }

                    override fun onCancel() {
                    }
                })
            }

            override fun clickResult(isCancel: Boolean) {
            }
        }, this, desc, *permissions)
    }

    private fun openComposeWithMedias(medias: ArrayList<MomentComposeMedia>) {
        val intent = Intent(this, MomentComposeActivity::class.java)
        intent.putParcelableArrayListExtra(MomentComposeActivity.EXTRA_INITIAL_MEDIAS, medias)
        composeLauncher.launch(intent)
    }

    private fun buildAlbumComposeMedias(paths: List<ChooseResult>): List<MomentComposeMedia>? {
        if (paths.isEmpty()) return null
        val videos = paths.filter { it.model == ChooseResultModel.video }
        if (videos.isNotEmpty()) {
            if (videos.size > 1 || paths.size > 1) {
                showToast(R.string.moment_only_one_video)
                return null
            }
            return listOf(buildVideoMedia(videos.first().path))
        }
        if (paths.size > 9) {
            showToast(R.string.moment_max_image_count)
            return null
        }
        return paths.map { buildImageMedia(it.path) }
    }

    private fun buildCapturedMedia(result: VideoCaptureResult): MomentComposeMedia {
        return if (result.mode == VideoCaptureResult.MODE_VIDEO) {
            MomentComposeMedia(
                type = MomentComposeMedia.TYPE_VIDEO,
                localPath = result.path,
                coverPath = result.coverPath,
                width = result.width,
                height = result.height,
                durationMs = result.durationMs,
                size = result.size
            )
        } else {
            buildImageMedia(result.path)
        }
    }

    private fun buildImageMedia(path: String): MomentComposeMedia {
        val options = android.graphics.BitmapFactory.Options()
        options.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeFile(path, options)
        return MomentComposeMedia(
            type = MomentComposeMedia.TYPE_IMAGE,
            localPath = path,
            width = options.outWidth,
            height = options.outHeight,
            size = WKFileUtils.getInstance().getFileSize(path)
        )
    }

    private fun buildVideoMedia(path: String): MomentComposeMedia {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        return MomentComposeMedia(
            type = MomentComposeMedia.TYPE_VIDEO,
            localPath = path,
            coverPath = WKMediaFileUtils.getInstance().getVideoCover(path),
            width = width,
            height = height,
            durationMs = WKMediaFileUtils.getInstance().getVideoTime(path),
            size = WKFileUtils.getInstance().getFileSize(path)
        )
    }

    private fun refreshHeaderCover(fallbackCover: String) {
        val uid = headerUid.orEmpty()
        if (uid.isEmpty()) {
            showHeaderCover(fallbackCover, System.currentTimeMillis())
            WKToastUtils.getInstance().showToastNormal(getString(R.string.moment_cover_update_success))
            return
        }
        try {
            MomentModel.instance.getProfile(uid) { code, msg, profile ->
                if (code == HttpResponseCode.success.toInt()) {
                    val targetPath = profile.cover.ifEmpty { fallbackCover }
                    val targetVersion = if (profile.version > 0L) profile.version else System.currentTimeMillis()
                    showHeaderCover(targetPath, targetVersion)
                    WKToastUtils.getInstance().showToastNormal(getString(R.string.moment_cover_update_success))
                } else {
                    showToast(msg)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun refreshHeaderCoverFromProfile() {
        val uid = headerUid.orEmpty()
        if (uid.isEmpty()) return
        try {
            MomentModel.instance.getProfile(uid) { code, _, profile ->
                if (code == HttpResponseCode.success.toInt() && profile.cover.isNotEmpty()) {
                    showHeaderCover(profile.cover, profile.version)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showHeaderCover(path: String, version: Long) {
        try {
            if (path.isEmpty()) return
            currentHeaderCoverPath = path
            currentHeaderCoverVersion = version
            MomentUiUtils.showImage(this, MomentUiUtils.buildCacheBustedUrl(path, version), coverIv)
        } catch (_: Exception) {
        }
    }

    private fun showActionPopup(anchor: View, post: MomentPost) {
        val list = arrayListOf(
            PopupMenuItem(
                getString(if (post.likedByMe) R.string.moment_cancel_action else R.string.moment_like_action),
                if (post.likedByMe) R.drawable.icon_moment_like_menu_active else R.drawable.icon_moment_like_outline
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

    private fun toggleFavoriteText(post: MomentPost) {
        MomentModel.instance.toggleFavorite(post.postId) { code, msg, isFavorite ->
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg)
                return@toggleFavorite
            }
            WKToastUtils.getInstance().showToastNormal(
                getString(
                    if (isFavorite) com.chat.uikit.R.string.favorite_add_success
                    else com.chat.uikit.R.string.favorite_delete_success
                )
            )
        }
    }

    private fun toggleFavoriteImage(post: MomentPost, media: MomentMedia) {
        MomentModel.instance.toggleFavorite(
            post.postId,
            favoriteType = "image",
            mediaUrl = media.url.ifEmpty { null }
        ) { code, msg, isFavorite ->
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg)
                return@toggleFavorite
            }
            WKToastUtils.getInstance().showToastNormal(
                getString(
                    if (isFavorite) com.chat.uikit.R.string.favorite_add_success
                    else com.chat.uikit.R.string.favorite_delete_success
                )
            )
        }
    }
}
