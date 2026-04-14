package com.chat.video.provider

import android.content.Intent
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import com.chat.base.WKBaseApplication
import com.chat.base.act.PlayVideoActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.AndroidUtilities
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKMsgBgType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.net.ud.WKDownloader
import com.chat.base.net.ud.WKProgressManager
import com.chat.base.ui.Theme
import com.chat.base.ui.components.FilterImageView
import com.chat.base.utils.WKToastUtils
import com.chat.base.views.CircularProgressView
import com.chat.video.R
import com.chat.video.util.VideoDownloadRegistry
import com.chat.video.util.VideoUiUtils
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKMsg
import com.xinbida.wukongim.message.type.WKMsgContentType
import com.xinbida.wukongim.msgmodel.WKVideoContent
import java.io.File

class WKVideoProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View {
        return LayoutInflater.from(context).inflate(R.layout.wk_video_chat_item, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val videoFrame = parentView.findViewById<FrameLayout>(R.id.videoFrame)
        val coverIv = parentView.findViewById<FilterImageView>(R.id.coverIv)
        val playIv = parentView.findViewById<ImageView>(R.id.playIv)
        val progressLayout = parentView.findViewById<LinearLayout>(R.id.progressLayout)
        val progressView = parentView.findViewById<CircularProgressView>(R.id.progressView)
        val progressTv = parentView.findViewById<TextView>(R.id.progressTv)
        val metaTv = parentView.findViewById<TextView>(R.id.metaTv)
        val videoContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as WKVideoContent

        contentLayout.gravity = if (from == WKChatIteMsgFromType.RECEIVED) Gravity.START else Gravity.END
        progressView.setProgColor(Theme.colorAccount)

        // 视频气泡尺寸在原始分辨率基础上做一次温和放大，让它和图片消息更接近。
        val bubbleSize = scaleBubbleSize(VideoUiUtils.resolveBubbleSize(videoContent.width, videoContent.height))
        coverIv.layoutParams = (coverIv.layoutParams as FrameLayout.LayoutParams).apply {
            width = bubbleSize[0]
            height = bubbleSize[1]
        }
        videoFrame.layoutParams = (videoFrame.layoutParams as LinearLayout.LayoutParams).apply {
            width = bubbleSize[0]
            height = bubbleSize[1]
        }

        // 暂时隐藏左下角“时长 + 大小”展示，保留逻辑便于后续恢复。
        // val metaText = buildMetaText(videoContent)
        // metaTv.text = metaText
        // metaTv.visibility = if (metaText.isEmpty()) View.GONE else View.VISIBLE
        metaTv.visibility = View.GONE

        val coverPath = getCoverPath(videoContent)
        if (!TextUtils.isEmpty(coverPath)) {
            GlideUtils.getInstance().showImg(context, coverPath, bubbleSize[0], bubbleSize[1], coverIv)
        } else {
            coverIv.setImageDrawable(null)
            coverIv.setBackgroundColor(0xFF111111.toInt())
        }

        resetProgress(progressLayout, playIv)
        setCorners(from, uiChatMsgItemEntity, coverIv)

        when {
            TextUtils.isEmpty(videoContent.url) -> {
                progressLayout.visibility = View.GONE
                playIv.visibility = View.VISIBLE
            }

            hasLocalVideo(videoContent) -> {
                progressLayout.visibility = View.GONE
                playIv.visibility = View.VISIBLE
            }

            else -> {
                val downloadUrl = getDownloadUrl(videoContent)
                // 正在下载中的消息重新绑定时，要把进度状态接回来。
                if (downloadUrl.isNotEmpty() && VideoDownloadRegistry.isDownloading(downloadUrl)) {
                    registerDownloadProgress(
                        uiChatMsgItemEntity,
                        videoContent,
                        progressLayout,
                        progressView,
                        progressTv,
                        playIv,
                        coverIv,
                        false
                    )
                } else {
                    progressLayout.visibility = View.GONE
                    playIv.visibility = View.VISIBLE
                }
            }
        }

        val clickListener = View.OnClickListener {
            openOrDownloadVideo(uiChatMsgItemEntity, videoContent, coverIv, progressLayout, progressView, progressTv, playIv)
        }
        videoFrame.setOnClickListener(clickListener)
        coverIv.setOnClickListener(clickListener)
        playIv.setOnClickListener(clickListener)
    }

