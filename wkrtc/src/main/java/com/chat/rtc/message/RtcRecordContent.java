package com.chat.rtc.message;

import com.chat.base.msgitem.WKContentType;

/** Registers the server's string-based rtc_record message as a supported type. */
public class RtcRecordContent extends RtcNoticeContent {
    public RtcRecordContent() {
        type = WKContentType.rtcRecord;
    }
}
