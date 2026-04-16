package com.chat.moments.ui

import android.content.Intent
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ChooseContactsMenu
import com.chat.moments.R
import com.chat.moments.databinding.ActMomentMentionLayoutBinding
import com.chat.moments.entity.MomentAudienceSelection
import com.chat.moments.entity.MomentUserChoice
import com.chat.moments.util.MomentUiUtils
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType

class MomentMentionActivity : WKBaseActivity<ActMomentMentionLayoutBinding>() {

    companion object {
        const val EXTRA_SELECTION = "moment_mention_selection"
        const val EXTRA_RESULT = "moment_mention_result"
    }

    private var selection = MomentAudienceSelection()

    private val labelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val labels = result.data?.getParcelableArrayListExtra<com.chat.moments.entity.MomentTagChoice>(MomentLabelPickerActivity.EXTRA_RESULT)
        selection.tags.clear()
        if (labels != null) {
            selection.tags.addAll(labels)
        }
        renderSummary()
    }

    override fun getViewBinding(): ActMomentMentionLayoutBinding {
        return ActMomentMentionLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.moment_mention_title)
    }

    override fun getRightTvText(textView: TextView): String {
        return getString(R.string.moment_visibility_done)
    }

    override fun initPresenter() {
        selection = intent.getParcelableExtra(EXTRA_SELECTION) ?: MomentAudienceSelection()
    }

    override fun initView() {
        renderSummary()
    }

    override fun initListener() {
        wkVBinding.selectContactsTv.setOnClickListener {
            EndpointManager.getInstance().invoke(
                "choose_contacts",
                ChooseContactsMenu(-1, true, false, selection.users.map { toChannel(it) }, object : ChooseContactsMenu.IChooseBack {
                    override fun onBack(selectedList: MutableList<WKChannel>?) {
                        selection.users.clear()
                        selectedList?.forEach { channel ->
                            selection.users.add(MomentUserChoice(channel.channelID, channel.channelRemark.ifEmpty { channel.channelName }, channel.avatar))
                        }
                        renderSummary()
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

    private fun renderSummary() {
        val summary = MomentUiUtils.mentionsSummary(selection.users, selection.tags)
        wkVBinding.selectionSummaryTv.text = summary
        wkVBinding.selectionSummaryTv.visibility = if (summary.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
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
