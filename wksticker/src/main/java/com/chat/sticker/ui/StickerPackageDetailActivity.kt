package com.chat.sticker.ui

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.net.HttpResponseCode
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerPackageDetailLayoutBinding
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
    private var stickerPackage: StickerPackage = StickerPackage()

    override fun getViewBinding(): ActStickerPackageDetailLayoutBinding = ActStickerPackageDetailLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.text = getString(R.string.sticker_title)
    }

    override fun initView() {
        packageId = intent.getStringExtra(EXTRA_PACKAGE_ID).orEmpty()
        isAdded = intent.getBooleanExtra(EXTRA_IS_ADDED, false)
        wkVBinding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        wkVBinding.recyclerView.adapter = adapter
        refreshAction()
    }

    override fun initListener() {
        wkVBinding.actionTv.setOnClickListener {
            if (packageId.isEmpty()) return@setOnClickListener
            val callback: (Int, String) -> Unit = { code, msg ->
                if (code == HttpResponseCode.success.toInt()) {
                    isAdded = !isAdded
                    refreshAction()
                    StickerPanelView.notifyDataChanged()
                } else {
                    showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
                }
            }
            if (isAdded) StickerModel.instance.removeMyPackage(packageId, callback) else StickerModel.instance.addMyPackage(packageId, callback)
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
            wkVBinding.nameTv.text = pkg.name
            wkVBinding.descTv.text = if (pkg.description.isNotEmpty()) pkg.description else pkg.tags
            GlideUtils.getInstance().showImg(this, WKApiConfig.getShowUrl(if (pkg.cover.isNotEmpty()) pkg.cover else pkg.icon), wkVBinding.coverIv)
            adapter.setList(items)
            wkVBinding.noDataTv.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun refreshAction() {
        wkVBinding.actionTv.text = getString(if (isAdded) R.string.sticker_remove else R.string.sticker_add)
        wkVBinding.actionTv.setBackgroundResource(if (isAdded) R.drawable.bg_sticker_action_remove else R.drawable.bg_sticker_action)
        wkVBinding.actionTv.setTextColor(
            ContextCompat.getColor(this, if (isAdded) com.chat.base.R.color.red else com.chat.base.R.color.white)
        )
    }
}
