package com.chat.uikit.chat.provider

import android.content.Intent
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.chat.base.msgitem.WKChatBaseProvider
import com.chat.base.msgitem.WKChatIteMsgFromType
import com.chat.base.msgitem.WKContentType
import com.chat.base.msgitem.WKUIChatMsgItemEntity
import com.chat.base.views.BubbleLayout
import com.chat.uikit.R
import com.chat.uikit.chat.file.FileDetailActivity
import com.chat.uikit.chat.file.FileMessageUtils
import com.chat.uikit.chat.msgmodel.WKFileContent

class WKFileProvider : WKChatBaseProvider() {
    override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_file, parentView, false)
    }

    override fun setData(
        adapterPosition: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        val contentLayout = parentView.findViewById<LinearLayout>(R.id.contentLayout)
        val bubbleLayout = parentView.findViewById<BubbleLayout>(R.id.fileBubbleLayout)
        val fileContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as WKFileContent
        val fileNameTv = parentView.findViewById<TextView>(R.id.fileNameTv)
        val fileSizeTv = parentView.findViewById<TextView>(R.id.fileSizeTv)
        val fileBadgeTv = parentView.findViewById<TextView>(R.id.fileBadgeTv)

        contentLayout.gravity = if (from == WKChatIteMsgFromType.SEND) Gravity.END else Gravity.START
        bubbleLayout.layoutParams.width = getViewWidth(from, uiChatMsgItemEntity)

        fileNameTv.text =
            if (TextUtils.isEmpty(fileContent.name)) context.getString(R.string.unknown_file) else fileContent.name
        fileSizeTv.text = FileMessageUtils.formatFileSize(fileContent.size)
        fileBadgeTv.text = FileMessageUtils.getFileBadgeText(fileContent.name)

        bubbleLayout.setOnClickListener {
            val intent = Intent(context, FileDetailActivity::class.java)
            intent.putExtra(FileDetailActivity.EXTRA_NAME, fileContent.name)
            intent.putExtra(FileDetailActivity.EXTRA_SIZE, fileContent.size)
            intent.putExtra(FileDetailActivity.EXTRA_URL, fileContent.url)
            intent.putExtra(FileDetailActivity.EXTRA_LOCAL_PATH, fileContent.localPath)
            intent.putExtra(FileDetailActivity.EXTRA_CLIENT_MSG_NO, uiChatMsgItemEntity.wkMsg.clientMsgNO)
            context.startActivity(intent)
        }
    }

    override val itemViewType: Int
        get() = WKContentType.WK_FILE

    override fun resetCellBackground(
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellBackground(parentView, uiChatMsgItemEntity, from)
        val bgType = getMsgBgType(
            uiChatMsgItemEntity.previousMsg,
            uiChatMsgItemEntity.wkMsg,
            uiChatMsgItemEntity.nextMsg
        )
        parentView.findViewById<BubbleLayout>(R.id.fileBubbleLayout)
            .setAll(bgType, from, WKContentType.WK_FILE)
    }

    override fun resetCellListener(
        position: Int,
        parentView: View,
        uiChatMsgItemEntity: WKUIChatMsgItemEntity,
        from: WKChatIteMsgFromType
    ) {
        super.resetCellListener(position, parentView, uiChatMsgItemEntity, from)
        addLongClick(parentView.findViewById(R.id.fileBubbleLayout), uiChatMsgItemEntity)
    }
}
