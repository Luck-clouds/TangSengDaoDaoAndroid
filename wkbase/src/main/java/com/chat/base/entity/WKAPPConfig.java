package com.chat.base.entity;

public class WKAPPConfig {
    public int version;
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
    public int send_welcome_message_on;
    public int invite_system_account_join_group_on;
    public int register_user_must_complete_info_on;
    public int can_modify_api_url;
}
