package com.chat.uikit.violation;

import android.content.Context;
import android.text.TextUtils;

import com.chat.uikit.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ViolationRecord {
    public long id;
    public String recordType = "";
    public String status = "";
    public String violationType = "";
    public String reason = "";
    public Integer violationCount;
    public Integer warningLimit;
    public String banUntil = "";
    public String createdAt = "";

    public boolean isComplaint() {
        return "complaint".equals(recordType);
    }

    public String getTypeText(Context context) {
        return context.getString(isComplaint() ? R.string.record_type_complaint : R.string.record_type_punishment);
    }

    public String getStatusText(Context context) {
        if (isComplaint()) {
            if ("pending".equals(status)) {
                return context.getString(R.string.record_status_pending);
            }
            if ("handled".equals(status)) {
                if ("no_violation".equals(reason)) {
                    return context.getString(R.string.record_status_no_violation);
                }
                if ("violation".equals(reason)) {
                    return context.getString(R.string.record_status_confirmed);
                }
                return context.getString(R.string.record_status_handled);
            }
            return context.getString(R.string.record_status_pending);
        }
        switch (status) {
            case "warning":
                if (violationCount != null && warningLimit != null && warningLimit > 0) {
                    return context.getString(R.string.record_status_warning_count, violationCount, warningLimit);
                }
                return context.getString(R.string.record_status_warning);
            case "temporary_ban":
                String until = formatTime(banUntil);
                return TextUtils.isEmpty(until)
                        ? context.getString(R.string.record_status_temporary_ban)
                        : context.getString(R.string.record_status_temporary_ban_until, until);
            case "permanent_ban":
            case "report_ban":
                return context.getString(R.string.record_status_permanent_ban);
            case "review":
                return context.getString(R.string.record_status_review);
            default:
                return context.getString(R.string.record_type_punishment);
        }
    }

    public String getCategoryText(Context context) {
        if (TextUtils.isEmpty(violationType)) {
            return "";
        }
        int nameRes;
        switch (violationType) {
            case "fraud":
                nameRes = R.string.record_category_fraud;
                break;
            case "porn":
                nameRes = R.string.record_category_porn;
                break;
            case "gambling":
                nameRes = R.string.record_category_gambling;
                break;
            case "drugs":
                nameRes = R.string.record_category_drugs;
                break;
            default:
                nameRes = R.string.record_category_other;
                break;
        }
        return context.getString(R.string.record_category_format, context.getString(nameRes));
    }

    public String getReasonText(Context context) {
        if (isComplaint() || TextUtils.isEmpty(reason)) {
            return "";
        }
        return context.getString(R.string.record_reason_format, reason);
    }

    public String getCreatedTimeText() {
        return formatTime(createdAt);
    }

    private static String formatTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String format : formats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(format, Locale.US);
                parser.setLenient(false);
                Date date = parser.parse(value);
                if (date != null) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date);
                }
            } catch (Exception ignored) {
            }
        }
        return value;
    }
}
