package com.chat.sticker.ui

import android.content.Intent
import android.widget.TextView
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKReader
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerMyLayoutBinding
import com.chat.sticker.service.StickerModel
import com.chat.sticker.ui.adapter.MyStickerEntry
import com.chat.sticker.ui.adapter.MyStickerEntryAdapter

/**
 * 我的表情页
 * Created by Luckclouds .
 */
class StickerMyStickersActivity : WKBaseActivity<ActStickerMyLayoutBinding>() {
    private val adapter = MyStickerEntryAdapter()

    override fun getViewBinding(): ActStickerMyLayoutBinding = ActStickerMyLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.sticker_my_title)
    }

    override fun getRightTvText(textView: TextView): String = getString(R.string.sticker_my_package_sort)

    override fun rightLayoutClick() {
        startActivity(Intent(this, StickerPackageSortActivity::class.java))
    }

    override fun initView() {
        initAdapter(wkVBinding.recyclerView, adapter)
        adapter.addChildClickViewIds(R.id.actionTv)
    }

    override fun initListener() {
        adapter.setOnItemClickListener { _, _, position ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            when (item.type) {
                "custom" -> startActivity(Intent(this, StickerCustomActivity::class.java))
                "package" -> {
                    val intent = Intent(this, StickerPackageDetailActivity::class.java)
                    intent.putExtra(StickerPackageDetailActivity.EXTRA_PACKAGE_ID, item.id)
                    intent.putExtra(StickerPackageDetailActivity.EXTRA_IS_ADDED, true)
                    startActivity(intent)
                }
            }
        }
        adapter.setOnItemChildClickListener { _, view, position ->
            if (view.id != R.id.actionTv) return@setOnItemChildClickListener
            val item = adapter.getItem(position) ?: return@setOnItemChildClickListener
            if (item.type != "package") return@setOnItemChildClickListener
            StickerModel.instance.removeMyPackage(item.id) { code, msg ->
                if (code == HttpResponseCode.success.toInt()) {
                    loadData()
                    StickerPanelView.notifyDataChanged()
                } else {
                    showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
                }
            }
        }
    }

    override fun initData() {
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        StickerModel.instance.getMyPackages { code, msg, list ->
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg)
                return@getMyPackages
            }
            val data = mutableListOf<MyStickerEntry>()
            data += MyStickerEntry("custom", "custom", getString(R.string.sticker_custom_title), getString(R.string.sticker_add_custom), "", false)
            data += MyStickerEntry("section", "panel_packages", getString(R.string.sticker_panel_packages_title), "", "", false)
            list.forEach {
                data += MyStickerEntry("package", it.packageId, it.name, if (it.description.isNotEmpty()) it.description else it.tags, if (it.icon.isNotEmpty()) it.icon else it.cover, true)
            }
            adapter.setList(data)
            val hasData = WKReader.isNotEmpty(data)
            wkVBinding.noDataTv.visibility = if (hasData) android.view.View.GONE else android.view.View.VISIBLE
        }
    }
}
