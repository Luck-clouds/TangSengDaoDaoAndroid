package com.chat.scan.entity;

import androidx.annotation.Keep;

import java.util.Map;

/**
 * 2020-07-26 22:07
 * 扫一扫结果
 */
@Keep
public class ScanResult {
    public String forward;
    public String type;
    public Map data;
}
