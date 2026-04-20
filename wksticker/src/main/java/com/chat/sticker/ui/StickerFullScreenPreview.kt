package com.chat.sticker.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.sticker.databinding.DialogStickerFullPreviewBinding
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.utils.StickerTrace

object StickerFullScreenPreview {
    private var dialog: Dialog? = null

    fun show(context: Context, item: StickerItem) {
        dismiss()
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        this.dialog = dialog
        val binding = DialogStickerFullPreviewBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
        binding.rootLayout.setOnClickListener { dismiss() }
        binding.previewIv.setOnClickListener { dismiss() }
        WKImageDisplayUtils.prepareImageSlot(binding.previewIv, 8f)

        val previewUrl = when {
            item.gifUrl.isNotEmpty() -> WKApiConfig.getShowUrl(item.gifUrl)
            item.originUrl.isNotEmpty() && (item.originExt.contains("gif", true) || item.sourceMediaType.contains("gif", true) || item.originUrl.contains(".gif", true)) -> WKApiConfig.getShowUrl(item.originUrl)
            item.thumbUrl.isNotEmpty() -> WKApiConfig.getShowUrl(item.thumbUrl)
            else -> WKApiConfig.getShowUrl(item.originUrl)
        }
        val useGif = item.gifUrl.isNotEmpty()
            || item.sourceMediaType.contains("gif", true)
            || item.originExt.contains("gif", true)
            || previewUrl.contains(".gif", true)

        StickerTrace.d("STICKER_TRACE_FULLSCREEN_PREVIEW url=$previewUrl useGif=$useGif ${StickerTrace.itemSummary(item)}")
        if (useGif) {
            GlideUtils.getInstance().showGif(context, previewUrl, binding.previewIv, null)
        } else {
            GlideUtils.getInstance().showImg(context, previewUrl, binding.previewIv)
        }
        dialog.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
