package com.chat.moments.ui

/**
 * 朋友圈发布编辑页
 * Created by Luckclouds.
 */

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ChooseContactsMenu
import com.chat.base.glide.ChooseMimeType
import com.chat.base.glide.ChooseResult
import com.chat.base.glide.ChooseResultModel
import com.chat.base.glide.GlideUtils
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKMediaFileUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKToastUtils
import com.chat.moments.R
import com.chat.moments.databinding.ActMomentComposeLayoutBinding
import com.chat.moments.entity.MomentAudienceSelection
import com.chat.moments.entity.MomentComposeMedia
import com.chat.moments.service.MomentModel
import com.chat.moments.ui.adapter.MomentComposeMediaAdapter
import com.chat.moments.util.MomentUiUtils
import com.chat.video.contract.VideoCaptureResult
import com.chat.video.contract.WKVideoCapture
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType

class MomentComposeActivity : WKBaseActivity<ActMomentComposeLayoutBinding>() {

    companion object {
        const val EXTRA_AUTO_ACTION = "moment_auto_action"
        const val EXTRA_TEXT_ONLY = "moment_text_only"
        const val EXTRA_INITIAL_MEDIAS = "moment_initial_medias"
        const val ACTION_CAPTURE = "capture"
        const val ACTION_ALBUM = "album"
    }

