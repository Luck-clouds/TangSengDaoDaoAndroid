package com.chat.sticker.msg;

import android.os.Parcel;
import android.os.Parcelable;

import com.chat.base.msg.model.WKGifContent;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * web 端 lottieSticker(type=12) 对应的贴纸消息
 */
public class WKVectorStickerContent extends WKGifContent implements Parcelable {

    public WKVectorStickerContent() {
        type = WKMsgContentType.WK_VECTOR_STICKER;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("width", width);
            jsonObject.put("height", height);
            jsonObject.put("url", url);
            jsonObject.put("category", category);
            jsonObject.put("title", title);
            jsonObject.put("placeholder", placeholder);
            jsonObject.put("format", format);
            jsonObject.put("localPath", localPath);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        this.width = jsonObject.optInt("width");
        this.height = jsonObject.optInt("height");
        this.url = jsonObject.optString("url");
        this.category = jsonObject.optString("category");
        this.title = jsonObject.optString("title");
        this.localPath = jsonObject.optString("localPath");
        this.placeholder = jsonObject.optString("placeholder");
        this.format = jsonObject.optString("format");
        return this;
    }

    protected WKVectorStickerContent(Parcel in) {
        super(in);
        type = WKMsgContentType.WK_VECTOR_STICKER;
    }

    public static final Creator<WKVectorStickerContent> CREATOR = new Creator<WKVectorStickerContent>() {
        @Override
        public WKVectorStickerContent createFromParcel(Parcel in) {
            return new WKVectorStickerContent(in);
        }

        @Override
        public WKVectorStickerContent[] newArray(int size) {
            return new WKVectorStickerContent[size];
        }
    };

    @Override
    public String getDisplayContent() {
        return "[贴图]";
    }
}
