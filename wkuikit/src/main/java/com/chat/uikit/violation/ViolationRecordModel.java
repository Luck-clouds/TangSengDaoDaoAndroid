package com.chat.uikit.violation;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultErrorInfoListener;

import java.util.ArrayList;
import java.util.List;

public final class ViolationRecordModel extends WKBaseModel {
    private ViolationRecordModel() {
    }

    private static class Holder {
        private static final ViolationRecordModel INSTANCE = new ViolationRecordModel();
    }

    public static ViolationRecordModel getInstance() {
        return Holder.INSTANCE;
    }

    public void getRecords(String type, int page, int limit, Listener listener) {
        requestAndErrorBack(createService(ViolationRecordService.class).getRecords(type, page, limit),
                new IRequestResultErrorInfoListener<>() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        int total = result == null ? 0 : result.getIntValue("total");
                        listener.onResult(HttpResponseCode.success, "", total,
                                parseRecords(result == null ? null : result.getJSONArray("list")));
                    }

                    @Override
                    public void onFail(int code, String msg, String errJson) {
                        listener.onResult(code, msg, 0, new ArrayList<>());
                    }
                });
    }

    private List<ViolationRecord> parseRecords(JSONArray array) {
        ArrayList<ViolationRecord> records = new ArrayList<>();
        if (array == null) {
            return records;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject object = array.getJSONObject(i);
            if (object == null) {
                continue;
            }
            ViolationRecord record = new ViolationRecord();
            record.id = object.getLongValue("id");
            record.recordType = safe(object.getString("record_type"));
            record.status = safe(object.getString("status"));
            record.violationType = safe(object.getString("violation_type"));
            record.reason = safe(object.getString("reason"));
            record.violationCount = object.getInteger("violation_count");
            record.warningLimit = object.getInteger("warning_limit");
            record.banUntil = safe(object.getString("ban_until"));
            record.createdAt = safe(object.getString("created_at"));
            if ("punishment".equals(record.recordType) || "complaint".equals(record.recordType)) {
                records.add(record);
            }
        }
        return records;
    }

    private String safe(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }

    public interface Listener {
        void onResult(int code, String msg, int total, List<ViolationRecord> records);
    }
}
