package com.chat.sticker.msg;

import android.os.Parcel;
import android.os.Parcelable;

import com.chat.base.msg.model.WKGifContent;
import com.xinbida.wukongim.message.type.WKMsgContentType;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * web 端 lottieEmojiSticker(type=13) 对应的贴纸消息
 */
public class WKEmojiStickerContent extends WKGifContent implements Parcelable {

    public WKEmojiStickerContent() {
        type = WKMsgContentType.WK_EMOJI_STICKER;
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

    protected WKEmojiStickerContent(Parcel in) {
        super(in);
        type = WKMsgContentType.WK_EMOJI_STICKER;
    }

    public static final Creator<WKEmojiStickerContent> CREATOR = new Creator<WKEmojiStickerContent>() {
        @Override
        public WKEmojiStickerContent createFromParcel(Parcel in) {
            return new WKEmojiStickerContent(in);
        }

        @Override
        public WKEmojiStickerContent[] newArray(int size) {
            return new WKEmojiStickerContent[size];
        }
    };

    @Override
    public String getDisplayContent() {
        return "[贴图]";
    }
}
