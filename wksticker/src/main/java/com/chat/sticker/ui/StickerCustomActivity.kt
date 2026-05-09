package com.chat.sticker.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.chat.base.base.WKBaseActivity
import com.chat.base.glide.ChooseResult
import com.chat.base.glide.ChooseMimeType
import com.chat.base.glide.GlideUtils
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKPermissions
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerCustomLayoutBinding
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.service.StickerModel
import com.chat.sticker.ui.adapter.StickerGridAdapter
import com.chat.sticker.utils.StickerTrace

/**
 * 自定义表情管理页
 * Created by Luckclouds .
 */
class StickerCustomActivity : WKBaseActivity<ActStickerCustomLayoutBinding>() {
    companion object {
        private const val REQUEST_PREVIEW = 1001
    }

    private val adapter = StickerGridAdapter()
    private var editMode = false
    private var customItems = mutableListOf<StickerItem>()

    override fun getViewBinding(): ActStickerCustomLayoutBinding = ActStickerCustomLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.sticker_custom_title)
    }

    override fun getRightTvText(textView: TextView): String = getString(if (editMode) R.string.sticker_done else R.string.sticker_manage)

    override fun rightLayoutClick() {
        editMode = !editMode
        if (!editMode) {
            customItems = customItems.map { it.copy(selected = false) }.toMutableList()
        }
        StickerTrace.d("STICKER_TRACE_CUSTOM_TOGGLE_EDIT editMode=$editMode customCount=${customItems.size}")
        updateCustomDisplay()
        wkVBinding.bottomActionLayout.visibility = if (editMode) View.VISIBLE else View.GONE
        updateBottomActionState()
        findViewById<TextView>(com.chat.base.R.id.titleRightTv)?.text =
            getString(if (editMode) R.string.sticker_done else R.string.sticker_manage)
    }

    override fun initView() {
        adapter.showTitles = false
        wkVBinding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        wkVBinding.recyclerView.adapter = adapter
        updateBottomActionState()
    }

    override fun initListener() {
        adapter.setOnItemClickListener { _, _, position ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            if (item.isAddCell) {
                chooseImage()
                return@setOnItemClickListener
            }
            if (!editMode) return@setOnItemClickListener
            item.selected = !item.selected
            adapter.notifyItemChanged(position)
            customItems = adapter.data.filter { !it.isAddCell }.map { it.copy() }.toMutableList()
            updateBottomActionState()
        }
        wkVBinding.moveFrontTv.setOnClickListener {
            val selected = adapter.data.filter { !it.isAddCell && it.selected }
            if (selected.isEmpty()) return@setOnClickListener
            val sorted = mutableListOf<StickerItem>()
            sorted += selected.map { it.copy(selected = false) }
            sorted += adapter.data.filter { !it.isAddCell && !it.selected }.map { it.copy(selected = false) }
            customItems = sorted.toMutableList()
            adapter.setList(sorted)
            updateBottomActionState()
            reorderCustom()
        }
        wkVBinding.deleteTv.setOnClickListener {
            val ids = adapter.data.filter { !it.isAddCell && it.selected && it.customId.isNotEmpty() }.map { it.customId }
            if (ids.isEmpty()) return@setOnClickListener
            showDialog(getString(R.string.sticker_delete_confirm)) { index ->
                if (index != 1) return@showDialog
                StickerModel.instance.deleteCustom(ids) { code, msg ->
                    if (code == HttpResponseCode.success.toInt()) {
                        loadData()
                        StickerPanelView.notifyDataChanged()
                    } else {
                        showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
                    }
                }
            }
        }
    }

    override fun initData() {
        loadData()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PREVIEW && resultCode == Activity.RESULT_OK) {
            loadData()
            StickerPanelView.notifyDataChanged()
        }
    }

    private fun loadData() {
        StickerTrace.d("STICKER_TRACE_CUSTOM_LOAD start")
        StickerModel.instance.getCustom { code, msg, list ->
            if (code != HttpResponseCode.success.toInt()) {
                StickerTrace.e("STICKER_TRACE_CUSTOM_LOAD_FAIL code=$code msg=$msg")
                showToast(msg)
                return@getCustom
            }
            StickerTrace.d("STICKER_TRACE_CUSTOM_LOAD_SUCCESS count=${list.size} first=${StickerTrace.itemSummary(list.firstOrNull())}")
            customItems = list.map { it.copy(selected = false) }.toMutableList()
            updateCustomDisplay()
        }
    }

    private fun updateCustomDisplay() {
        val data = mutableListOf<StickerItem>()
        // 添加入口固定占第一位，避免在空列表或编辑态下入口消失。
        data += StickerItem(isAddCell = true)
        data += customItems
        adapter.editMode = editMode
        adapter.setList(data)
        wkVBinding.noDataTv.visibility = View.GONE
        StickerTrace.d("STICKER_TRACE_CUSTOM_RENDER editMode=$editMode customCount=${customItems.size} adapterCount=${data.size} firstIsAdd=${data.firstOrNull()?.isAddCell == true}")
        updateBottomActionState()
    }

    private fun updateBottomActionState() {
        val hasSelected = adapter.data.any { !it.isAddCell && it.selected }
        val moveColor = ContextCompat.getColor(this, com.chat.base.R.color.color999)
        val deleteColor = ContextCompat.getColor(this, com.chat.base.R.color.red)
        wkVBinding.moveFrontTv.isEnabled = hasSelected
        wkVBinding.deleteTv.isEnabled = hasSelected
        wkVBinding.moveFrontTv.setTextColor(moveColor)
        wkVBinding.deleteTv.setTextColor(deleteColor)
        wkVBinding.moveFrontTv.alpha = if (hasSelected) 1f else 0.35f
        wkVBinding.deleteTv.alpha = if (hasSelected) 1f else 0.35f
    }

    private fun reorderCustom() {
        val ids = adapter.data.filter { !it.isAddCell && it.customId.isNotEmpty() }.map { it.customId }
        StickerModel.instance.reorderCustom(ids) { code, msg ->
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
            } else {
                loadData()
                StickerPanelView.notifyDataChanged()
            }
        }
    }

    private fun chooseImage() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val desc = getString(com.chat.base.R.string.album_permissions_desc, getString(com.chat.base.R.string.app_name))
        WKPermissions.getInstance().checkPermissions(object : WKPermissions.IPermissionResult {
            override fun onResult(result: Boolean) {
                if (!result) return
                GlideUtils.getInstance().chooseIMG(this@StickerCustomActivity, 1, true, ChooseMimeType.img, false, false, object : GlideUtils.ISelectBack {
                    override fun onBack(paths: List<ChooseResult>) {
                        val path = paths.firstOrNull()?.path.orEmpty()
                        if (path.isEmpty()) return
                        val intent = Intent(this@StickerCustomActivity, StickerPreviewActivity::class.java)
                        intent.putExtra(StickerPreviewActivity.EXTRA_LOCAL_PATH, path)
                        startActivityForResult(intent, REQUEST_PREVIEW)
                    }

                    override fun onCancel() = Unit
                })
            }

            override fun clickResult(isCancel: Boolean) = Unit
        }, this, desc, *permissions)
    }
}
