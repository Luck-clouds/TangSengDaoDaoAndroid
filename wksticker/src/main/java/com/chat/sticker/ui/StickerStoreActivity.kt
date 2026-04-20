package com.chat.sticker.ui

import android.widget.TextView
import androidx.annotation.NonNull
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKReader
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerStoreLayoutBinding
import com.chat.sticker.service.StickerModel
import com.chat.sticker.ui.adapter.StickerPackageAdapter
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

/**
 * 表情商店页
 * Created by Luckclouds .
 */
class StickerStoreActivity : WKBaseActivity<ActStickerStoreLayoutBinding>() {
    private val adapter = StickerPackageAdapter()
    private var pageIndex = 1
    private var totalCount = 0
    private val pageSize = 20

    override fun getViewBinding(): ActStickerStoreLayoutBinding = ActStickerStoreLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.sticker_title)
    }

    override fun getRightTvText(textView: TextView): String = getString(R.string.sticker_my_title)

    override fun rightLayoutClick() {
        startActivity(android.content.Intent(this, StickerMyStickersActivity::class.java))
    }

    override fun initView() {
        initAdapter(wkVBinding.recyclerView, adapter)
        adapter.addChildClickViewIds(R.id.actionTv)
    }

    override fun initListener() {
        adapter.setOnItemClickListener { _, _, position ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            val intent = android.content.Intent(this, StickerPackageDetailActivity::class.java)
            intent.putExtra(StickerPackageDetailActivity.EXTRA_PACKAGE_ID, item.packageId)
            intent.putExtra(StickerPackageDetailActivity.EXTRA_IS_ADDED, item.isAdded)
            startActivity(intent)
        }
        adapter.setOnItemChildClickListener { _, view, position ->
            if (view.id != R.id.actionTv) return@setOnItemChildClickListener
            val item = adapter.getItem(position) ?: return@setOnItemChildClickListener
            val callback: (Int, String) -> Unit = { code, msg ->
                if (code == HttpResponseCode.success.toInt()) {
                    item.isAdded = !item.isAdded
                    adapter.notifyItemChanged(position)
                    StickerPanelView.notifyDataChanged()
                } else {
                    showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
                }
            }
            if (item.isAdded) {
                StickerModel.instance.removeMyPackage(item.packageId, callback)
            } else {
                StickerModel.instance.addMyPackage(item.packageId, callback)
            }
        }
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(@NonNull refreshLayout: RefreshLayout) {
                pageIndex++
                loadData()
            }

            override fun onRefresh(@NonNull refreshLayout: RefreshLayout) {
                pageIndex = 1
                loadData()
            }
        })
    }

    override fun initData() {
        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (adapter.data.isNotEmpty()) {
            pageIndex = 1
            loadData()
        }
    }

    private fun loadData() {
        StickerModel.instance.getStorePackages("", pageIndex, pageSize) { code, msg, count, list ->
            if (pageIndex == 1) wkVBinding.refreshLayout.finishRefresh()
            if (code != HttpResponseCode.success.toInt()) {
                if (pageIndex > 1) {
                    pageIndex--
                    wkVBinding.refreshLayout.finishLoadMore(false)
                }
                showToast(msg)
                return@getStorePackages
            }
            totalCount = count
            if (pageIndex == 1) adapter.setList(list) else adapter.addData(list)
            val hasData = WKReader.isNotEmpty(adapter.data)
            wkVBinding.recyclerView.visibility = if (hasData) android.view.View.VISIBLE else android.view.View.GONE
            wkVBinding.noDataTv.visibility = if (hasData) android.view.View.GONE else android.view.View.VISIBLE
            val enableLoadMore = hasData && adapter.data.size < totalCount && list.isNotEmpty()
            if (pageIndex > 1) {
                if (enableLoadMore) wkVBinding.refreshLayout.finishLoadMore() else wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData()
            } else {
                wkVBinding.refreshLayout.resetNoMoreData()
            }
            wkVBinding.refreshLayout.setEnableLoadMore(enableLoadMore)
        }
    }
}
