package com.chat.flagship.chatbg;

import java.io.Serializable;
import java.util.List;

/**
 * 当前会话聊天背景本地配置
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgConfig implements Serializable {
    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_PRESET = 1;
    public static final int TYPE_LOCAL = 2;

    public int type = TYPE_DEFAULT;
    public String sourceUrl;
    public String cover;
    public String localPath;
    public int isSvg;
    public boolean blur;
    public List<String> lightColors;
    public List<String> darkColors;
    public int gradientStep;
    public boolean showPattern = true;
}
