package com.chat.moments.ui

import android.widget.TextView
import com.chat.base.base.WKBaseActivity
import com.chat.base.net.HttpResponseCode
import com.chat.moments.R
import com.chat.moments.databinding.ActMomentUserStateLayoutBinding
import com.chat.moments.service.MomentModel

class MomentUserStateActivity : WKBaseActivity<ActMomentUserStateLayoutBinding>() {

    companion object {
        const val EXTRA_UID = "moment_state_uid"
    }

    private var uid: String = ""
    private var ignoreSwitchChange = false
    private var isUpdating = false

    override fun getViewBinding(): ActMomentUserStateLayoutBinding {
        return ActMomentUserStateLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.moment_status_title)
    }

    override fun initPresenter() {
        uid = intent.getStringExtra(EXTRA_UID).orEmpty()
    }

    override fun initView() {
        setSwitchEnabled(false)
    }

    override fun initData() {
        if (uid.isEmpty()) {
            showToast(R.string.moment_state_load_failed)
            finish()
            return
        }
        loadState()
    }

    override fun initListener() {
        wkVBinding.blockMeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSwitchChange || isUpdating) return@setOnCheckedChangeListener
            updateHideMyMoment(isChecked)
        }
        wkVBinding.hideHimSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreSwitchChange || isUpdating) return@setOnCheckedChangeListener
            updateHideHisMoment(isChecked)
        }
    }

    private fun loadState() {
        isUpdating = true
        setSwitchEnabled(false)
        MomentModel.instance.getMomentUserState(uid) { code, msg, setting ->
            isUpdating = false
            if (code != HttpResponseCode.success.toInt()) {
                showToast(msg.ifEmpty { getString(R.string.moment_state_load_failed) })
                setSwitchEnabled(true)
                return@getMomentUserState
            }
            ignoreSwitchChange = true
            wkVBinding.blockMeSwitch.isChecked = setting.hideMyMoment
            wkVBinding.hideHimSwitch.isChecked = setting.hideHisMoment
            ignoreSwitchChange = false
            setSwitchEnabled(true)
        }
    }

    private fun updateHideMyMoment(enabled: Boolean) {
        isUpdating = true
        setSwitchEnabled(false)
        MomentModel.instance.updateHideMyMoment(uid, enabled) { code, msg ->
            isUpdating = false
            if (code != HttpResponseCode.success.toInt()) {
                rollbackBlockMe(!enabled)
                showToast(msg.ifEmpty { getString(R.string.moment_state_save_failed) })
            }
            setSwitchEnabled(true)
        }
    }

    private fun updateHideHisMoment(enabled: Boolean) {
        isUpdating = true
        setSwitchEnabled(false)
        MomentModel.instance.updateHideHisMoment(uid, enabled) { code, msg ->
            isUpdating = false
            if (code != HttpResponseCode.success.toInt()) {
                rollbackHideHim(!enabled)
                showToast(msg.ifEmpty { getString(R.string.moment_state_save_failed) })
            }
            setSwitchEnabled(true)
        }
    }

    private fun rollbackBlockMe(oldValue: Boolean) {
        ignoreSwitchChange = true
        wkVBinding.blockMeSwitch.isChecked = oldValue
        ignoreSwitchChange = false
    }

    private fun rollbackHideHim(oldValue: Boolean) {
        ignoreSwitchChange = true
        wkVBinding.hideHimSwitch.isChecked = oldValue
        ignoreSwitchChange = false
    }

    private fun setSwitchEnabled(enabled: Boolean) {
        wkVBinding.blockMeSwitch.isEnabled = enabled
        wkVBinding.hideHimSwitch.isEnabled = enabled
    }
}
