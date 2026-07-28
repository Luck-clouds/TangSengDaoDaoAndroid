package com.qinghangim.app

import android.content.Intent
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.WKDialogUtils
import com.chat.login.ui.PerfectUserInfoActivity
import com.chat.login.ui.WKLoginActivity
import com.chat.uikit.TabActivity
import com.qinghangim.app.R
import com.qinghangim.app.databinding.ActivityMainBinding
import com.xinbida.wukongim.WKIM

class MainActivity : WKBaseActivity<ActivityMainBinding>() {

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        val isShowDialog: Boolean =
            WKSharedPreferencesUtil.getInstance().getBoolean("show_agreement_dialog")
        if (isShowDialog) {
            showDialog()
        } else gotoApp()
    }

    private fun gotoApp() {
        if (!TextUtils.isEmpty(WKConfig.getInstance().token)) {
            if (TextUtils.isEmpty(WKConfig.getInstance().userInfo.name)) {
                startActivity(Intent(this@MainActivity, PerfectUserInfoActivity::class.java))
            } else {
                val publicRSAKey: String =
                    WKIM.getInstance().cmdManager.rsaPublicKey
                if (TextUtils.isEmpty(publicRSAKey)) {
                    val intent = Intent(this@MainActivity, WKLoginActivity::class.java)
                    intent.putExtra("from", getIntent().getIntExtra("from", 0))
                    startActivity(intent)
                } else {
                    startActivity(Intent(this@MainActivity, TabActivity::class.java))
                }
            }
        } else {
            val intent = Intent(this@MainActivity, WKLoginActivity::class.java)
            intent.putExtra("from", getIntent().getIntExtra("from", 0))
            startActivity(intent)
        }
        finish()
    }

    private fun showDialog() {
        val content = getString(R.string.dialog_content)
        val linkSpan = SpannableStringBuilder(content)
        addAgreementLinkSpan(
            linkSpan,
            content,
            getString(R.string.main_user_agreement),
            WKApiConfig.baseWebUrl + "user_agreement.html"
        )
        addAgreementLinkSpan(
            linkSpan,
            content,
            getString(R.string.main_privacy_policy),
            WKApiConfig.baseWebUrl + "privacy_policy.html"
        )

        WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.dialog_title),
            linkSpan,
            false,
            getString(R.string.disagree),
            getString(R.string.agree),
            0,
            0
        ) { index ->
            if (index == 1) {
                WKSharedPreferencesUtil.getInstance()
                    .putBoolean("show_agreement_dialog", false)
                (application as TSApplication).initializeAfterPrivacyConsent()
                gotoApp()
            } else {
                finish()
            }
        }
    }

    private fun addAgreementLinkSpan(
        linkSpan: SpannableStringBuilder,
        content: String,
        linkText: String,
        url: String
    ) {
        val start = content.indexOf(linkText)
        if (start < 0) {
            Log.e(TAG, "Agreement link text was not found in dialog content: $linkText")
            return
        }
        linkSpan.setSpan(
            NormalClickableSpan(
                true,
                ContextCompat.getColor(this, R.color.blue),
                NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""),
                object : NormalClickableSpan.IClick {
                    override fun onClick(view: View) {
                        showWebView(url)
                    }
                }
            ),
            start,
            start + linkText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
