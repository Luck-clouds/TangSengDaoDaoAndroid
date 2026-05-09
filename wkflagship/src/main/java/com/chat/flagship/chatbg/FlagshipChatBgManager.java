package com.chat.flagship.chatbg;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.entity.SetChatBgMenu;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.SvgHelper;
import com.chat.base.utils.WKFileUtils;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天背景管理
 * Created by Luckclouds .
 */
public class FlagshipChatBgManager {
    private static final String TAG = "FlagshipChatBg";

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
            Log.w(TAG, "apply ignored: menu is null");
            return;
        }
        FlagshipChatBgConfig config = FlagshipChatBgStore.getConfig(menu.getChannelID(), menu.getChannelType());
        if (config == null || config.type == FlagshipChatBgConfig.TYPE_DEFAULT || TextUtils.isEmpty(config.localPath)) {
            Log.d(TAG, "apply default background channel=" + menu.getChannelID() + " type=" + menu.getChannelType());
            clear(menu);
            return;
        }
        File file = new File(config.localPath);
        if (!file.exists()) {
            Log.w(TAG, "apply failed: local file missing path=" + config.localPath);
            FlagshipChatBgStore.clearConfig(menu.getChannelID(), menu.getChannelType());
            clear(menu);
            return;
        }
        String applyKey = config.localPath + "#" + config.blur + "#" + config.isSvg + "#" + config.gradientStep + "#" + config.showPattern;
        if (TextUtils.equals(String.valueOf(menu.getBackGroundIV().getTag()), applyKey)) {
            Log.d(TAG, "apply skipped same key=" + applyKey);
            menu.getBlurView().setVisibility(config.blur ? View.VISIBLE : View.GONE);
            return;
        }
        Log.d(TAG, "apply background key=" + applyKey + " exists=" + file.exists());
        menu.getBackGroundIV().setTag(applyKey);
        menu.getBackGroundIV().setScaleType(ImageView.ScaleType.CENTER_CROP);
        menu.getBlurView().setVisibility(config.blur ? View.VISIBLE : View.GONE);
        if (config.isSvg == 1) {
            applyGradientBackground(menu.getBackGroundIV(), config.lightColors, config.darkColors, config.gradientStep);
            if (config.showPattern) {
                renderLocalSvgAsync(menu, file, applyKey);
            } else {
                menu.getBackGroundIV().setImageDrawable(null);
            }
        } else {
            menu.getBackGroundIV().setBackground(null);
            GlideUtils.getInstance().showImg(menu.getBackGroundIV().getContext(), file.getAbsolutePath(), menu.getBackGroundIV());
            menu.getBackGroundIV().setAlpha(0.96f);
            menu.getBackGroundIV().animate().alpha(1f).setDuration(140).start();
        }
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
            config.lightColors = item.lightColors;
            config.darkColors = item.darkColors;
        }
        config.blur = false;
        return config;
    }

    public String resolveRemoteAssetUrl(String path) {
        if (TextUtils.isEmpty(path) || path.startsWith("http") || path.startsWith("HTTP")) {
            Log.d(TAG, "resolveRemoteAssetUrl direct path=" + path);
            return path;
        }
        String baseUrl = WKApiConfig.baseUrl;
        if (TextUtils.isEmpty(baseUrl)) {
            Log.w(TAG, "resolveRemoteAssetUrl baseUrl empty path=" + path);
            return path;
        }
        if (baseUrl.endsWith("/v1/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 4);
        } else if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        String result = path.startsWith("/") ? baseUrl + path : baseUrl + "/" + path;
        Log.d(TAG, "resolveRemoteAssetUrl path=" + path + " resolved=" + result);
        return result;
    }

    public List<String> buildRemoteAssetCandidates(String path) {
        List<String> candidates = new ArrayList<>();
        if (TextUtils.isEmpty(path)) {
            return candidates;
        }
        String baseUrl = WKApiConfig.baseUrl;
        String host = baseUrl;
        if (!TextUtils.isEmpty(host)) {
            if (host.endsWith("/v1/")) {
                host = host.substring(0, host.length() - 4);
            } else if (host.endsWith("/v1")) {
                host = host.substring(0, host.length() - 3);
            }
        }
        addCandidate(candidates, resolveRemoteAssetUrl(path));
        if (path.startsWith("http://") || path.startsWith("https://")) {
            addCandidate(candidates, path);
        }
        if (!TextUtils.isEmpty(host)) {
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            addCandidate(candidates, host + "/" + normalized);
            if (normalized.startsWith("file/preview/")) {
                String stripped = normalized.substring("file/preview/".length());
                addCandidate(candidates, host + "/" + stripped);
                addCandidate(candidates, host + "/v1/" + stripped);
                addCandidate(candidates, host + "/v1/file/preview/" + stripped);
                addCandidate(candidates, host + "/file/" + stripped);
                if (stripped.startsWith("common/")) {
                    String objectStorageHost = buildObjectStorageHost(host);
                    addCandidate(candidates, objectStorageHost + "/" + stripped);
                }
            }
        }
        Log.d(TAG, "buildRemoteAssetCandidates path=" + path + " candidates=" + candidates);
        return candidates;
    }

    public String createCacheTargetPath(String originalSource, int isSvg) {
        String extension = resolveExtension(originalSource, isSvg);
        File dir = new File(WKConstants.chatBgCacheDir);
        WKFileUtils.getInstance().createFileDir(dir);
        return new File(dir, "chat_bg_" + System.currentTimeMillis() + extension).getAbsolutePath();
    }

    public void loadPreviewImage(@NonNull ImageView imageView, FlagshipChatBgItem item) {
        if (item == null) {
            Log.w(TAG, "loadPreviewImage ignored: item is null");
            imageView.setImageDrawable(null);
            return;
        }
        String previewPath = !TextUtils.isEmpty(item.cover) ? item.cover : item.url;
        Log.d(TAG, "loadPreviewImage isSvg=" + item.isSvg + " cover=" + item.cover + " url=" + item.url + " previewPath=" + previewPath);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackground(null);
        loadRemoteBitmapPreview(imageView, buildRemoteAssetCandidates(previewPath));
    }

    private void clear(SetChatBgMenu menu) {
        Log.d(TAG, "clear background");
        menu.getBackGroundIV().setImageDrawable((Drawable) null);
        menu.getBackGroundIV().setBackground(null);
        menu.getBackGroundIV().setTag(null);
        menu.getBlurView().setVisibility(View.GONE);
    }

    private void renderLocalSvgAsync(@NonNull SetChatBgMenu menu, @NonNull File file, @NonNull String applyKey) {
        new Thread(() -> {
            Bitmap bitmap = renderSvg(file, menu.getBackGroundIV());
            menu.getBackGroundIV().post(() -> {
                if (!TextUtils.equals(String.valueOf(menu.getBackGroundIV().getTag()), applyKey)) {
                    Log.d(TAG, "renderLocalSvgAsync skipped stale key=" + applyKey);
                    return;
                }
                if (bitmap != null) {
                    Log.d(TAG, "renderLocalSvgAsync success path=" + file.getAbsolutePath() + " width=" + bitmap.getWidth() + " height=" + bitmap.getHeight());
                    menu.getBackGroundIV().setImageBitmap(bitmap);
                    menu.getBackGroundIV().setAlpha(0.94f);
                    menu.getBackGroundIV().animate().alpha(1f).setDuration(180).start();
                } else {
                    Log.e(TAG, "renderLocalSvgAsync failed path=" + file.getAbsolutePath());
                    clear(menu);
                }
            });
        }).start();
    }

    private void loadRemoteSvgPreview(@NonNull ImageView imageView, @NonNull List<String> candidates) {
        String tag = candidates.toString();
        imageView.setTag(tag);
        new Thread(() -> {
            String xml = null;
            String successUrl = null;
            for (String candidate : candidates) {
                xml = downloadText(candidate);
                if (!TextUtils.isEmpty(xml)) {
                    successUrl = candidate;
                    break;
                }
            }
            Bitmap bitmap = null;
            if (!TextUtils.isEmpty(xml)) {
                Log.d(TAG, "loadRemoteSvgPreview xmlLength=" + xml.length() + " url=" + successUrl);
                bitmap = renderRemoteSvg(xml, imageView);
            }
            Bitmap finalBitmap = bitmap;
            imageView.post(() -> {
                if (!TextUtils.equals(String.valueOf(imageView.getTag()), tag)) {
                    Log.d(TAG, "loadRemoteSvgPreview skipped stale tag=" + tag);
                    return;
                }
                if (finalBitmap != null) {
                    Log.d(TAG, "loadRemoteSvgPreview success width=" + finalBitmap.getWidth() + " height=" + finalBitmap.getHeight());
                    imageView.setImageBitmap(finalBitmap);
                } else {
                    Log.e(TAG, "loadRemoteSvgPreview failed candidates=" + candidates);
                    imageView.setImageDrawable(null);
                }
            });
        }).start();
    }

    private void loadRemoteBitmapPreview(@NonNull ImageView imageView, @NonNull List<String> candidates) {
        String tag = candidates.toString();
        imageView.setTag(tag);
        new Thread(() -> {
            Bitmap bitmap = null;
            String successUrl = null;
            for (String candidate : candidates) {
                bitmap = downloadBitmap(candidate);
                if (bitmap != null) {
                    successUrl = candidate;
                    break;
                }
            }
            Bitmap finalBitmap = bitmap;
            String finalSuccessUrl = successUrl;
            imageView.post(() -> {
                if (!TextUtils.equals(String.valueOf(imageView.getTag()), tag)) {
                    Log.d(TAG, "loadRemoteBitmapPreview skipped stale tag=" + tag);
                    return;
                }
                if (finalBitmap != null) {
                    Log.d(TAG, "loadRemoteBitmapPreview success url=" + finalSuccessUrl + " width=" + finalBitmap.getWidth() + " height=" + finalBitmap.getHeight());
                    imageView.setImageBitmap(finalBitmap);
                } else {
                    Log.e(TAG, "loadRemoteBitmapPreview failed candidates=" + candidates);
                    imageView.setImageDrawable(null);
                }
            });
        }).start();
    }

    private Bitmap renderSvg(File file, ImageView imageView) {
        DisplayMetrics metrics = imageView.getResources().getDisplayMetrics();
        int width = imageView.getWidth() > 0 ? imageView.getWidth() : metrics.widthPixels;
        int height = imageView.getHeight() > 0 ? imageView.getHeight() : Math.max(metrics.heightPixels, width);
        Log.d(TAG, "renderSvg local width=" + width + " height=" + height + " path=" + file.getAbsolutePath());
        return SvgHelper.getBitmap(file, width, height, false);
    }

    private Bitmap renderRemoteSvg(String xml, ImageView imageView) {
        DisplayMetrics metrics = imageView.getResources().getDisplayMetrics();
        int width = imageView.getWidth() > 0 ? imageView.getWidth() : Math.max(metrics.widthPixels / 3, 240);
        int height = imageView.getHeight() > 0 ? imageView.getHeight() : Math.max((int) (width * 16f / 9f), 360);
        Log.d(TAG, "renderRemoteSvg width=" + width + " height=" + height);
        return SvgHelper.getBitmap(xml, width, height, false);
    }

    public void applyGradientBackground(@NonNull ImageView imageView, List<String> lightColors, List<String> darkColors) {
        applyGradientBackground(imageView, lightColors, darkColors, 0);
    }

    public void applyGradientBackground(@NonNull ImageView imageView, List<String> lightColors, List<String> darkColors, int gradientStep) {
        int[] colors = resolveGradientColors(lightColors, darkColors, gradientStep);
        if (colors == null || colors.length == 0) {
            imageView.setBackground(null);
            return;
        }
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        imageView.setBackground(drawable);
    }

    public void loadRemoteSvgPattern(@NonNull ImageView imageView, String remotePath) {
        loadRemoteSvgPreview(imageView, buildRemoteAssetCandidates(remotePath));
    }

    private Bitmap downloadBitmap(String remoteUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            Log.d(TAG, "downloadBitmap start url=" + remoteUrl);
            connection = (HttpURLConnection) new URL(remoteUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.connect();
            int code = connection.getResponseCode();
            Log.d(TAG, "downloadBitmap response code=" + code + " url=" + remoteUrl + " contentType=" + connection.getContentType());
            if (code / 100 != 2) {
                return null;
            }
            inputStream = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                Log.d(TAG, "downloadBitmap success width=" + bitmap.getWidth() + " height=" + bitmap.getHeight() + " url=" + remoteUrl);
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "downloadBitmap exception url=" + remoteUrl + " msg=" + e.getMessage(), e);
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String downloadText(String remoteUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            Log.d(TAG, "downloadText start url=" + remoteUrl);
            connection = (HttpURLConnection) new URL(remoteUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.connect();
            int code = connection.getResponseCode();
            Log.d(TAG, "downloadText response code=" + code + " url=" + remoteUrl + " contentType=" + connection.getContentType());
            if (code / 100 != 2) {
                return null;
            }
            inputStream = connection.getInputStream();
            byte[] bytes = readStreamBytes(inputStream);
            Log.d(TAG, "downloadText bytes=" + bytes.length + " url=" + remoteUrl);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "downloadText exception url=" + remoteUrl + " msg=" + e.getMessage(), e);
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
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

    private void addCandidate(List<String> candidates, String candidate) {
        if (TextUtils.isEmpty(candidate) || candidates.contains(candidate)) {
            return;
        }
        candidates.add(candidate);
    }

    private String buildObjectStorageHost(String host) {
        try {
            URI uri = URI.create(host);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            String hostname = uri.getHost();
            if (TextUtils.isEmpty(hostname)) {
                return host;
            }
            return scheme + "://" + hostname + ":9000";
        } catch (Exception e) {
            Log.w(TAG, "buildObjectStorageHost fail host=" + host + " msg=" + e.getMessage());
            return host;
        }
    }

    private int[] resolveGradientColors(List<String> lightColors, List<String> darkColors, int gradientStep) {
        List<String> source = lightColors != null && !lightColors.isEmpty() ? lightColors : darkColors;
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<Integer> colors = new ArrayList<>();
        for (String value : source) {
            int color = parseColorSafely(value);
            if (color != Integer.MIN_VALUE) {
                colors.add(color);
            }
        }
        if (colors.isEmpty()) {
            return null;
        }
        if (colors.size() == 1) {
            colors.add(colors.get(0));
        }
        int[] result = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            result[i] = transformGradientColor(colors.get(i), gradientStep, i);
        }
        return result;
    }

    private int parseColorSafely(String value) {
        if (TextUtils.isEmpty(value)) {
            return Integer.MIN_VALUE;
        }
        String color = value.startsWith("#") ? value : "#" + value;
        try {
            return Color.parseColor(color);
        } catch (Exception e) {
            Log.w(TAG, "parseColorSafely fail value=" + value + " msg=" + e.getMessage());
            return Integer.MIN_VALUE;
        }
    }

    private int transformGradientColor(int color, int gradientStep, int index) {
        if (gradientStep == 0) {
            return color;
        }
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        int step = Math.abs(gradientStep) % 6;
        float hueShift = (step * 14f) + (index * 6f);
        hsv[0] = (hsv[0] + hueShift) % 360f;
        hsv[1] = Math.min(1f, hsv[1] * (0.92f + step * 0.03f));
        hsv[2] = Math.min(1f, hsv[2] * (0.95f + step * 0.02f));
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private byte[] readStreamBytes(@NonNull InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }
}
