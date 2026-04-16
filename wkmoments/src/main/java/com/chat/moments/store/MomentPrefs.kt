package com.chat.moments.store

import com.chat.base.config.WKConfig
import com.chat.base.config.WKSharedPreferencesUtil

object MomentPrefs {
    private fun key(suffix: String): String {
        val uid = WKConfig.getInstance().uid ?: "moment"
        return "${uid}_$suffix"
    }

    fun saveUnreadCount(count: Int) {
        WKSharedPreferencesUtil.getInstance().putInt(key("moment_unread_count"), count)
    }

    fun unreadCount(): Int {
        return WKSharedPreferencesUtil.getInstance().getInt(key("moment_unread_count"))
    }

    fun saveNoticeVersion(version: Long) {
        WKSharedPreferencesUtil.getInstance().putLong(key("moment_notice_version"), version)
    }

    fun noticeVersion(): Long {
        return WKSharedPreferencesUtil.getInstance().getLong(key("moment_notice_version"))
    }

    fun saveUserState(uid: String, blockMe: Boolean, hideHim: Boolean) {
        WKSharedPreferencesUtil.getInstance().putBoolean(key("moment_state_block_$uid"), blockMe)
        WKSharedPreferencesUtil.getInstance().putBoolean(key("moment_state_hide_$uid"), hideHim)
    }

    fun getBlockMe(uid: String): Boolean {
        return WKSharedPreferencesUtil.getInstance().getBoolean(key("moment_state_block_$uid"), false)
    }

    fun getHideHim(uid: String): Boolean {
        return WKSharedPreferencesUtil.getInstance().getBoolean(key("moment_state_hide_$uid"), false)
    }
}
