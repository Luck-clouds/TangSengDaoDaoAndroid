package com.chat.moments.ui

/**
 * 朋友圈消息通知页
 * Created by Luckclouds.
 */

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.moments.R
import com.chat.moments.WKMomentsApplication
import com.chat.moments.databinding.ActMomentNoticeLayoutBinding
import com.chat.moments.service.MomentModel
import com.chat.moments.ui.adapter.MomentNoticeAdapter

class MomentNoticeActivity : WKBaseActivity<ActMomentNoticeLayoutBinding>() {

    private val adapter = MomentNoticeAdapter()
    private val postCache = mutableMapOf<String, com.chat.moments.entity.MomentPost>()
    private val noticeListenerTag = "moment_notice_page"

    override fun getViewBinding(): ActMomentNoticeLayoutBinding {
        return ActMomentNoticeLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.moment_notice_title)
    }

    override fun initView() {
        wkVBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        wkVBinding.recyclerView.adapter = adapter
    }

    override fun initData() {
        renderFromLocal()
        WKMomentsApplication.getInstance().requestMomentNoticeSync(debounce = false, force = true) {
            renderFromLocal()
            WKMomentsApplication.getInstance().markAllMomentNoticesRead()
        }
    }

    override fun onResume() {
        super.onResume()
        WKMomentsApplication.getInstance().addNoticeStateListener(noticeListenerTag) {
            renderFromLocal()
        }
    }

    override fun onPause() {
        super.onPause()
        WKMomentsApplication.getInstance().removeNoticeStateListener(noticeListenerTag)
    }

    /**
     * Notice page always renders from the unified local cache.
     * Realtime CMD sync updates this cache, and the page only enriches display fields.
     */
    private fun renderFromLocal() {
        val ordered = WKMomentsApplication.getInstance().cachedNotices().sortedByDescending { it.version }
        enrichNoticeContent(ordered) { enriched ->
            adapter.setList(enriched)
            wkVBinding.noDataTv.visibility = if (enriched.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun enrichNoticeContent(
        list: List<com.chat.moments.entity.MomentNotice>,
        callback: (List<com.chat.moments.entity.MomentNotice>) -> Unit
    ) {
        if (list.isEmpty()) {
            callback(list)
            return
        }
        enrichNoticeAt(list.toMutableList(), 0, callback)
    }

    private fun enrichNoticeAt(
        list: MutableList<com.chat.moments.entity.MomentNotice>,
        index: Int,
        callback: (List<com.chat.moments.entity.MomentNotice>) -> Unit
    ) {
        if (index >= list.size) {
            callback(list)
            return
        }
        val notice = list[index]
        val needContent = notice.noticeType.equals("comment", true) || notice.noticeType.equals("reply", true)
        val needThumb = notice.mediaThumb.isEmpty()
        if (notice.postId.isEmpty() || (!needContent && !needThumb)) {
            enrichNoticeAt(list, index + 1, callback)
            return
        }
        val cached = postCache[notice.postId]
        if (cached != null) {
            applyPostToNotice(notice, cached)
            enrichNoticeAt(list, index + 1, callback)
            return
        }
        MomentModel.instance.getPostDetail(notice.postId) { code, _, post ->
            if (code == HttpResponseCode.success.toInt()) {
                postCache[notice.postId] = post
                applyPostToNotice(notice, post)
            }
            enrichNoticeAt(list, index + 1, callback)
        }
    }

    private fun applyPostToNotice(
        notice: com.chat.moments.entity.MomentNotice,
        post: com.chat.moments.entity.MomentPost
    ) {
        if (notice.mediaThumb.isEmpty()) {
            val firstMedia = post.medias.sortedBy { it.sortIndex }.firstOrNull()
            if (firstMedia != null) {
                notice.mediaThumb = if (firstMedia.coverUrl.isNotEmpty()) firstMedia.coverUrl else firstMedia.url
            }
        }
        if (notice.content.isNotEmpty()) return
        if (!notice.noticeType.equals("comment", true) && !notice.noticeType.equals("reply", true)) return
        val comment = post.comments.firstOrNull { it.commentId == notice.commentId } ?: return
        notice.content = comment.content
    }
}
