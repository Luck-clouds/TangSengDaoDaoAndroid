package com.chat.sticker.ui

import android.app.Activity
import android.widget.TextView
import com.chat.base.base.WKBaseActivity
import com.chat.base.glide.GlideUtils
import com.chat.base.net.HttpResponseCode
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.sticker.R
import com.chat.sticker.databinding.ActStickerPreviewLayoutBinding
import com.chat.sticker.service.StickerModel
import com.chat.sticker.utils.StickerTrace

/**
 * 自定义表情预览页
 * Created by Luckclouds .
 */
class StickerPreviewActivity : WKBaseActivity<ActStickerPreviewLayoutBinding>() {
    companion object {
        const val EXTRA_LOCAL_PATH = "local_path"
    }

    private var localPath: String = ""

    override fun getViewBinding(): ActStickerPreviewLayoutBinding = ActStickerPreviewLayoutBinding.inflate(layoutInflater)

    override fun setTitle(titleTv: TextView) {
        titleTv.setText(R.string.sticker_preview_title)
    }

    override fun getRightTvText(textView: TextView): String = getString(R.string.sticker_save)

    override fun rightLayoutClick() {
        StickerTrace.d("STICKER_TRACE_PREVIEW_SAVE click localPath=$localPath")
        showTitleRightLoading()
        StickerModel.instance.createCustom(localPath) { code, msg, _ ->
            hideTitleRightLoading()
            if (code == HttpResponseCode.success.toInt()) {
                StickerTrace.d("STICKER_TRACE_PREVIEW_SAVE success localPath=$localPath")
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                StickerTrace.e("STICKER_TRACE_PREVIEW_SAVE fail code=$code msg=$msg localPath=$localPath")
                showToast(msg.ifEmpty { getString(R.string.sticker_request_failed) })
            }
        }
    }

    override fun initView() {
        localPath = intent.getStringExtra(EXTRA_LOCAL_PATH).orEmpty()
        val isGif = WKFileUtils.getInstance().isGif(localPath) || localPath.endsWith(".gif", true)
        StickerTrace.d("STICKER_TRACE_PREVIEW_BIND localPath=$localPath isGif=$isGif")
        WKImageDisplayUtils.prepareImageSlot(wkVBinding.previewIv, 8f)
        if (isGif) {
            GlideUtils.getInstance().showGif(this, localPath, wkVBinding.previewIv, null)
        } else {
            GlideUtils.getInstance().showImg(this, localPath, wkVBinding.previewIv)
        }
    }
}
