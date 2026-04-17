package com.chat.moments.ui

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.chat.base.base.WKBaseActivity
import com.chat.base.utils.WKLogUtils
import com.chat.moments.databinding.ActMomentLabelPickerLayoutBinding
import com.chat.moments.entity.MomentTagChoice
import com.chat.moments.ui.adapter.MomentLabelAdapter
import com.chat.moments.ui.adapter.MomentLabelItem
import com.chat.uikit.contacts.label.LabelEntity
import com.chat.uikit.contacts.label.LabelModel

class MomentLabelPickerActivity : WKBaseActivity<ActMomentLabelPickerLayoutBinding>() {

    companion object {
        const val EXTRA_SELECTED = "moment_label_selected"
        const val EXTRA_SELECTED_IDS = "moment_label_selected_ids"
        const val EXTRA_RESULT = "moment_label_result"
    }

    private val adapter = MomentLabelAdapter()
    private var selected = arrayListOf<MomentTagChoice>()
    private var selectedIds = hashSetOf<String>()

    override fun getViewBinding(): ActMomentLabelPickerLayoutBinding {
        return ActMomentLabelPickerLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(com.chat.moments.R.string.moment_select_tags)
    }

    override fun getRightTvText(textView: TextView): String {
        return getString(com.chat.moments.R.string.moment_visibility_done)
    }

    override fun initPresenter() {
        selected = intent.getParcelableArrayListExtra(EXTRA_SELECTED) ?: arrayListOf()
        selectedIds.clear()
        selectedIds.addAll(selected.map { it.id })
        selectedIds.addAll(intent.getStringArrayListExtra(EXTRA_SELECTED_IDS) ?: arrayListOf())
        WKLogUtils.d(
            "MomentLabelPicker",
            "initPresenter selected=${selected.map { "${it.id}:${it.name}" }} selectedIds=$selectedIds"
        )
    }

    override fun initView() {
        wkVBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        wkVBinding.recyclerView.adapter = adapter
    }

    override fun initData() {
        LabelModel.getInstance().getLabels(true) { _, _, list ->
            WKLogUtils.d(
                "MomentLabelPicker",
                "getLabels size=${list.size} serverIds=${list.map { "${it.id}:${it.name}" }} selectedIds=$selectedIds"
            )
            val items = list.map { label ->
                val choice = toChoice(label)
                val checked = selectedIds.contains(choice.id)
                WKLogUtils.d(
                    "MomentLabelPicker",
                    "bind label id=${choice.id} name=${choice.name} checked=$checked"
                )
                MomentLabelItem(choice, checked)
            }
            adapter.setList(items)
            wkVBinding.noDataTv.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun rightLayoutClick() {
        val result = ArrayList(adapter.data.filter { it.selected }.map { it.label })
        selectedIds.clear()
        selectedIds.addAll(result.map { it.id })
        WKLogUtils.d(
            "MomentLabelPicker",
            "rightLayoutClick result=${result.map { "${it.id}:${it.name}" }} savedIds=$selectedIds"
        )
        setResult(RESULT_OK, Intent().putParcelableArrayListExtra(EXTRA_RESULT, result))
        finish()
    }

    private fun toChoice(label: LabelEntity): MomentTagChoice {
        return MomentTagChoice(
            id = label.id,
            name = label.name,
            memberNames = ArrayList(label.members.map { it.channelRemark.ifEmpty { it.channelName } })
        )
    }
}