    override val itemViewType: Int
        get() = WKMsgContentType.WK_VIDEO

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val coverIv = parentView.findViewById<FilterImageView>(R.id.coverIv)
        setCorners(from, uiChatMsgItemEntity, coverIv)
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        val videoFrame = parentView.findViewById<FrameLayout>(R.id.videoFrame)
        val coverIv = parentView.findViewById<ImageView>(R.id.coverIv)
        val playIv = parentView.findViewById<ImageView>(R.id.playIv)
        addLongClick(videoFrame, uiChatMsgItemEntity)
        addLongClick(coverIv, uiChatMsgItemEntity)
        addLongClick(playIv, uiChatMsgItemEntity)
    }

    private fun openOrDownloadVideo(
        entity: WKUIChatMsgItemEntity,
        content: WKVideoContent,
        coverIv: View,
        progressLayout: LinearLayout,
        progressView: CircularProgressView,
        progressTv: TextView,
        playIv: ImageView
    ) {
        val localPath = content.localPath
        if (!localPath.isNullOrEmpty()) {
            val file = File(localPath)
            if (file.exists() && file.length() > 0L) {
                // 本地已有视频文件时直接进播放器，避免重复下载。
                openPlayer(entity.wkMsg, coverIv, localPath, getCoverPath(content))
                return
            }
        }

        if (TextUtils.isEmpty(content.url)) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.video_waiting_upload))
            return
        }

        registerDownloadProgress(
            entity,
            content,
            progressLayout,
            progressView,
            progressTv,
            playIv,
            coverIv,
            true
        )

        val showUrl = getDownloadUrl(content)
        if (showUrl.isEmpty()) {
            return
        }
        // 同一条消息只允许一个下载任务，避免重复点击触发多次下载。
        if (!VideoDownloadRegistry.markDownloading(showUrl)) {
            return
        }
        val savePath = VideoUiUtils.createDownloadVideoPath(context, entity.wkMsg.clientMsgNO)
        VideoUiUtils.ensureParent(savePath)
        WKDownloader.instance.download(showUrl, savePath, null)
    }

    private fun registerDownloadProgress(
        entity: WKUIChatMsgItemEntity,
        content: WKVideoContent,
        progressLayout: LinearLayout,
        progressView: CircularProgressView,
        progressTv: TextView,
        playIv: ImageView,
        coverIv: View,
        needPlayWhenReady: Boolean
    ) {
        val downloadUrl = getDownloadUrl(content)
        if (downloadUrl.isEmpty()) {
            return
        }
        progressLayout.visibility = View.VISIBLE
        playIv.visibility = View.GONE
        progressTv.visibility = View.GONE
        WKProgressManager.instance.registerProgress(downloadUrl,
            object : WKProgressManager.IProgress {
                override fun onProgress(tag: Any?, progress: Int) {
                    if (tag == downloadUrl) {
                        progressLayout.visibility = View.VISIBLE
                        playIv.visibility = View.GONE
                        progressView.progress = progress
                        progressTv.visibility = View.GONE
                    }
                }

                override fun onSuccess(tag: Any?, path: String?) {
                    if (tag == downloadUrl) {
                        VideoDownloadRegistry.clear(downloadUrl)
                        progressLayout.visibility = View.GONE
                        playIv.visibility = View.VISIBLE
                        WKProgressManager.instance.unregisterProgress(downloadUrl)
                        if (!path.isNullOrEmpty()) {
                            content.localPath = path
                            WKIM.getInstance().msgManager.updateContentAndRefresh(
                                entity.wkMsg.clientMsgNO,
                                content,
                                false
                            )
                            // 用户是通过点击触发下载时，下载完成后直接拉起播放器。
                            if (needPlayWhenReady) {
                                openPlayer(entity.wkMsg, coverIv, path, getCoverPath(content))
                            }
                        }
                    }
                }

                override fun onFail(tag: Any?, msg: String?) {
                    if (tag == downloadUrl) {
                        VideoDownloadRegistry.clear(downloadUrl)
                        progressLayout.visibility = View.GONE
                        playIv.visibility = View.VISIBLE
                        WKProgressManager.instance.unregisterProgress(downloadUrl)
                        WKToastUtils.getInstance().showToastNormal(context.getString(R.string.video_download_failed))
                    }
                }
            })
    }

    private fun openPlayer(msg: WKMsg, coverView: View, playPath: String, coverPath: String) {
        val activity = context as? AppCompatActivity ?: return
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            activity,
            Pair(coverView, "coverIv")
        )
        // 聊天视频仍然复用 wkbase 的正式播放器页，预览页全屏裁切逻辑不会影响这里。
        val intent = Intent(activity, PlayVideoActivity::class.java)
        intent.putExtra("coverImg", coverPath)
        intent.putExtra("url", playPath)
        intent.putExtra("title", "")
        intent.putExtra("clientMsgNo", msg.clientMsgNO)
        activity.startActivity(intent, options.toBundle())
        activity.overridePendingTransition(com.chat.base.R.anim.fade_in, com.chat.base.R.anim.fade_out)
    }

    private fun getCoverPath(content: WKVideoContent): String {
        if (!content.coverLocalPath.isNullOrEmpty()) {
            val localCover = File(content.coverLocalPath)
            if (localCover.exists() && localCover.length() > 0L) {
                return content.coverLocalPath
            }
        }
        if (!content.cover.isNullOrEmpty()) {
            return WKApiConfig.getShowUrl(content.cover)
        }
        return ""
    }

    private fun hasLocalVideo(content: WKVideoContent): Boolean {
        if (content.localPath.isNullOrEmpty()) {
            return false
        }
        val file = File(content.localPath)
        return file.exists() && file.length() > 0L
    }

    private fun resetProgress(progressLayout: LinearLayout, playIv: ImageView) {
        progressLayout.visibility = View.GONE
        playIv.visibility = View.VISIBLE
    }

    private fun getDownloadUrl(content: WKVideoContent): String {
        return if (content.url.isNullOrEmpty()) "" else WKApiConfig.getShowUrl(content.url)
    }

    private fun scaleBubbleSize(size: IntArray): IntArray {
        val scaledWidth = (size[0] * 1.12f).toInt()
        val scaledHeight = (size[1] * 1.12f).toInt()
        val maxWidth = AndroidUtilities.getScreenWidth() * 3 / 5
        val maxHeight = AndroidUtilities.getScreenHeight() / 3
        val width = scaledWidth.coerceAtMost(maxWidth).coerceAtLeast(AndroidUtilities.dp(145f))
        val height = scaledHeight.coerceAtMost(maxHeight).coerceAtLeast(AndroidUtilities.dp(110f))
        return intArrayOf(width, height)
    }

    private fun setCorners(
        from: WKChatIteMsgFromType,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        coverIv: FilterImageView
    ) {
        // 圆角规则和图片消息保持一致，确保连续消息时视觉连接自然。
        coverIv.strokeWidth = 0f
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        if (bgType == WKMsgBgType.center) {
            if (from == WKChatIteMsgFromType.SEND) {
                coverIv.setCorners(10, 5, 10, 5)
            } else {
                coverIv.setCorners(5, 10, 5, 10)
            }
        } else if (bgType == WKMsgBgType.top) {
            if (from == WKChatIteMsgFromType.SEND) {
                coverIv.setCorners(10, 10, 10, 5)
            } else {
                coverIv.setCorners(10, 10, 5, 10)
            }
        } else if (bgType == WKMsgBgType.bottom) {
            if (from == WKChatIteMsgFromType.SEND) {
                coverIv.setCorners(10, 5, 10, 10)
            } else {
                coverIv.setCorners(5, 10, 10, 10)
            }
        } else {
            coverIv.setAllCorners(10)
        }
    }
}
