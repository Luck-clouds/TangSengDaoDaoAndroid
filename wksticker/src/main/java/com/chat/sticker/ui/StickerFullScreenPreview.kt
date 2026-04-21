package com.chat.sticker.ui

import android.app.Dialog
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import com.chat.base.config.WKApiConfig
import com.chat.base.glide.GlideUtils
import com.chat.base.utils.WKImageDisplayUtils
import com.chat.sticker.databinding.DialogStickerFullPreviewBinding
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.utils.StickerTrace

object StickerFullScreenPreview {
    private var dialog: Dialog? = null
    private var binding: DialogStickerFullPreviewBinding? = null
    private var currentKey: String = ""
    private var previewContext: Context? = null
    private var moveResolver: ((Float, Float) -> StickerItem?)? = null
    private var switchedOnMove: Boolean = false

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun show(context: Context, item: StickerItem, moveResolver: ((Float, Float) -> StickerItem?)? = null) {
        dismiss()
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        this.dialog = dialog
        this.previewContext = context
        this.moveResolver = moveResolver
        this.switchedOnMove = false
        val binding = DialogStickerFullPreviewBinding.inflate(LayoutInflater.from(context))
        this.binding = binding
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
        var hasDown = false
        var allowTapDismiss = false
        binding.rootLayout.postDelayed({ allowTapDismiss = true }, 300)
        binding.rootLayout.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    hasDown = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleMove(event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (switchedOnMove || (allowTapDismiss && hasDown)) {
                        dismiss()
                    } else {
                        hasDown = false
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        WKImageDisplayUtils.prepareImageSlot(binding.previewIv, 8f)
        updatePreview(context, item)
        dialog.show()
    }

    fun handleMove(rawX: Float, rawY: Float) {
        val context = previewContext ?: return
        val nextItem = moveResolver?.invoke(rawX, rawY) ?: return
        if (itemKey(nextItem) != currentKey) {
            switchedOnMove = true
            binding?.rootLayout?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            updatePreview(context, nextItem)
        }
    }

    fun handleRelease(shouldDismissAfterSwitch: Boolean) {
        if (shouldDismissAfterSwitch && switchedOnMove) {
            dismiss()
        }
    }

    private fun updatePreview(context: Context, item: StickerItem) {
        val binding = binding ?: return
        currentKey = itemKey(item)
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
    }

    private fun itemKey(item: StickerItem): String {
        return item.itemId.ifEmpty { item.customId }.ifEmpty { item.gifUrl }.ifEmpty { item.originUrl }.ifEmpty { item.thumbUrl }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
        currentKey = ""
        previewContext = null
        moveResolver = null
        switchedOnMove = false
    }
}