    private val medias = arrayListOf<MomentComposeMedia>()
    private var locationTitle = ""
    private var mentionSelection = MomentAudienceSelection()
    private var visibilitySelection = MomentAudienceSelection()
    private var textOnlyMode = false
    private var dragOriginPosition = RecyclerView.NO_POSITION
    private var dragPreviewTargetPosition = RecyclerView.NO_POSITION
    private var dragOriginLeft = 0
    private var dragOriginTop = 0
    private val mediaTouchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= medias.size) {
                    return makeMovementFlags(0, 0)
                }
                if (medias[position].type != MomentComposeMedia.TYPE_IMAGE) {
                    return makeMovementFlags(0, 0)
                }
                return super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false
                if (fromPosition >= medias.size || toPosition >= medias.size) return false
                if (medias[fromPosition].type != MomentComposeMedia.TYPE_IMAGE || medias[toPosition].type != MomentComposeMedia.TYPE_IMAGE) {
                    return false
                }
                if (dragOriginPosition == RecyclerView.NO_POSITION) {
                    dragOriginPosition = fromPosition
                }
                if (dragPreviewTargetPosition == toPosition) {
                    return true
                }
                // 拖动中只做“目标坑位”的视觉预览，不提前改动真实 medias 顺序，
                // 避免还没松手时列表就被真正重排。
                resetPreviewTarget()
                dragPreviewTargetPosition = toPosition
                applyPreviewTarget(toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    dragOriginPosition = viewHolder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
                    dragPreviewTargetPosition = RecyclerView.NO_POSITION
                    dragOriginLeft = viewHolder?.itemView?.left ?: 0
                    dragOriginTop = viewHolder?.itemView?.top ?: 0
                    viewHolder?.itemView?.animate()?.scaleX(1.04f)?.scaleY(1.04f)?.alpha(0.92f)?.setDuration(120L)?.start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val finalTarget = dragPreviewTargetPosition
                resetPreviewTarget()
                if (dragOriginPosition != RecyclerView.NO_POSITION &&
                    finalTarget != RecyclerView.NO_POSITION &&
                    dragOriginPosition < medias.size &&
                    finalTarget < medias.size &&
                    dragOriginPosition != finalTarget &&
                    medias[dragOriginPosition].type == MomentComposeMedia.TYPE_IMAGE &&
                    medias[finalTarget].type == MomentComposeMedia.TYPE_IMAGE
                ) {
                    val temp = medias[dragOriginPosition]
                    medias[dragOriginPosition] = medias[finalTarget]
                    medias[finalTarget] = temp
                    (wkVBinding.mediaRecyclerView.parent as? ViewGroup)?.let { parent ->
                        TransitionManager.beginDelayedTransition(parent, AutoTransition().apply {
                            duration = 180L
                        })
                    }
                    renderMediaList()
                }
                dragOriginPosition = RecyclerView.NO_POSITION
                dragPreviewTargetPosition = RecyclerView.NO_POSITION
                dragOriginLeft = 0
                dragOriginTop = 0
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120L).start()
            }
        })
    }

    private val mediaAdapter by lazy {
        MomentComposeMediaAdapter(
            onAddClick = ::chooseMediaFromAlbum,
            onDeleteClick = { _, media ->
                medias.remove(media)
                renderMediaList()
                updatePublishEnabled()
            },
            onPreviewClick = { media, imageView ->
                if (media.type == MomentComposeMedia.TYPE_VIDEO) {
                    val intent = Intent(this, com.chat.base.act.PlayVideoActivity::class.java)
                    intent.putExtra("url", media.localPath)
                    intent.putExtra("coverImg", media.coverPath ?: media.localPath)
                    intent.putExtra("title", getString(R.string.moment_video))
                    startActivity(intent)
                } else {
                    val imagePaths = medias.filter { it.type == MomentComposeMedia.TYPE_IMAGE }.map { it.localPath }
                    val selectedIndex = imagePaths.indexOf(media.localPath).coerceAtLeast(0)
                    MomentUiUtils.showImagePopup(this, imageView, imagePaths, selectedIndex)
                }
            }
        )
    }

    private val captureLauncher = registerForActivityResult(WKVideoCapture.contract()) { result ->
        if (result == null) return@registerForActivityResult
        addCapturedMedia(result)
    }

    private val visibilityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        visibilitySelection = result.data?.getParcelableExtra(MomentVisibilityActivity.EXTRA_RESULT) ?: MomentAudienceSelection()
        renderVisibilitySummary()
    }

    override fun getViewBinding(): ActMomentComposeLayoutBinding {
        return ActMomentComposeLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.moment_title)
    }

    override fun getRightTvText(textView: TextView): String {
        return getString(R.string.moment_publish)
    }

    override fun initView() {
        textOnlyMode = intent.getBooleanExtra(EXTRA_TEXT_ONLY, false)
        wkVBinding.mediaRecyclerView.layoutManager = GridLayoutManager(this, 3)
        wkVBinding.mediaRecyclerView.adapter = mediaAdapter
        mediaTouchHelper.attachToRecyclerView(wkVBinding.mediaRecyclerView)
        MomentUiUtils.limitIconInside(wkVBinding.mentionIconIv, R.drawable.icon_moment_mention, insetDp = 2.5f)
        MomentUiUtils.limitIconInside(wkVBinding.visibilityIconIv, R.drawable.icon_moment_visibility, insetDp = 2.5f)
        MomentUiUtils.limitIconInside(wkVBinding.mentionArrowIv, com.chat.base.R.mipmap.ic_arrow_right, insetDp = 2.5f)
        MomentUiUtils.limitIconInside(wkVBinding.visibilityArrowIv, com.chat.base.R.mipmap.ic_arrow_right, insetDp = 2.5f)
        val initialMedias = intent.getParcelableArrayListExtra<MomentComposeMedia>(EXTRA_INITIAL_MEDIAS)
        if (!initialMedias.isNullOrEmpty()) {
            medias.clear()
            medias.addAll(initialMedias)
        }
        setRightViewEnabled(false)
        renderMediaList()
        renderMentionSummary()
        renderVisibilitySummary()
        updatePublishEnabled()
    }

    override fun initListener() {
        wkVBinding.contentEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val content = wkVBinding.contentEt.text?.toString().orEmpty()
                wkVBinding.countTv.text = "${content.length}/500"
                updatePublishEnabled()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })
        wkVBinding.mentionLayout.setOnClickListener {
            EndpointManager.getInstance().invoke(
                "choose_contacts",
                ChooseContactsMenu(-1, true, false, mentionSelection.users.map { toChannel(it) }, object : ChooseContactsMenu.IChooseBack {
                    override fun onBack(selectedList: MutableList<WKChannel>?) {
                        mentionSelection.users.clear()
                        mentionSelection.tags.clear()
                        selectedList?.forEach { channel ->
                            mentionSelection.users.add(
                                com.chat.moments.entity.MomentUserChoice(
                                    channel.channelID,
                                    channel.channelRemark.ifEmpty { channel.channelName },
                                    channel.avatar
                                )
                            )
                        }
                        renderMentionSummary()
                    }
                })
            )
        }
        wkVBinding.visibilityLayout.setOnClickListener {
            val intent = Intent(this, MomentVisibilityActivity::class.java)
            intent.putExtra(MomentVisibilityActivity.EXTRA_SELECTION, visibilitySelection)
            visibilityLauncher.launch(intent)
        }
    }

    override fun initData() {
        wkVBinding.countTv.text = "0/500"
        when (intent.getStringExtra(EXTRA_AUTO_ACTION)) {
            ACTION_CAPTURE -> wkVBinding.mediaRecyclerView.post { captureLauncher.launch(WKVideoCapture.request("moment_compose")) }
            ACTION_ALBUM -> wkVBinding.mediaRecyclerView.post { chooseMediaFromAlbum() }
        }
    }

    override fun rightLayoutClick() {
        val content = wkVBinding.contentEt.text?.toString().orEmpty().trim()
        if (content.isEmpty() && medias.isEmpty()) {
            showToast(R.string.moment_select_media_first)
            return
        }
        showTitleRightLoading()
        MomentModel.instance.publishMoment(content, medias, locationTitle, mentionSelection, visibilitySelection) { code, msg ->
            hideTitleRightLoading()
            if (code == HttpResponseCode.success.toInt()) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.moment_publish_success))
                setResult(RESULT_OK)
                finish()
            } else {
                showToast(if (msg.isEmpty()) getString(R.string.moment_publish_failed) else msg)
            }
        }
    }

    private fun chooseMediaFromAlbum() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val desc = getString(com.chat.base.R.string.album_permissions_desc, getString(com.chat.base.R.string.app_name))
        val mimeType = if (medias.any { it.type == MomentComposeMedia.TYPE_IMAGE }) ChooseMimeType.img else ChooseMimeType.all
        WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
            override fun onResult(result: Boolean) {
                if (!result) return
                GlideUtils.getInstance().chooseIMG(this@MomentComposeActivity, remainingSelectCount(), false, mimeType, true, object : GlideUtils.ISelectBack {
                    override fun onBack(paths: List<ChooseResult>) {
                        addAlbumResults(paths)
                    }

                    override fun onCancel() {
                    }
                })
            }

            override fun clickResult(isCancel: Boolean) {
            }
        }, this, desc, *permissions)
    }

    private fun addAlbumResults(paths: List<ChooseResult>) {
        if (paths.isEmpty()) return
        val videos = paths.filter { it.model == ChooseResultModel.video }
        if (videos.isNotEmpty()) {
            if (videos.size > 1 || paths.size > 1 || medias.isNotEmpty()) {
                showToast(R.string.moment_only_one_video)
                return
            }
            val videoPath = videos.first().path
            medias.clear()
            medias += buildVideoMedia(videoPath)
            renderMediaList()
            updatePublishEnabled()
            return
        }
        if (medias.any { it.type == MomentComposeMedia.TYPE_VIDEO }) {
            showToast(R.string.moment_only_one_video)
            return
        }
        if (medias.size + paths.size > 9) {
            showToast(R.string.moment_max_image_count)
            return
        }
        paths.forEach { result ->
            medias += buildImageMedia(result.path)
        }
        renderMediaList()
        updatePublishEnabled()
    }

    private fun addCapturedMedia(result: VideoCaptureResult) {
        if (result.mode == VideoCaptureResult.MODE_VIDEO) {
            if (medias.isNotEmpty()) {
                showToast(R.string.moment_only_one_video)
                return
            }
            medias.clear()
            medias += MomentComposeMedia(
                type = MomentComposeMedia.TYPE_VIDEO,
                localPath = result.path,
                coverPath = result.coverPath,
                width = result.width,
                height = result.height,
                durationMs = result.durationMs,
                size = result.size
            )
        } else {
            if (medias.any { it.type == MomentComposeMedia.TYPE_VIDEO } || medias.size >= 9) {
                showToast(R.string.moment_max_image_count)
                return
            }
            medias += buildImageMedia(result.path)
        }
        renderMediaList()
        updatePublishEnabled()
    }

    private fun buildImageMedia(path: String): MomentComposeMedia {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
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
        return try {
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            MomentComposeMedia(
                type = MomentComposeMedia.TYPE_VIDEO,
                localPath = path,
                coverPath = WKMediaFileUtils.getInstance().getVideoCover(path),
                width = width,
                height = height,
                durationMs = WKMediaFileUtils.getInstance().getVideoTime(path),
                size = WKFileUtils.getInstance().getFileSize(path)
            )
        } catch (_: Exception) {
            MomentComposeMedia(
                type = MomentComposeMedia.TYPE_VIDEO,
                localPath = path,
                coverPath = path
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun renderMediaList() {
        if (textOnlyMode && medias.isEmpty()) {
            wkVBinding.mediaRecyclerView.visibility = View.GONE
            mediaAdapter.setList(emptyList())
            return
        }
        wkVBinding.mediaRecyclerView.visibility = View.VISIBLE
        val display = arrayListOf<MomentComposeMedia?>()
        display.addAll(medias)
        if (canAddMoreMedia()) {
            display += null
        }
        mediaAdapter.setList(display)
    }

    private fun applyPreviewTarget(targetPosition: Int) {
        if (dragOriginPosition == RecyclerView.NO_POSITION ||
            targetPosition == RecyclerView.NO_POSITION ||
            targetPosition == dragOriginPosition
        ) {
            return
        }
        try {
            val targetView = wkVBinding.mediaRecyclerView.findViewHolderForAdapterPosition(targetPosition)?.itemView ?: return
            val deltaX = (dragOriginLeft - targetView.left).toFloat()
            val deltaY = (dragOriginTop - targetView.top).toFloat()
            targetView.animate()
                .translationX(deltaX)
                .translationY(deltaY)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(140L)
                .start()
        } catch (_: Exception) {
        }
    }

    private fun resetPreviewTarget() {
        if (dragPreviewTargetPosition == RecyclerView.NO_POSITION) return
        try {
            val targetView = wkVBinding.mediaRecyclerView.findViewHolderForAdapterPosition(dragPreviewTargetPosition)?.itemView ?: return
            targetView.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120L)
                .start()
        } catch (_: Exception) {
        }
    }

    private fun updatePublishEnabled() {
        setRightViewEnabled(wkVBinding.contentEt.text?.isNotBlank() == true || medias.isNotEmpty())
    }

    private fun remainingSelectCount(): Int {
        if (medias.any { it.type == MomentComposeMedia.TYPE_VIDEO }) {
            return 0
        }
        return 9 - medias.size
    }

    private fun canAddMoreMedia(): Boolean {
        return medias.none { it.type == MomentComposeMedia.TYPE_VIDEO } && medias.size < 9
    }

    private fun renderMentionSummary() {
        val summary = when {
            mentionSelection.users.isNotEmpty() -> mentionSelection.users.size.toString()
            mentionSelection.tags.isNotEmpty() -> mentionSelection.tags.size.toString()
            else -> ""
        }
        wkVBinding.mentionValueTv.text = summary
        wkVBinding.mentionValueTv.visibility = if (summary.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderVisibilitySummary() {
        wkVBinding.visibilityValueTv.setText(MomentUiUtils.visibilityLabel(visibilitySelection.type))
    }

    private fun toChannel(choice: com.chat.moments.entity.MomentUserChoice): WKChannel {
        val channel = WKChannel()
        channel.channelID = choice.uid
        channel.channelType = WKChannelType.PERSONAL
        channel.channelName = choice.name
        channel.avatar = choice.avatar
        return channel
    }
}
