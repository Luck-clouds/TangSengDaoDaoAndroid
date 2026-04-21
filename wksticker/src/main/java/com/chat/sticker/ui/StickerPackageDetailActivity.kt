package com.chat.sticker.ui

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.net.HttpResponseCode
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerPackageDetailLayoutBinding
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.entity.StickerPackage
import com.chat.sticker.service.StickerModel
import com.chat.sticker.ui.adapter.StickerGridAdapter

/**
 * 表情包详情页
 * Created by Luckclouds .
 */
class StickerPackageDetailActivity : WKBaseActivity<ActStickerPackageDetailLayoutBinding>() {
    companion object {
        const val EXTRA_PACKAGE_ID = "package_id"
        const val EXTRA_IS_ADDED = "is_added"
    }

    private val adapter = StickerGridAdapter()
    private var packageId: String = ""
    private var isAdded: Boolean = false
    private var hasAddedStateExtra: Boolean = false
    private var stickerPackage: StickerPackage = StickerPackage()

    override fun getViewBinding(): ActStickerPackageDetailLayoutBinding = ActStickerPackageDetailLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.text = getString(R.string.sticker_title)
    }

    override fun initView() {
        packageId = intent.getStringExtra(EXTRA_PACKAGE_ID).orEmpty()
        hasAddedStateExtra = intent.hasExtra(EXTRA_IS_ADDED)
        isAdded = intent.getBooleanExtra(EXTRA_IS_ADDED, false)
        wkVBinding.recyclerView.layoutManager = GridLayoutManager(this, 5)
        adapter.showTitles = false
        adapter.previewMoveResolver = ::findStickerItemUnder
        wkVBinding.recyclerView.adapter = adapter
        attachPreviewTouchListener()
        refreshAction()
    }

    override fun initListener() {
        wkVBinding.actionTv.setOnClickListener {
            if (packageId.isEmpty() || isAdded) return@setOnClickListener
            val callback: (Int, String) -> Unit = { code, msg ->
                if (code == HttpResponseCode.success.toInt()) {
                    isAdded = true
                    refreshAction()
                    StickerPanelView.notifyDataChanged()
                } else {
                    showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
                }
            }
            StickerModel.instance.addMyPackage(packageId, callback)
        }
    }

    override fun initData() {
        if (packageId.isEmpty()) return
        StickerModel.instance.getPackageDetail(packageId) { code, msg, pkg, items ->
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg)
                return@getPackageDetail
            }
            stickerPackage = pkg
            if (!hasAddedStateExtra || pkg.isAdded) {
                isAdded = pkg.isAdded
                refreshAction()
            }
            wkVBinding.nameTv.text = pkg.name
            wkVBinding.descTv.text = if (pkg.description.isNotEmpty()) pkg.description else pkg.tags
            GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(if (pkg.cover.isNotEmpty()) pkg.cover else pkg.icon), wkVBinding.coverIv)
            adapter.setList(items)
            wkVBinding.noDataTv.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            refreshAddedStateFromMyPackages()
        }
    }

    private fun refreshAddedStateFromMyPackages() {
        if (packageId.isEmpty()) return
        StickerModel.instance.getMyPackages { code, _, list ->
            if (code != HttpResponseCode.success.toInt()) return@getMyPackages
            val exists = list.any { it.packageId == packageId }
            if (exists != isAdded) {
                isAdded = exists
                refreshAction()
            }
        }
    }

    private fun refreshAction() {
        wkVBinding.actionTv.text = getString(if (isAdded) R.string.sticker_already_added else R.string.sticker_add)
        wkVBinding.actionTv.isEnabled = !isAdded
        wkVBinding.actionTv.alpha = if (isAdded) 0.55f else 1f
        wkVBinding.actionTv.setBackgroundResource(R.drawable.bg_sticker_action)
        wkVBinding.actionTv.setTextColor(ContextCompat.getColor(this, com.chat.base.R.color.white))
    }

    private fun attachPreviewTouchListener() {
        wkVBinding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                if (!StickerFullScreenPreview.isShowing()) return false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> StickerFullScreenPreview.handleMove(e.rawX, e.rawY)
                    android.view.MotionEvent.ACTION_UP -> StickerFullScreenPreview.handleRelease(true)
                    android.view.MotionEvent.ACTION_CANCEL -> StickerFullScreenPreview.handleRelease(false)
                }
                return true
            }

            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {
                if (!StickerFullScreenPreview.isShowing()) return
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> StickerFullScreenPreview.handleMove(e.rawX, e.rawY)
                    android.view.MotionEvent.ACTION_UP -> StickerFullScreenPreview.handleRelease(true)
                    android.view.MotionEvent.ACTION_CANCEL -> StickerFullScreenPreview.handleRelease(false)
                }
            }
        })
    }

    private fun findStickerItemUnder(rawX: Float, rawY: Float): StickerItem? {
        val location = IntArray(2)
        wkVBinding.recyclerView.getLocationOnScreen(location)
        val child = wkVBinding.recyclerView.findChildViewUnder(rawX - location[0], rawY - location[1]) ?: return null
        val position = wkVBinding.recyclerView.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION) return null
        val item = adapter.getItem(position) ?: return null
        return item.takeUnless { it.isSectionHeader || it.isAddCell }
    }
}
