package com.chat.uikit.group.manage;

public class GroupManageConstants {
    private GroupManageConstants() {
    }

    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_EXCLUDED_UIDS = "excludedUids";

    public static final int MODE_MANAGER = 1;
    public static final int MODE_BLACKLIST = 2;
    public static final int MODE_TRANSFER_OWNER = 3;

    public static final int REQUEST_MANAGERS = 3101;
    public static final int REQUEST_BLACKLIST = 3102;
    public static final int REQUEST_TRANSFER_OWNER = 3103;
    public static final int REQUEST_MEMBER_FORBIDDEN = 3104;
    public static final int REQUEST_MEMBER_SELECT = 3105;

    public static final String KEY_FORBIDDEN_ADD_FRIEND = "forbidden_add_friend";
    public static final String KEY_ALLOW_VIEW_HISTORY_MSG = "allow_view_history_msg";
    public static final String KEY_FORBIDDEN_EXPIR_TIME = "forbidden_expir_time";
}
