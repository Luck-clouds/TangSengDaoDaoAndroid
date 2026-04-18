package com.chat.flagship.picture;

/**
 * 图片编辑入口管理
 * Created by Luckclouds .
 */

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.chat.base.endpoint.entity.EditImgMenu;
import com.chat.base.endpoint.entity.EditMsgMenu;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.flagship.R;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlagshipPictureEditorManager {
    private static final ConcurrentHashMap<String, WeakReference<EditImgMenu.IBack>> callbackMap = new ConcurrentHashMap<>();

    private FlagshipPictureEditorManager() {
    }

    private static class Binder {
        private static final FlagshipPictureEditorManager INSTANCE = new FlagshipPictureEditorManager();
    }

    public static FlagshipPictureEditorManager getInstance() {
        return Binder.INSTANCE;
    }

    public void openByEditImg(EditImgMenu menu) {
        if (menu == null) {
            return;
        }
        Context context = menu.context;
        if (context == null && menu.fragment != null) {
            context = menu.fragment.getContext();
        }
        if (context == null) {
            return;
        }
        String callbackKey = null;
        if (menu.iBack != null) {
            callbackKey = UUID.randomUUID().toString();
            callbackMap.put(callbackKey, new WeakReference<>(menu.iBack));
        }
        startEditor(context, menu.path, false, menu.isShowSaveDialog, callbackKey);
    }

    public void openByEditMsg(EditMsgMenu menu) {
        if (menu == null || menu.getContext() == null) {
            return;
        }
        resolveEditorPath(menu.getContext(), menu.getUrl(), path -> startEditor(menu.getContext(), path, true, true, null));
    }

    private void startEditor(Context context, String path, boolean fromViewer, boolean showSaveDialog, String callbackKey) {
        Intent intent = new Intent(context, FlagshipPictureEditorActivity.class);
        intent.putExtra(FlagshipPictureEditorActivity.EXTRA_PATH, path);
        intent.putExtra(FlagshipPictureEditorActivity.EXTRA_FROM_VIEWER, fromViewer);
        intent.putExtra(FlagshipPictureEditorActivity.EXTRA_SHOW_SAVE_DIALOG, showSaveDialog);
        intent.putExtra(FlagshipPictureEditorActivity.EXTRA_CALLBACK_KEY, callbackKey);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public void dispatchEditResult(Context context, String callbackKey, String path) {
        if (callbackKey == null) {
            return;
        }
        WeakReference<EditImgMenu.IBack> reference = callbackMap.remove(callbackKey);
        if (reference == null) {
            return;
        }
        EditImgMenu.IBack callback = reference.get();
        if (callback != null) {
            callback.onBack(null, path);
        } else if (context != null) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.flagship_picture_edit_callback_expired));
        }
    }

    private void resolveEditorPath(Context context, String path, PathCallback callback) {
        if (context == null || TextUtils.isEmpty(path) || callback == null) {
            return;
        }
        if (!isRemotePath(path)) {
            callback.onResult(path);
            return;
        }
        Glide.with(context)
                .asBitmap()
                .load(path)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        ImageUtils.getInstance().saveBitmap(context, resource, false, callback::onResult);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        WKToastUtils.getInstance().showToastNormal(context.getString(com.chat.base.R.string.file_read_failed));
                    }
                });
    }

    private boolean isRemotePath(String path) {
        return !TextUtils.isEmpty(path) && (path.startsWith("http://") || path.startsWith("https://"));
    }

    private interface PathCallback {
        void onResult(String path);
    }
}
