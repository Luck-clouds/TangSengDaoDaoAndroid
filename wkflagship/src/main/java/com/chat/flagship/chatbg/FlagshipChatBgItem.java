package com.chat.flagship.chatbg;

import java.io.Serializable;
import java.util.List;

/**
 * 聊天背景列表项
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgItem implements Serializable {
    public boolean isDefault;
    public String cover;
    public String url;
    public int isSvg;
    public List<String> lightColors;
    public List<String> darkColors;
}
