package com.chat.uikit.group.manage;

import android.content.Context;
import android.text.TextUtils;

import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.R;
import com.xinbida.wukongim.entity.WKChannelMember;

import java.util.Map;

public class GroupManageUtils {
    private GroupManageUtils() {
    }

    public static int getIntFromMap(Map<String, Object> map, String key) {
        if (map == null || TextUtils.isEmpty(key) || !map.containsKey(key)) {
            return 0;
        }
        Object object = map.get(key);
        if (object instanceof Integer) {
            return (int) object;
        }
        if (object instanceof Long) {
            return ((Long) object).intValue();
        }
        if (object instanceof String) {
            try {
                return Integer.parseInt((String) object);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    public static String getDisplayName(WKChannelMember member) {
        if (member == null) {
            return "";
        }
        if (!TextUtils.isEmpty(member.remark)) {
            return member.remark;
        }
        if (!TextUtils.isEmpty(member.memberRemark)) {
            return member.memberRemark;
        }
        return TextUtils.isEmpty(member.memberName) ? member.memberUID : member.memberName;
    }

    public static boolean isForbidden(long forbiddenExpireTime) {
        return forbiddenExpireTime > WKTimeUtils.getInstance().getCurrentSeconds();
    }

    public static String getForbiddenStatusText(Context context, long forbiddenExpireTime) {
        if (context == null || !isForbidden(forbiddenExpireTime)) {
            return context == null ? "" : context.getString(R.string.group_forbidden_not_set);
        }
        long diff = forbiddenExpireTime - WKTimeUtils.getInstance().getCurrentSeconds();
        long day = diff / (3600 * 24);
        long hour = diff / 3600;
        long minute = diff / 60;
        if (day > 0) {
            return String.format(context.getString(R.string.forbidden_to_day), day);
        }
        if (hour > 0) {
            return String.format(context.getString(R.string.forbidden_to_hour), hour);
        }
        if (minute > 0) {
            return String.format(context.getString(R.string.forbidden_to_minute), minute);
        }
        return String.format(context.getString(R.string.forbidden_to_second), diff);
    }
}
