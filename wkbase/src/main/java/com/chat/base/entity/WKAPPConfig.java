package com.chat.base.entity;

public class WKAPPConfig {
    public int version;
    // 旧服务端或旧缓存缺少该字段时默认开启，兼容历史部署。
    public Integer mutual_delete_on;
    public String web_url;
    // 旧服务端缺少该字段时保持允许截屏，避免升级客户端后意外全局锁屏。
    public int global_screenshot_on = 1;
    public String contact_wecom_qrcode;
    public String contact_email;
    public String contact_phone;
    public int phone_search_off;
    public int shortno_edit_off;
    public int revoke_second;
    public int register_invite_on;
    // 旧服务端或旧缓存缺少此字段时默认显示，兼容历史部署。
    public Integer show_register_invite_code_input_on;
    public int send_welcome_message_on;
    public int invite_system_account_join_group_on;
    public int register_user_must_complete_info_on;
    public int can_modify_api_url;

    public boolean isRegisterInviteCodeInputVisible() {
        return show_register_invite_code_input_on == null
                || show_register_invite_code_input_on == 1;
    }

    public boolean isWebLoginVisible() {
        return isRegisterInviteCodeInputVisible();
    }

    public boolean isMutualDeleteEnabled() {
        return mutual_delete_on == null || mutual_delete_on == 1;
    }

    /**
     * HUAWEI 分支按注册邀请码输入框开关同步控制断网屏保入口。
     * 旧服务端缺少字段时仍保持显示，兼容历史配置。
     */
    public boolean isOfflineProtectionVisible() {
        return isRegisterInviteCodeInputVisible();
    }
}
