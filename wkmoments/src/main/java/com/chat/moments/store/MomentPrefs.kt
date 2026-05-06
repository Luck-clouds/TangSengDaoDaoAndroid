package com.chat.moments.store

/**
 * 朋友圈本地状态缓存
 * Created by Luckclouds.
 */

import com.alibaba.fastjson.JSON
import com.chat.base.config.WKConfig
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.moments.entity.MomentNotice

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

    fun saveLatestNoticePreview(text: String) {
        WKSharedPreferencesUtil.getInstance().putSP(key("moment_latest_notice_preview"), text)
    }

    fun latestNoticePreview(): String {
        return WKSharedPreferencesUtil.getInstance().getSP(key("moment_latest_notice_preview"))
    }

    fun saveNoticeList(list: List<MomentNotice>) {
        WKSharedPreferencesUtil.getInstance().putSP(
            key("moment_notice_list"),
            JSON.toJSONString(list)
        )
    }

    fun noticeList(): MutableList<MomentNotice> {
        val value = WKSharedPreferencesUtil.getInstance().getSP(key("moment_notice_list"))
        if (value.isEmpty()) return mutableListOf()
        return try {
            JSON.parseArray(value, MomentNotice::class.java)?.toMutableList() ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
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
