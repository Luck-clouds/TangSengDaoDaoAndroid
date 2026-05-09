package com.chat.sticker.ui

import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKReader
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerStoreLayoutBinding
import com.chat.sticker.service.StickerModel
import com.chat.sticker.ui.adapter.StickerPackageAdapter
import com.chat.sticker.utils.StickerTrace
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

    override fun getRightIvResourceId(imageView: ImageView): Int {
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val padding = AndroidUtilities.dp(2f)
        imageView.setPadding(padding, padding, padding, padding)
        return R.mipmap.sticker_settings_icon
    }

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
            StickerTrace.d("STICKER_TRACE_STORE_ACTION click packageId=${item.packageId} isAdded=${item.isAdded} position=$position")
            val callback: (Int, String) -> Unit = { code, msg ->
                StickerTrace.d("STICKER_TRACE_STORE_ACTION_RESULT packageId=${item.packageId} beforeToggle=${item.isAdded} code=$code msg=$msg")
                if (code == HttpResponseCode.success.toInt()) {
                    item.isAdded = !item.isAdded
                    StickerTrace.d("STICKER_TRACE_STORE_ACTION_SUCCESS packageId=${item.packageId} afterToggle=${item.isAdded} notifyPanel=true")
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
        StickerTrace.d("STICKER_TRACE_STORE_LOAD pageIndex=$pageIndex pageSize=$pageSize adapterCount=${adapter.data.size}")
        StickerModel.instance.getStorePackages("", pageIndex, pageSize) { code, msg, count, list ->
            if (pageIndex == 1) wkVBinding.refreshLayout.finishRefresh()
            if (code != HttpResponseCode.success.toInt()) {
                StickerTrace.e("STICKER_TRACE_STORE_LOAD_FAIL pageIndex=$pageIndex code=$code msg=$msg")
                if (pageIndex > 1) {
                    pageIndex--
                    wkVBinding.refreshLayout.finishLoadMore(false)
                }
                showToast(msg)
                return@getStorePackages
            }
            StickerTrace.d("STICKER_TRACE_STORE_LOAD_SUCCESS pageIndex=$pageIndex totalCount=$count listSize=${list.size} firstPackage=${list.firstOrNull()?.packageId.orEmpty()}")
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
