package com.chat.flagship.chatbg;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.entity.SetChatBgMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.SvgHelper;
import com.chat.base.utils.WKFileUtils;

import java.io.File;

/**
 * 聊天背景管理
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgManager {

    private FlagshipChatBgManager() {
    }

    private static class Binder {
        private static final FlagshipChatBgManager INSTANCE = new FlagshipChatBgManager();
    }

    public static FlagshipChatBgManager getInstance() {
        return Binder.INSTANCE;
    }

    public void openList(@NonNull Context context, @NonNull String channelId, byte channelType) {
        Intent intent = new Intent(context, FlagshipChatBgListActivity.class);
        intent.putExtra(FlagshipChatBgListActivity.EXTRA_CHANNEL_ID, channelId);
        intent.putExtra(FlagshipChatBgListActivity.EXTRA_CHANNEL_TYPE, channelType);
        context.startActivity(intent);
    }

    public void apply(SetChatBgMenu menu) {
        if (menu == null) {
            return;
        }
        FlagshipChatBgConfig config = FlagshipChatBgStore.getConfig(menu.getChannelID(), menu.getChannelType());
        if (config == null || config.type == FlagshipChatBgConfig.TYPE_DEFAULT || TextUtils.isEmpty(config.localPath)) {
            clear(menu);
            return;
        }
        File file = new File(config.localPath);
        if (!file.exists()) {
            FlagshipChatBgStore.clearConfig(menu.getChannelID(), menu.getChannelType());
            clear(menu);
            return;
        }
        menu.getBackGroundIV().setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (config.isSvg == 1) {
            Bitmap bitmap = renderSvg(file, menu.getBackGroundIV());
            if (bitmap != null) {
                menu.getBackGroundIV().setImageBitmap(bitmap);
            } else {
                clear(menu);
            }
        } else {
            GlideUtils.getInstance().showImg(menu.getBackGroundIV().getContext(), file.getAbsolutePath(), menu.getBackGroundIV());
        }
        menu.getBlurView().setVisibility(config.blur ? View.VISIBLE : View.GONE);
    }

    public FlagshipChatBgConfig buildLocalConfig(String sourcePath, boolean blur) {
        FlagshipChatBgConfig config = new FlagshipChatBgConfig();
        config.type = FlagshipChatBgConfig.TYPE_LOCAL;
        config.localPath = sourcePath;
        config.blur = blur;
        config.isSvg = 0;
        return config;
    }

    public FlagshipChatBgConfig buildPresetConfig(FlagshipChatBgItem item) {
        FlagshipChatBgConfig config = new FlagshipChatBgConfig();
        config.type = item != null && item.isDefault ? FlagshipChatBgConfig.TYPE_DEFAULT : FlagshipChatBgConfig.TYPE_PRESET;
        if (item != null) {
            config.sourceUrl = item.url;
            config.cover = item.cover;
            config.isSvg = item.isSvg;
        }
        config.blur = false;
        return config;
    }

    public String createCacheTargetPath(String originalSource, int isSvg) {
        String extension = resolveExtension(originalSource, isSvg);
        File dir = new File(WKConstants.chatBgCacheDir);
        WKFileUtils.getInstance().createFileDir(dir);
        return new File(dir, "chat_bg_" + System.currentTimeMillis() + extension).getAbsolutePath();
    }

    private void clear(SetChatBgMenu menu) {
        menu.getBackGroundIV().setImageDrawable((Drawable) null);
        menu.getBlurView().setVisibility(View.GONE);
    }

    private Bitmap renderSvg(File file, ImageView imageView) {
        DisplayMetrics metrics = imageView.getResources().getDisplayMetrics();
        int width = imageView.getWidth() > 0 ? imageView.getWidth() : metrics.widthPixels;
        int height = imageView.getHeight() > 0 ? imageView.getHeight() : Math.max(metrics.heightPixels, width);
        return SvgHelper.getBitmap(file, width, height, false);
    }

    private String resolveExtension(String source, int isSvg) {
        if (isSvg == 1) {
            return ".svg";
        }
        if (!TextUtils.isEmpty(source)) {
            String path = source;
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                path = path.substring(0, queryIndex);
            }
            int dotIndex = path.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < path.length() - 1) {
                return path.substring(dotIndex);
            }
        }
        return ".jpg";
    }
}
