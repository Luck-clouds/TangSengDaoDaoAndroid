package com.chat.moments.ui

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ChooseContactsMenu
import com.chat.moments.R
import com.chat.moments.databinding.ActMomentVisibilityLayoutBinding
import com.chat.moments.entity.MomentAudienceSelection
import com.chat.moments.entity.MomentUserChoice
import com.chat.moments.entity.MomentVisibilityType
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType

class MomentVisibilityActivity : WKBaseActivity<ActMomentVisibilityLayoutBinding>() {

    companion object {
        const val EXTRA_SELECTION = "moment_visibility_selection"
        const val EXTRA_RESULT = "moment_visibility_result"
    }

    private var selection = MomentAudienceSelection()

    private val labelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val labels = result.data?.getParcelableArrayListExtra<com.chat.moments.entity.MomentTagChoice>(MomentLabelPickerActivity.EXTRA_RESULT)
        selection.tags.clear()
        if (labels != null) {
            selection.tags.addAll(labels)
        }
        renderSelectionSummary()
    }

    override fun getViewBinding(): ActMomentVisibilityLayoutBinding {
        return ActMomentVisibilityLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.moment_visibility_title)
    }

    override fun getRightTvText(textView: TextView): String {
        return getString(R.string.moment_visibility_done)
    }

    override fun initPresenter() {
        selection = intent.getParcelableExtra(EXTRA_SELECTION) ?: MomentAudienceSelection()
    }

    override fun initView() {
        when (selection.type) {
            MomentVisibilityType.PRIVATE -> wkVBinding.privateRb.isChecked = true
            MomentVisibilityType.PARTIAL_VISIBLE -> wkVBinding.partialRb.isChecked = true
            MomentVisibilityType.EXCLUDE_VISIBLE -> wkVBinding.excludeRb.isChecked = true
            else -> wkVBinding.publicRb.isChecked = true
        }
        renderSelectionState()
    }

    override fun initListener() {
        wkVBinding.visibilityGroup.setOnCheckedChangeListener { _, checkedId ->
            selection.type = when (checkedId) {
                R.id.privateRb -> MomentVisibilityType.PRIVATE
                R.id.partialRb -> MomentVisibilityType.PARTIAL_VISIBLE
                R.id.excludeRb -> MomentVisibilityType.EXCLUDE_VISIBLE
                else -> MomentVisibilityType.PUBLIC
            }
            renderSelectionState()
        }
        wkVBinding.selectContactsTv.setOnClickListener {
            EndpointManager.getInstance().invoke(
                "choose_contacts",
                ChooseContactsMenu(-1, true, false, selection.users.map { toChannel(it) }, object : ChooseContactsMenu.IChooseBack {
                    override fun onBack(selectedList: MutableList<WKChannel>?) {
                        selection.users.clear()
                        selectedList?.forEach { channel ->
                            selection.users.add(MomentUserChoice(channel.channelID, channel.channelRemark.ifEmpty { channel.channelName }, channel.avatar))
                        }
                        renderSelectionSummary()
                    }
                })
            )
        }
        wkVBinding.selectTagsTv.setOnClickListener {
            val intent = Intent(this, MomentLabelPickerActivity::class.java)
            intent.putParcelableArrayListExtra(MomentLabelPickerActivity.EXTRA_SELECTED, ArrayList(selection.tags))
            labelLauncher.launch(intent)
        }
    }

    override fun rightLayoutClick() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, selection))
        finish()
    }

    private fun renderSelectionState() {
        val showSelection = selection.type == MomentVisibilityType.PARTIAL_VISIBLE || selection.type == MomentVisibilityType.EXCLUDE_VISIBLE
        wkVBinding.selectionLayout.visibility = if (showSelection) View.VISIBLE else View.GONE
        renderSelectionSummary()
    }

    private fun renderSelectionSummary() {
        val summary = selection.buildSelectionSummary()
        wkVBinding.selectionSummaryTv.text = summary
        wkVBinding.selectionSummaryTv.visibility = if (summary.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toChannel(choice: MomentUserChoice): WKChannel {
        val channel = WKChannel()
        channel.channelID = choice.uid
        channel.channelType = WKChannelType.PERSONAL
        channel.channelName = choice.name
        channel.avatar = choice.avatar
        return channel
    }
}
