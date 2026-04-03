package com.chat.uikit.contacts.label;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabelModel extends WKBaseModel {
    private static final String TAG = "LabelModel";
    private LabelModel() {
    }

    private static class Binder {
        private static final LabelModel MODEL = new LabelModel();
    }

    public static LabelModel getInstance() {
        return Binder.MODEL;
    }

    public interface ILabelListListener {
        void onResult(int code, String msg, List<LabelEntity> list);
    }

    public void getLabels(boolean fullSync, ILabelListListener listener) {
        if (fullSync) {
            request(createService(LabelService.class).getTagsFull(), new IRequestResultListener<>() {
                @Override
                public void onSuccess(JSONObject result) {
                    listener.onResult(HttpResponseCode.success, "", parseFromObject(result));
                }

                @Override
                public void onFail(int code, String msg) {
                    fallbackTags(listener, code, msg);
                }
            });
            return;
        }
        request(createService(LabelService.class).getTags(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(JSONArray result) {
                listener.onResult(HttpResponseCode.success, "", parseFromArray(result));
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg, new ArrayList<>());
            }
        });
    }

    private void fallbackTags(ILabelListListener listener, int code, String msg) {
        request(createService(LabelService.class).getTags(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(JSONArray result) {
                listener.onResult(HttpResponseCode.success, "", parseFromArray(result));
            }

            @Override
            public void onFail(int innerCode, String innerMsg) {
                listener.onResult(code == HttpResponseCode.success ? innerCode : code, TextUtils.isEmpty(msg) ? innerMsg : msg, new ArrayList<>());
            }
        });
    }

    public void createLabelWithContacts(String name, List<WKChannel> members, ICommonListener listener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", name);
        jsonObject.put("sort_no", 0);
        jsonObject.put("uids", buildUidArray(members));
        WKLogUtils.d(TAG, "request POST /v1/friend/tags/create_with_contacts body=" + jsonObject);
        request(createService(LabelService.class).createTagWithContacts(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(JSONObject result) {
                WKLogUtils.d(TAG, "response POST /v1/friend/tags/create_with_contacts body=" + result);
                listener.onResult(HttpResponseCode.success, "");
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e(TAG, "fail POST /v1/friend/tags/create_with_contacts code=" + code + " msg=" + msg);
                listener.onResult(code, msg);
            }
        });
    }

    public void updateLabelName(String labelId, String name, ICommonListener listener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", name);
        jsonObject.put("sort_no", 0);
        WKLogUtils.d(TAG, "request PUT /v1/friend/tags/" + labelId + " body=" + jsonObject);
        request(createService(LabelService.class).updateTag(labelId, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(JSONObject result) {
                WKLogUtils.d(TAG, "response PUT /v1/friend/tags/" + labelId + " body=" + result);
                listener.onResult(HttpResponseCode.success, "");
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e(TAG, "fail PUT /v1/friend/tags/" + labelId + " code=" + code + " msg=" + msg);
                listener.onResult(code, msg);
            }
        });
    }

    public void addContacts(String labelId, List<String> uidList, ICommonListener listener) {
        JSONObject jsonObject = new JSONObject();
        JSONArray uidArray = new JSONArray();
        uidArray.addAll(uidList);
        jsonObject.put("uids", uidArray);
        WKLogUtils.d(TAG, "request POST /v1/friend/tags/" + labelId + "/contacts body=" + jsonObject);
        request(createService(LabelService.class).addContacts(labelId, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                WKLogUtils.d(TAG, "response POST /v1/friend/tags/" + labelId + "/contacts status=" + result.status + " msg=" + result.msg);
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e(TAG, "fail POST /v1/friend/tags/" + labelId + "/contacts code=" + code + " msg=" + msg);
                listener.onResult(code, msg);
            }
        });
    }

    public void removeContact(String labelId, String uid, ICommonListener listener) {
        WKLogUtils.d(TAG, "request DELETE /v1/friend/tags/" + labelId + "/contacts/" + uid);
        request(createService(LabelService.class).removeContact(labelId, uid), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                WKLogUtils.d(TAG, "response DELETE /v1/friend/tags/" + labelId + "/contacts/" + uid + " status=" + result.status + " msg=" + result.msg);
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e(TAG, "fail DELETE /v1/friend/tags/" + labelId + "/contacts/" + uid + " code=" + code + " msg=" + msg);
                listener.onResult(code, msg);
            }
        });
    }

    public void deleteLabel(String labelId, ICommonListener listener) {
        WKLogUtils.d(TAG, "request DELETE /v1/friend/tags/" + labelId);
        request(createService(LabelService.class).deleteTag(labelId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                WKLogUtils.d(TAG, "response DELETE /v1/friend/tags/" + labelId + " status=" + result.status + " msg=" + result.msg);
                listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                WKLogUtils.e(TAG, "fail DELETE /v1/friend/tags/" + labelId + " code=" + code + " msg=" + msg);
                listener.onResult(code, msg);
            }
        });
    }

    private JSONArray buildUidArray(List<WKChannel> members) {
        JSONArray jsonArray = new JSONArray();
        if (WKReader.isNotEmpty(members)) {
            for (WKChannel channel : members) {
                if (channel != null && !TextUtils.isEmpty(channel.channelID)) {
                    jsonArray.add(channel.channelID);
                }
            }
        }
        return jsonArray;
    }

    private List<LabelEntity> parseFromObject(JSONObject root) {
        if (root == null) {
            return new ArrayList<>();
        }
        JSONArray tagArray = findTagArray(root);
        if (tagArray != null) {
            Map<String, LabelEntity> map = parseTagMap(tagArray);
            applyRelations(map, findRelationArray(root));
            return sortLabels(map);
        }
        Object data = root.get("data");
        if (data instanceof JSONArray) {
            return parseFromArray((JSONArray) data);
        }
        return new ArrayList<>();
    }

    private JSONArray findTagArray(JSONObject root) {
        JSONArray array = root.getJSONArray("tags");
        if (array != null) {
            return array;
        }
        array = root.getJSONArray("list");
        if (array != null) {
            return array;
        }
        array = root.getJSONArray("items");
        if (array != null) {
            return array;
        }
        Object data = root.get("data");
        if (data instanceof JSONObject) {
            JSONObject dataObject = (JSONObject) data;
            array = dataObject.getJSONArray("tags");
            if (array != null) {
                return array;
            }
            array = dataObject.getJSONArray("list");
            if (array != null) {
                return array;
            }
            array = dataObject.getJSONArray("items");
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private List<LabelEntity> parseFromArray(JSONArray array) {
        return sortLabels(parseTagMap(array));
    }

    private Map<String, LabelEntity> parseTagMap(JSONArray array) {
        Map<String, LabelEntity> map = new LinkedHashMap<>();
        if (array == null) {
            return map;
        }
        for (int i = 0, size = array.size(); i < size; i++) {
            JSONObject object = array.getJSONObject(i);
            if (object == null) {
                continue;
            }
            LabelEntity entity = parseEntity(object);
            if (entity == null || TextUtils.isEmpty(entity.id) || entity.isDeleted == 1) {
                continue;
            }
            map.put(entity.id, entity);
        }
        return map;
    }

    private List<LabelEntity> sortLabels(Map<String, LabelEntity> map) {
        List<LabelEntity> list = new ArrayList<>(map.values());
        Collections.sort(list, (o1, o2) -> {
            int sortCompare = Integer.compare(o2.sortNo, o1.sortNo);
            if (sortCompare != 0) {
                return sortCompare;
            }
            return Long.compare(o2.version, o1.version);
        });
        return list;
    }

    private void applyRelations(Map<String, LabelEntity> map, JSONArray relationArray) {
        if (map.isEmpty() || relationArray == null || relationArray.isEmpty()) {
            return;
        }
        for (int i = 0, size = relationArray.size(); i < size; i++) {
            JSONObject relation = relationArray.getJSONObject(i);
            if (relation == null || intValue(relation, "is_deleted", "deleted") == 1) {
                continue;
            }
            String tagId = stringValue(relation, "tag_id", "id");
            if (TextUtils.isEmpty(tagId)) {
                continue;
            }
            LabelEntity entity = map.get(tagId);
            if (entity == null) {
                continue;
            }
            WKChannel channel = parseChannel(relation);
            if (channel != null && !containsMember(entity.members, channel.channelID)) {
                entity.members.add(channel);
                entity.syncCount();
            }
        }
    }

    private JSONArray findRelationArray(JSONObject root) {
        JSONArray array = root.getJSONArray("relations");
        if (array != null) {
            return array;
        }
        Object data = root.get("data");
        if (data instanceof JSONObject) {
            return ((JSONObject) data).getJSONArray("relations");
        }
        return null;
    }

    private LabelEntity parseEntity(JSONObject object) {
        String id = stringValue(object, "id", "tag_id", "label_id");
        String name = stringValue(object, "name", "tag_name", "label_name");
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) {
            return null;
        }
        LabelEntity entity = new LabelEntity();
        entity.id = id;
        entity.name = name;
        entity.sortNo = intValue(object, "sort_no");
        entity.count = intValue(object, "count", "member_count", "contact_count", "num");
        entity.version = longValue(object, "version");
        entity.isDeleted = intValue(object, "is_deleted", "deleted");
        entity.createdAt = stringValue(object, "created_at");
        entity.updatedAt = stringValue(object, "updated_at");

        JSONArray memberArray = memberArray(object);
        if (memberArray != null) {
            for (int i = 0, size = memberArray.size(); i < size; i++) {
                WKChannel channel = parseChannel(memberArray.get(i));
                if (channel != null && !containsMember(entity.members, channel.channelID)) {
                    entity.members.add(channel);
                }
            }
        }

        JSONArray uidArray = uidArray(object);
        if (uidArray != null) {
            for (int i = 0, size = uidArray.size(); i < size; i++) {
                WKChannel channel = parseChannel(uidArray.get(i));
                if (channel != null && !containsMember(entity.members, channel.channelID)) {
                    entity.members.add(channel);
                }
            }
        }
        if (entity.members.isEmpty()) {
            entity.count = Math.max(entity.count, 0);
        } else {
            entity.syncCount();
        }
        return entity;
    }

    private JSONArray memberArray(JSONObject object) {
        JSONArray array = object.getJSONArray("members");
        if (array != null) {
            return array;
        }
        array = object.getJSONArray("contacts");
        if (array != null) {
            return array;
        }
        array = object.getJSONArray("users");
        if (array != null) {
            return array;
        }
        return object.getJSONArray("friends");
    }

    private JSONArray uidArray(JSONObject object) {
        JSONArray array = object.getJSONArray("uids");
        if (array != null) {
            return array;
        }
        array = object.getJSONArray("to_uids");
        if (array != null) {
            return array;
        }
        return object.getJSONArray("member_uids");
    }

    private WKChannel parseChannel(Object source) {
        if (source == null) {
            return null;
        }
        String uid;
        String name = null;
        String remark = null;
        String avatar = null;
        String avatarCacheKey = null;
        if (source instanceof String) {
            uid = (String) source;
        } else if (source instanceof JSONObject) {
            JSONObject object = (JSONObject) source;
            // 标签关系对象同时包含关系主键 id 和联系人 to_uid，这里必须优先取真实联系人 uid。
            uid = stringValue(object, "uid", "to_uid", "id");
            name = stringValue(object, "name", "channel_name", "nickname");
            remark = stringValue(object, "remark", "channel_remark");
            avatar = stringValue(object, "avatar");
            avatarCacheKey = stringValue(object, "avatar_cache_key", "avatarCacheKey");
        } else {
            uid = String.valueOf(source);
        }
        if (TextUtils.isEmpty(uid)) {
            return null;
        }
        WKChannel local = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
        WKChannel channel = new WKChannel();
        channel.channelID = uid;
        channel.channelType = WKChannelType.PERSONAL;
        if (local != null) {
            channel.channelName = local.channelName;
            channel.channelRemark = local.channelRemark;
            channel.avatar = local.avatar;
            channel.avatarCacheKey = local.avatarCacheKey;
        }
        if (!TextUtils.isEmpty(name)) {
            channel.channelName = name;
        }
        if (!TextUtils.isEmpty(remark)) {
            channel.channelRemark = remark;
        }
        if (!TextUtils.isEmpty(avatar)) {
            channel.avatar = avatar;
        }
        if (!TextUtils.isEmpty(avatarCacheKey)) {
            channel.avatarCacheKey = avatarCacheKey;
        }
        if (TextUtils.isEmpty(channel.channelName)) {
            channel.channelName = uid;
        }
        return channel;
    }

    private boolean containsMember(List<WKChannel> list, String uid) {
        for (WKChannel channel : list) {
            if (channel != null && uid.equals(channel.channelID)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.getString(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private int intValue(JSONObject object, String... keys) {
        for (String key : keys) {
            Integer value = object.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return 0;
    }

    private long longValue(JSONObject object, String... keys) {
        for (String key : keys) {
            Long value = object.getLong(key);
            if (value != null) {
                return value;
            }
        }
        return 0L;
    }
}
