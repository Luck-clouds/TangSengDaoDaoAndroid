package com.chat.uikit.favorite;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultErrorInfoListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ViewFavoriteTipBinding;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FavoriteModel extends WKBaseModel {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Dialog favoriteTipDialog;

    private FavoriteModel() {
    }

    private static class Holder {
        private static final FavoriteModel MODEL = new FavoriteModel();
    }

    public static FavoriteModel getInstance() {
        return Holder.MODEL;
    }

    public interface IFavoriteListListener {
        void onResult(int code, String msg, int totalCount, List<FavoriteEntity> list);
    }

    public void addFavorite(Context context, WKMsg msg, ICommonListener listener) {
        if (context == null) {
            return;
        }
        if (msg == null || TextUtils.isEmpty(msg.messageID) || TextUtils.isEmpty(msg.channelID) || msg.messageSeq <= 0) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.favorite_message_invalid));
            if (listener != null) {
                listener.onResult(HttpResponseCode.error, context.getString(R.string.favorite_message_invalid));
            }
            return;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message_id", msg.messageID);
        jsonObject.put("message_seq", msg.messageSeq);
        jsonObject.put("channel_id", msg.channelID);
        jsonObject.put("channel_type", msg.channelType);
        requestAndErrorBack(createService(FavoriteService.class).favorite(jsonObject), new IRequestResultErrorInfoListener<>() {
            @Override
            public void onSuccess(JSONObject result) {
                int favoriteStatus = result == null ? 0 : result.getIntValue("is_favorite");
                if (favoriteStatus == 1) {
                    showFavoriteAddedTips(context);
                } else {
                    WKToastUtils.getInstance().showToastNormal(context.getString(R.string.favorite_delete_success));
                }
                if (listener != null) {
                    listener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg, String errJson) {
                WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? context.getString(R.string.favorite_add_failed) : msg);
                if (listener != null) {
                    listener.onResult(code, msg);
                }
            }
        });
    }

    public void addFavorite(Context context, Map<String, Object> map, ICommonListener listener) {
        addFavorite(context, resolveMsg(map), listener);
    }

    public void deleteFavorite(Context context, FavoriteEntity entity, ICommonListener listener) {
        if (context == null || entity == null) {
            return;
        }
        JSONObject jsonObject = new JSONObject(true);
        JSONArray ids = new JSONArray();
        JSONArray messageIds = new JSONArray();
        String messageId = entity.messageId;
        if (TextUtils.isEmpty(messageId)) {
            WKMsg local = entity.findLocalMsg();
            if (local != null) {
                messageId = local.messageID;
            }
        }
        if (entity.id > 0) {
            ids.add(entity.id);
        }
        if (!TextUtils.isEmpty(messageId)) {
            messageIds.add(messageId);
        }
        if (!ids.isEmpty()) {
            jsonObject.put("ids", ids);
        }
        if (!messageIds.isEmpty()) {
            jsonObject.put("message_ids", messageIds);
        }
        if (jsonObject.isEmpty()) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.favorite_delete_failed));
            if (listener != null) {
                listener.onResult(HttpResponseCode.error, context.getString(R.string.favorite_delete_failed));
            }
            return;
        }
        entity.messageId = messageId;
        requestAndErrorBack(createService(FavoriteService.class).deleteFavorite(jsonObject), new IRequestResultErrorInfoListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (isFavoriteSuccess(result.status)) {
                    WKToastUtils.getInstance().showToastNormal(context.getString(R.string.favorite_delete_success));
                } else if (!TextUtils.isEmpty(result.msg)) {
                    WKToastUtils.getInstance().showToastNormal(result.msg);
                }
                if (listener != null) {
                    listener.onResult(isFavoriteSuccess(result.status) ? HttpResponseCode.success : result.status, result.msg);
                }
            }

            @Override
            public void onFail(int code, String msg, String errJson) {
                WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? context.getString(R.string.favorite_delete_failed) : msg);
                if (listener != null) {
                    listener.onResult(code, msg);
                }
            }
        });
    }

    public void getFavoriteList(int pageIndex, int pageSize, IFavoriteListListener listener) {
        requestAndErrorBack(createService(FavoriteService.class).favoriteList(pageIndex, pageSize, "all"), new IRequestResultErrorInfoListener<>() {
            @Override
            public void onSuccess(JSONObject result) {
                List<FavoriteEntity> list = parseFavorites(result == null ? null : result.getJSONArray("list"));
                int count = result == null ? 0 : result.getIntValue("count");
                listener.onResult(HttpResponseCode.success, "", count, list);
            }

            @Override
            public void onFail(int code, String msg, String errJson) {
                listener.onResult(code, msg, 0, new ArrayList<>());
            }
        });
    }

    public WKMessageContent buildForwardContent(FavoriteEntity entity) {
        if (entity == null) {
            return null;
        }
        WKMsg local = entity.findLocalMsg();
        if (local != null && local.baseContentMsgModel != null) {
            return local.baseContentMsgModel;
        }
        if (entity.isImageType()) {
            WKImageContent imageContent = new WKImageContent("");
            imageContent.url = entity.getDisplayContent();
            imageContent.width = entity.width;
            imageContent.height = entity.height;
            return imageContent;
        }
        WKTextContent textContent = new WKTextContent(entity.getDisplayContent());
        textContent.content = entity.getDisplayContent();
        return textContent;
    }

    public String formatTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        if (TextUtils.isDigitsOnly(value)) {
            long time = Long.parseLong(value);
            if (String.valueOf(time).length() <= 10) {
                time *= 1000;
            }
            return WKTimeUtils.getInstance().getShowDateAndMinute(time);
        }
        return value;
    }

    private WKMsg resolveMsg(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object uniqueKeyObject = map.get("unique_key");
        String uniqueKey = uniqueKeyObject == null ? "" : String.valueOf(uniqueKeyObject);
        WKMsg msg = null;
        if (!TextUtils.isEmpty(uniqueKey)) {
            msg = WKIM.getInstance().getMsgManager().getWithMessageID(uniqueKey);
            if (msg == null) {
                msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(uniqueKey);
            }
        }
        return msg;
    }

    private List<FavoriteEntity> parseFavorites(JSONArray array) {
        List<FavoriteEntity> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0, size = array.size(); i < size; i++) {
            JSONObject object = array.getJSONObject(i);
            FavoriteEntity entity = parseFavorite(object);
            if (entity != null) {
                entity.fillFromLocalIfNeed();
                list.add(entity);
            }
        }
        return list;
    }

    private FavoriteEntity parseFavorite(JSONObject object) {
        if (object == null) {
            return null;
        }
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = longValue(object, "id");
        entity.messageId = stringValue(object, "message_id", "messageId");
        entity.messageSeq = longValue(object, "message_seq", "messageSeq");
        entity.channelId = stringValue(object, "channel_id", "channelId");
        entity.channelType = (byte) intValue(object, "channel_type", "channelType");
        entity.createdAt = stringValue(object, "created_at", "createdAt", "favorite_at", "updated_at");
        entity.type = intValue(object, "message_type", "type", "content_type");
        entity.content = stringValue(object, "content");
        if (TextUtils.isEmpty(entity.content) && entity.type == WKContentType.WK_IMAGE) {
            entity.content = stringValue(object, "image_url");
        }
        entity.authorUid = stringValue(object, "author", "author_uid", "from_uid", "uid");
        entity.authorName = stringValue(object, "nickname", "author_name", "from_name");
        entity.authorAvatar = stringValue(object, "author_avatar", "avatar");
        entity.authorAvatarCacheKey = stringValue(object, "avatar_cache_key", "avatarCacheKey");
        fillAuthorFromLocal(entity);
        return entity;
    }

    private void fillAuthorFromLocal(FavoriteEntity entity) {
        if (TextUtils.isEmpty(entity.authorUid)) {
            return;
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(entity.authorUid, WKChannelType.PERSONAL);
        if (channel == null) {
            return;
        }
        if (TextUtils.isEmpty(entity.authorName)) {
            entity.authorName = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
        }
        if (TextUtils.isEmpty(entity.authorAvatar)) {
            entity.authorAvatar = channel.avatar;
        }
        if (TextUtils.isEmpty(entity.authorAvatarCacheKey)) {
            entity.authorAvatarCacheKey = channel.avatarCacheKey;
        }
    }

    private String stringValue(JSONObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = object.getString(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private int intValue(JSONObject object, String... keys) {
        if (object == null) {
            return 0;
        }
        for (String key : keys) {
            Integer value = object.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return 0;
    }

    private long longValue(JSONObject object, String... keys) {
        if (object == null) {
            return 0L;
        }
        for (String key : keys) {
            Long value = object.getLong(key);
            if (value != null) {
                return value;
            }
            String string = object.getString(key);
            if (TextUtils.isDigitsOnly(string)) {
                return Long.parseLong(string);
            }
        }
        return 0L;
    }

    private boolean isFavoriteSuccess(int status) {
        return status == HttpResponseCode.success || status == 0;
    }

    private void showFavoriteAddedTips(Context context) {
        Activity activity = context instanceof Activity ? (Activity) context : ActManagerUtils.getInstance().getCurrentActivity();
        if (activity == null) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.favorite_add_success));
            return;
        }
        if (activity.isFinishing()) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.favorite_add_success));
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                dismissFavoriteTip();
                ViewFavoriteTipBinding binding = ViewFavoriteTipBinding.inflate(LayoutInflater.from(context));
                binding.contentTv.setText(R.string.favorite_add_success);
                binding.actionTv.setOnClickListener(v -> {
                    dismissFavoriteTip();
                    Intent intent = new Intent(context, FavoriteListActivity.class);
                    context.startActivity(intent);
                });
                favoriteTipDialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
                favoriteTipDialog.setContentView(binding.getRoot());
                favoriteTipDialog.setCancelable(true);
                favoriteTipDialog.setCanceledOnTouchOutside(true);
                Window window = favoriteTipDialog.getWindow();
                if (window != null) {
                    window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setBackgroundDrawableResource(android.R.color.transparent);
                    WindowManager.LayoutParams attributes = window.getAttributes();
                    attributes.y = (int) (activity.getResources().getDisplayMetrics().density * 80);
                    window.setAttributes(attributes);
                }
                favoriteTipDialog.show();
                mainHandler.postDelayed(this::dismissFavoriteTip, 2500);
            } catch (Exception e) {
                WKToastUtils.getInstance().showToastNormal(context.getString(R.string.favorite_add_success));
                dismissFavoriteTip();
            }
        });
    }

    private void dismissFavoriteTip() {
        mainHandler.removeCallbacksAndMessages(null);
        if (favoriteTipDialog != null) {
            try {
                if (favoriteTipDialog.isShowing()) {
                    favoriteTipDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            favoriteTipDialog = null;
        }
    }
}
