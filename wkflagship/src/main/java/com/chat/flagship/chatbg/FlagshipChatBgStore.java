package com.chat.flagship.chatbg;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;

import java.io.File;

/**
 * 聊天背景本地存储
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgStore {
    private static final String KEY_PREFIX = "chat_bg_";

    private FlagshipChatBgStore() {
    }

    public static FlagshipChatBgConfig getConfig(String channelId, byte channelType) {
        String content = WKSharedPreferencesUtil.getInstance().getSPWithUID(buildKey(channelId, channelType));
        if (TextUtils.isEmpty(content)) {
            return null;
        }
        try {
            return JSON.parseObject(content, FlagshipChatBgConfig.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void saveConfig(String channelId, byte channelType, FlagshipChatBgConfig config) {
        if (config == null) {
            clearConfig(channelId, channelType);
            return;
        }
        FlagshipChatBgConfig oldConfig = getConfig(channelId, channelType);
        deleteManagedFile(oldConfig, config.localPath);
        WKSharedPreferencesUtil.getInstance().putSPWithUID(buildKey(channelId, channelType), JSON.toJSONString(config));
    }

    public static void clearConfig(String channelId, byte channelType) {
        FlagshipChatBgConfig oldConfig = getConfig(channelId, channelType);
        deleteManagedFile(oldConfig, null);
        WKSharedPreferencesUtil.getInstance().putSPWithUID(buildKey(channelId, channelType), "");
    }

    private static String buildKey(String channelId, byte channelType) {
        return KEY_PREFIX + channelId + "_" + channelType;
    }

    private static void deleteManagedFile(FlagshipChatBgConfig oldConfig, String keepPath) {
        if (oldConfig == null || TextUtils.isEmpty(oldConfig.localPath) || TextUtils.equals(oldConfig.localPath, keepPath)) {
            return;
        }
        if (TextUtils.isEmpty(WKConstants.chatBgCacheDir) || !oldConfig.localPath.startsWith(WKConstants.chatBgCacheDir)) {
            return;
        }
        File file = new File(oldConfig.localPath);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
