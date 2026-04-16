package com.chat.moments.ui

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.moments.R
import com.chat.moments.WKMomentsApplication
import com.chat.moments.databinding.ActMomentNoticeLayoutBinding
import com.chat.moments.service.MomentModel
import com.chat.moments.store.MomentPrefs
import com.chat.moments.ui.adapter.MomentNoticeAdapter

class MomentNoticeActivity : WKBaseActivity<ActMomentNoticeLayoutBinding>() {

    private val adapter = MomentNoticeAdapter { notice ->
        startActivity(Intent(this, MomentTimelineActivity::class.java))
    }

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
        MomentModel.instance.syncNotices(0, 100) { code, msg, list, version ->
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg)
                return@syncNotices
            }
            adapter.setList(list)
            wkVBinding.noDataTv.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            MomentPrefs.saveUnreadCount(0)
            MomentPrefs.saveNoticeVersion(version)
            WKMomentsApplication.getInstance().refreshMomentEntry()
            val unreadIds = list.filter { !it.isRead }.map { it.id }
            if (unreadIds.isNotEmpty()) {
                MomentModel.instance.markNoticesRead(unreadIds, false) { _, _ -> }
            }
        }
    }
}
