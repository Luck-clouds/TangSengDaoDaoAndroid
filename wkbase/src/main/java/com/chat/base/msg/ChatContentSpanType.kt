package com.chat.base.msg

class ChatContentSpanType {
    companion object {
        @JvmStatic
        val mention = "mention"

        @JvmStatic
        val link = "link"

        @JvmStatic
        val botCommand = "bot_command"

        @JvmStatic
        val richBold = "rich_bold"

        @JvmStatic
        val richItalic = "rich_italic"

        @JvmStatic
        val richColor = "rich_color"

        @JvmStatic
        val richUnderline = "rich_underline"

        @JvmStatic
        val richStrike = "rich_strike"

        @JvmStatic
        val richSize = "rich_size"
    }
}
