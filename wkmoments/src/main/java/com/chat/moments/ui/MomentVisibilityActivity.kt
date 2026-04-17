package com.chat.moments.ui

import android.content.Intent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.ChooseContactsMenu
import com.chat.base.utils.WKLogUtils
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
    private val selectedTagIds = arrayListOf<String>()
    private var currentExpandedType: String? = null

    private val labelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val labels = result.data?.getParcelableArrayListExtra<com.chat.moments.entity.MomentTagChoice>(MomentLabelPickerActivity.EXTRA_RESULT)
        WKLogUtils.d(
            "MomentVisibility",
            "label result received labels=${labels?.map { "${it.id}:${it.name}" }} beforeSavedIds=$selectedTagIds"
        )
        selection.tags.clear()
        if (labels != null) {
            selection.tags.addAll(labels)
        }
        selectedTagIds.clear()
        selectedTagIds.addAll(selection.tags.map { it.id })
        WKLogUtils.d(
            "MomentVisibility",
            "label result applied selectionTags=${selection.tags.map { it.id }} savedIds=$selectedTagIds"
        )
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
        selectedTagIds.clear()
        selectedTagIds.addAll(selection.tags.map { it.id })
        WKLogUtils.d(
            "MomentVisibility",
            "initPresenter type=${selection.type} userCount=${selection.users.size} tagIds=$selectedTagIds"
        )
    }

    override fun initView() {
        when (selection.type) {
            MomentVisibilityType.PRIVATE -> wkVBinding.privateRb.isChecked = true
            MomentVisibilityType.PARTIAL_VISIBLE -> wkVBinding.partialRb.isChecked = true
            MomentVisibilityType.EXCLUDE_VISIBLE -> wkVBinding.excludeRb.isChecked = true
            else -> wkVBinding.publicRb.isChecked = true
        }
        renderSelectionState(false)
    }

    override fun initListener() {
        wkVBinding.visibilityGroup.setOnCheckedChangeListener { _, checkedId ->
            selection.type = when (checkedId) {
                R.id.privateRb -> MomentVisibilityType.PRIVATE
                R.id.partialRb -> MomentVisibilityType.PARTIAL_VISIBLE
                R.id.excludeRb -> MomentVisibilityType.EXCLUDE_VISIBLE
                else -> MomentVisibilityType.PUBLIC
            }
            renderSelectionState(true)
        }
        wkVBinding.partialSelectContactsLayout.setOnClickListener { openContactsChooser() }
        wkVBinding.excludeSelectContactsLayout.setOnClickListener { openContactsChooser() }
        wkVBinding.partialSelectTagsLayout.setOnClickListener { openTagPicker() }
        wkVBinding.excludeSelectTagsLayout.setOnClickListener { openTagPicker() }
    }

    override fun rightLayoutClick() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, selection))
        finish()
    }

    private fun renderSelectionState(animated: Boolean) {
        when (selection.type) {
            MomentVisibilityType.PARTIAL_VISIBLE -> {
                showSelectionMenu(wkVBinding.partialSelectionLayout, MomentVisibilityType.PARTIAL_VISIBLE, animated)
                hideSelectionMenu(wkVBinding.excludeSelectionLayout, MomentVisibilityType.EXCLUDE_VISIBLE, animated)
            }

            MomentVisibilityType.EXCLUDE_VISIBLE -> {
                hideSelectionMenu(wkVBinding.partialSelectionLayout, MomentVisibilityType.PARTIAL_VISIBLE, animated)
                showSelectionMenu(wkVBinding.excludeSelectionLayout, MomentVisibilityType.EXCLUDE_VISIBLE, animated)
            }

            else -> {
                hideSelectionMenu(wkVBinding.partialSelectionLayout, MomentVisibilityType.PARTIAL_VISIBLE, animated)
                hideSelectionMenu(wkVBinding.excludeSelectionLayout, MomentVisibilityType.EXCLUDE_VISIBLE, animated)
            }
        }
        renderSelectionSummary()
    }

    private fun showSelectionMenu(target: View, type: String, animated: Boolean) {
        if (currentExpandedType == type && target.visibility == View.VISIBLE) return
        currentExpandedType = type
        target.animate().cancel()
        if (!animated) {
            target.visibility = View.VISIBLE
            target.alpha = 1f
            target.scaleY = 1f
            target.translationY = 0f
            return
        }
        target.apply {
            pivotY = 0f
            visibility = View.VISIBLE
            alpha = 0f
            scaleY = 0.82f
            translationY = -12f * resources.displayMetrics.density
            animate()
                .alpha(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun hideSelectionMenu(target: View, type: String, animated: Boolean) {
        if (currentExpandedType == type) currentExpandedType = null
        if (target.visibility != View.VISIBLE) return
        target.animate().cancel()
        if (!animated) {
            target.visibility = View.GONE
            target.alpha = 1f
            target.scaleY = 1f
            target.translationY = 0f
            return
        }
        target.apply {
            pivotY = 0f
            animate()
                .alpha(0f)
                .scaleY(0.82f)
                .translationY(-12f * resources.displayMetrics.density)
                .setDuration(180L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    visibility = View.GONE
                    alpha = 1f
                    scaleY = 1f
                    translationY = 0f
                }
                .start()
        }
    }

    private fun renderSelectionSummary() {
        val usersSummary = if (selection.users.isEmpty()) "" else getString(R.string.moment_selected_users, selection.users.size)
        val tagCount = if (selection.tags.isNotEmpty()) selection.tags.size else selectedTagIds.size
        val tagsSummary = if (tagCount <= 0) "" else getString(R.string.moment_selected_tags, tagCount)
        WKLogUtils.d(
            "MomentVisibility",
            "renderSelectionSummary users=${selection.users.map { it.uid }} selectionTags=${selection.tags.map { it.id }} savedIds=$selectedTagIds tagCount=$tagCount"
        )
        wkVBinding.partialSelectContactsValueTv.text = usersSummary
        wkVBinding.excludeSelectContactsValueTv.text = usersSummary
        wkVBinding.partialSelectContactsValueTv.visibility = if (usersSummary.isEmpty()) View.GONE else View.VISIBLE
        wkVBinding.excludeSelectContactsValueTv.visibility = if (usersSummary.isEmpty()) View.GONE else View.VISIBLE
        wkVBinding.partialSelectTagsValueTv.text = tagsSummary
        wkVBinding.excludeSelectTagsValueTv.text = tagsSummary
        wkVBinding.partialSelectTagsValueTv.visibility = if (tagsSummary.isEmpty()) View.GONE else View.VISIBLE
        wkVBinding.excludeSelectTagsValueTv.visibility = if (tagsSummary.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openContactsChooser() {
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

    private fun openTagPicker() {
        WKLogUtils.d(
            "MomentVisibility",
            "open label picker savedIds=$selectedTagIds selectionTags=${selection.tags.map { "${it.id}:${it.name}" }}"
        )
        val intent = Intent(this, MomentLabelPickerActivity::class.java)
        intent.putParcelableArrayListExtra(MomentLabelPickerActivity.EXTRA_SELECTED, ArrayList(selection.tags))
        intent.putStringArrayListExtra(MomentLabelPickerActivity.EXTRA_SELECTED_IDS, ArrayList(selectedTagIds))
        labelLauncher.launch(intent)
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
