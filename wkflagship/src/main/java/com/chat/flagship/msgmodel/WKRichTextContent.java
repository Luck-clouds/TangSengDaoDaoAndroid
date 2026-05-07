package com.chat.flagship.msgmodel;

/**
 * 富文本消息体
 * Created by Luckclouds .
 */

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.WKBaseApplication;
import com.chat.base.msgitem.WKContentType;
import com.chat.flagship.R;
import com.xinbida.wukongim.entity.WKMentionInfo;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WKRichTextContent extends WKMessageContent {
    public static final String NODE_KIND_TEXT = "text";
    public static final String NODE_KIND_IMAGE = "image";

    public String content = "";
    public List<RichNode> nodes = new ArrayList<>();

    public WKRichTextContent() {
        type = WKContentType.richText;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("type", type);
            jsonObject.put("content", content);
            jsonObject.put("nodes", encodeNodes());
            jsonObject.put("entities", encodeEntities());
            jsonObject.put("mention_all", mentionAll);
            jsonObject.put("mention_info", encodeMentionInfo());
        } catch (Exception ignored) {
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        if (jsonObject == null) {
            return this;
        }
        content = jsonObject.optString("content");
        type = jsonObject.optInt("type", WKContentType.richText);
        mentionAll = jsonObject.optInt("mention_all", 0);
        nodes = decodeNodes(jsonObject.optJSONArray("nodes"));
        entities = decodeEntities(jsonObject.optJSONArray("entities"));
        mentionInfo = decodeMentionInfo(jsonObject.optJSONObject("mention_info"));
        return this;
    }

    @Override
    public String getDisplayContent() {
        if (!TextUtils.isEmpty(content)) {
            return content;
        }
        return WKBaseApplication.getInstance().application.getString(R.string.flagship_rich_text_summary);
    }

    private JSONArray encodeNodes() {
        JSONArray array = new JSONArray();
        if (nodes == null) {
            return array;
        }
        for (RichNode node : nodes) {
            if (node == null) {
                continue;
            }
            JSONObject object = new JSONObject();
            try {
                object.put("kind", node.kind);
                if (NODE_KIND_TEXT.equals(node.kind)) {
                    object.put("text", node.text);
                    object.put("entities", encodeEntities(node.entities));
                } else if (NODE_KIND_IMAGE.equals(node.kind)) {
                    object.put("path", node.path);
                    object.put("width_percent", node.widthPercent);
                    object.put("width", node.width);
                    object.put("height", node.height);
                }
            } catch (Exception ignored) {
            }
            array.put(object);
        }
        return array;
    }

    private List<RichNode> decodeNodes(JSONArray array) {
        List<RichNode> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            RichNode node = new RichNode();
            node.kind = object.optString("kind", NODE_KIND_IMAGE);
            if (NODE_KIND_TEXT.equals(node.kind)) {
                node.text = object.optString("text");
                node.entities = decodeEntities(object.optJSONArray("entities"));
            } else {
                node.path = object.optString("path");
                node.widthPercent = object.optInt("width_percent", 100);
                node.width = object.optInt("width", 0);
                node.height = object.optInt("height", 0);
            }
            result.add(node);
        }
        return result;
    }

    private JSONArray encodeEntities() {
        return encodeEntities(entities);
    }

    private JSONArray encodeEntities(List<WKMsgEntity> source) {
        JSONArray array = new JSONArray();
        if (source == null) {
            return array;
        }
        for (WKMsgEntity entity : source) {
            if (entity == null) {
                continue;
            }
            JSONObject object = new JSONObject();
            try {
                object.put("offset", entity.offset);
                object.put("length", entity.length);
                object.put("type", entity.type);
                object.put("value", entity.value);
            } catch (Exception ignored) {
            }
            array.put(object);
        }
        return array;
    }

    private List<WKMsgEntity> decodeEntities(JSONArray array) {
        List<WKMsgEntity> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            WKMsgEntity entity = new WKMsgEntity();
            entity.offset = object.optInt("offset", 0);
            entity.length = object.optInt("length", 0);
            entity.type = object.optString("type");
            entity.value = object.optString("value", null);
            result.add(entity);
        }
        return result;
    }

    private JSONObject encodeMentionInfo() {
        JSONObject object = new JSONObject();
        if (mentionInfo == null || mentionInfo.uids == null) {
            return object;
        }
        try {
            JSONArray array = new JSONArray();
            for (String uid : mentionInfo.uids) {
                array.put(uid);
            }
            object.put("uids", array);
        } catch (Exception ignored) {
        }
        return object;
    }

    private WKMentionInfo decodeMentionInfo(JSONObject object) {
        if (object == null) {
            return null;
        }
        JSONArray array = object.optJSONArray("uids");
        if (array == null || array.length() == 0) {
            return null;
        }
        WKMentionInfo info = new WKMentionInfo();
        info.uids = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String uid = array.optString(i);
            if (!TextUtils.isEmpty(uid)) {
                info.uids.add(uid);
            }
        }
        return info;
    }

    protected WKRichTextContent(Parcel in) {
        super(in);
        content = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(content);
    }

    public static final Creator<WKRichTextContent> CREATOR = new Creator<>() {
        @Override
        public WKRichTextContent createFromParcel(Parcel in) {
            return new WKRichTextContent(in);
        }

        @Override
        public WKRichTextContent[] newArray(int size) {
            return new WKRichTextContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    public static class RichNode {
        public String kind = NODE_KIND_TEXT;
        public String text;
        public List<WKMsgEntity> entities = new ArrayList<>();
        public String path;
        public int widthPercent = 100;
        public int width;
        public int height;
    }
}
