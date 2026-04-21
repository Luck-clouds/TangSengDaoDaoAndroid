package com.chat.sticker.ui

import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerSortLayoutBinding
import com.chat.sticker.service.StickerModel
import com.chat.sticker.ui.adapter.StickerPackageAdapter
import java.util.Collections

/**
 * 表情包排序页
 * Created by Luckclouds .
 */
class StickerPackageSortActivity : WKBaseActivity<ActStickerSortLayoutBinding>() {
    private val adapter = StickerPackageAdapter()

    override fun getViewBinding(): ActStickerSortLayoutBinding = ActStickerSortLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.sticker_sort_title)
    }

    override fun getRightTvText(textView: TextView): String = getString(R.string.sticker_done)

    override fun rightLayoutClick() {
        val ids = adapter.data.map { it.packageId }
        StickerModel.instance.reorderMyPackages(ids) { code, msg ->
            if (code == HttpResponseCode.success.toInt()) {
                StickerPanelView.notifyDataChanged()
                finish()
            } else {
                showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
            }
        }
    }

    override fun initView() {
        adapter.showAction = false
        adapter.showSortHandle = true
        adapter.compactMode = true
        initAdapter(wkVBinding.recyclerView, adapter)
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                Collections.swap(adapter.data, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }
        ItemTouchHelper(callback).attachToRecyclerView(wkVBinding.recyclerView)
    }

    override fun initData() {
        StickerModel.instance.getMyPackages { code, msg, list ->
            if (code == HttpResponseCode.success.toInt()) {
                adapter.setList(list)
            } else {
                showToast(msg)
            }
        }
    }
}
