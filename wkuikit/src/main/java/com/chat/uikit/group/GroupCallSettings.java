package com.chat.uikit.group;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;

import java.util.HashMap;

/**
 * 群通话开关的本地读取与缓存。服务端未返回新字段时保持历史行为，默认启用。
 */
public final class GroupCallSettings {
    public static final String AUDIO_ENABLED = "audio_call_enabled";
    public static final String VIDEO_ENABLED = "video_call_enabled";

    private GroupCallSettings() {
    }

    public static boolean isEnabled(WKChannel channel, int callType) {
        if (channel == null || channel.remoteExtraMap == null) {
            return true;
        }
        Object value = channel.remoteExtraMap.get(callType == 1 ? VIDEO_ENABLED : AUDIO_ENABLED);
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        if (value instanceof String) {
            return !"0".equals(value);
        }
        return true;
    }

    public static void apply(String groupNo, int audioEnabled, int videoEnabled) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(
                groupNo,
                com.xinbida.wukongim.entity.WKChannelType.GROUP
        );
        if (channel == null) {
            channel = new WKChannel(groupNo, com.xinbida.wukongim.entity.WKChannelType.GROUP);
        }
        if (channel.remoteExtraMap == null) {
            channel.remoteExtraMap = new HashMap<>();
        }
        channel.remoteExtraMap.put(AUDIO_ENABLED, audioEnabled);
        channel.remoteExtraMap.put(VIDEO_ENABLED, videoEnabled);
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
    }
}
