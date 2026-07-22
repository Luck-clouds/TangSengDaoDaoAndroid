package com.chat.base.utils;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.chat.base.R;
import com.chat.base.ui.Theme;
import com.chat.base.utils.rxpermissions.RxPermissions;

public class WKPermissions {
    private WKPermissions() {
    }

    private static class PermissionsBinder {
        final static WKPermissions permissions = new WKPermissions();
    }

    public static WKPermissions getInstance() {
        return PermissionsBinder.permissions;
    }

    public void checkPermissions(final IPermissionResult iPermissionResult, FragmentActivity activity, String authDesc, String... permissions) {
        if (hasPermissions(activity, permissions)) {
            iPermissionResult.onResult(true);
            return;
        }
        String purpose = buildPermissionPurpose(activity, permissions);
        WKDialogUtils.getInstance().showDialog(activity,
                activity.getString(R.string.authorization_request),
                purpose,
                false,
                activity.getString(R.string.cancel),
                activity.getString(R.string.sure),
                0,
                Theme.colorAccount,
                index -> {
                    if (index == 1) {
                        requestSystemPermissions(iPermissionResult, activity, authDesc, permissions);
                    } else {
                        iPermissionResult.clickResult(true);
                        iPermissionResult.onResult(false);
                    }
                });
    }

    public boolean hasPermissions(FragmentActivity activity, String... permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        if (permissions == null || permissions.length == 0) return true;
        boolean partialMediaAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                == PackageManager.PERMISSION_GRANTED;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                if (partialMediaAccess && isVisualMediaPermission(permission)) continue;
                return false;
            }
        }
        return true;
    }

    private void requestSystemPermissions(final IPermissionResult iPermissionResult, FragmentActivity activity, String authDesc, String... permissions) {
        RxPermissions rxPermissions = new RxPermissions(activity);
        rxPermissions.request(permissions).subscribe(aBoolean -> {
            boolean granted = aBoolean || hasPermissions(activity, permissions);
            if (!granted) {
                WKDialogUtils.getInstance().showDialog(activity, activity.getString(R.string.authorization_request), authDesc ,false,activity.getString(R.string.cancel), activity.getString(R.string.to_set),0, Theme.colorAccount, index -> {
                    if (index == 1) {
                        Intent intent = new Intent();
                        intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    }
                    iPermissionResult.clickResult(index == 0);
                });
            }
            iPermissionResult.onResult(granted);
        });
    }

    private String buildPermissionPurpose(FragmentActivity activity, String... permissions) {
        boolean camera = contains(permissions, Manifest.permission.CAMERA);
        boolean microphone = contains(permissions, Manifest.permission.RECORD_AUDIO);
        boolean contacts = contains(permissions, Manifest.permission.READ_CONTACTS);
        boolean notification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && contains(permissions, Manifest.permission.POST_NOTIFICATIONS);
        boolean phone = contains(permissions, Manifest.permission.CALL_PHONE);
        boolean media = contains(permissions, Manifest.permission.READ_EXTERNAL_STORAGE)
                || contains(permissions, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && (contains(permissions, Manifest.permission.READ_MEDIA_IMAGES)
                || contains(permissions, Manifest.permission.READ_MEDIA_VIDEO)
                || contains(permissions, Manifest.permission.READ_MEDIA_AUDIO)))
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && contains(permissions, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED));
        CharSequence appName = activity.getApplicationInfo().loadLabel(activity.getPackageManager());
        int purposeRes;
        if (camera && microphone) {
            purposeRes = R.string.permission_purpose_camera_microphone;
        } else if (camera) {
            purposeRes = R.string.permission_purpose_camera;
        } else if (microphone) {
            purposeRes = R.string.permission_purpose_microphone;
        } else if (media) {
            purposeRes = R.string.permission_purpose_media;
        } else if (contacts) {
            purposeRes = R.string.permission_purpose_contacts;
        } else if (notification) {
            purposeRes = R.string.permission_purpose_notification;
        } else if (phone) {
            purposeRes = R.string.permission_purpose_phone;
        } else {
            purposeRes = R.string.permission_purpose_other;
        }
        return activity.getString(purposeRes, appName);
    }

    private boolean contains(String[] permissions, String target) {
        if (permissions == null) return false;
        for (String permission : permissions) {
            if (target.equals(permission)) return true;
        }
        return false;
    }

    private boolean isVisualMediaPermission(String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        if (Manifest.permission.READ_MEDIA_IMAGES.equals(permission)
                || Manifest.permission.READ_MEDIA_VIDEO.equals(permission)) return true;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED.equals(permission);
    }

    public interface IPermissionResult {
        void onResult(boolean result);

        void clickResult(boolean isCancel);
    }
}
