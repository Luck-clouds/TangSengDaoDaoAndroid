package com.chat.flagship.msgmodel;

/**
 * 截屏通知消息体
 * Created by Luckclouds and chatGPT.
 */

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.chat.base.WKBaseApplication;
import com.chat.base.msgitem.WKContentType;
import com.chat.flagship.R;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONObject;

public class WKScreenShotContent extends WKMessageContent {
    public WKScreenShotContent() {
        type = WKContentType.screenshot;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        return new JSONObject();
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        return this;
    }

    @Override
    public String getDisplayContent() {
        // 会话列表只需要一个稳定的摘要文案，具体“你/某某”文案交给 provider 渲染。
        return WKBaseApplication.getInstance().application.getString(R.string.flagship_screenshot_notice_summary);
    }

    protected WKScreenShotContent(Parcel in) {
        super(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }

    public static final Creator<WKScreenShotContent> CREATOR = new Creator<>() {
        @Override
        public WKScreenShotContent createFromParcel(Parcel in) {
            return new WKScreenShotContent(in);
        }

        @Override
        public WKScreenShotContent[] newArray(int size) {
            return new WKScreenShotContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
