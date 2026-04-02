package com.chat.uikit.chat.msgmodel;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.R;
import com.chat.base.WKBaseApplication;
import com.chat.base.msgitem.WKContentType;
import com.chat.uikit.chat.file.FileMessageUtils;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 文件消息体
 */
public class WKFileContent extends WKMediaMessageContent {
    public String name;
    public long size;
    public String ext;

    public WKFileContent() {
        type = WKContentType.WK_FILE;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("size", size);
            jsonObject.put("ext", ext);
            jsonObject.put("url", url);
            jsonObject.put("localPath", localPath);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        name = jsonObject.optString("name");
        size = jsonObject.optLong("size");
        ext = jsonObject.optString("ext");
        url = jsonObject.optString("url");
        localPath = jsonObject.optString("localPath");
        if (TextUtils.isEmpty(ext)) {
            ext = FileMessageUtils.getFileExtension(name);
        }
        return this;
    }

    protected WKFileContent(Parcel in) {
        super(in);
        name = in.readString();
        size = in.readLong();
        ext = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(name);
        dest.writeLong(size);
        dest.writeString(ext);
    }

    public static final Creator<WKFileContent> CREATOR = new Creator<>() {
        @Override
        public WKFileContent createFromParcel(Parcel in) {
            return new WKFileContent(in);
        }

        @Override
        public WKFileContent[] newArray(int size) {
            return new WKFileContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getDisplayContent() {
        String fileName = TextUtils.isEmpty(name)
                ? WKBaseApplication.getInstance().application.getString(com.chat.uikit.R.string.unknown_file)
                : name;
        return WKBaseApplication.getInstance().application.getString(R.string.last_message_file) + " " + fileName;
    }

    @Override
    public String getSearchableWord() {
        return TextUtils.isEmpty(name) ? "" : name;
    }
}
