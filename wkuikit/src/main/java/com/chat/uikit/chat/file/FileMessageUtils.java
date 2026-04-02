package com.chat.uikit.chat.file;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.chat.base.config.WKConstants;
import com.chat.base.utils.WKFileUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.chat.manager.WKSendMsgUtils;
import com.chat.uikit.chat.msgmodel.WKFileContent;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKMsg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileMessageUtils {
    private static final DecimalFormat FILE_SIZE_FORMAT = new DecimalFormat("0.##");

    private FileMessageUtils() {
    }

    @NonNull
    public static WKFileContent createFileContent(@NonNull String localPath, @Nullable String displayName) {
        File file = new File(localPath);
        WKFileContent content = new WKFileContent();
        content.localPath = localPath;
        content.name = !TextUtils.isEmpty(displayName) ? displayName : file.getName();
        content.size = file.length();
        content.ext = getFileExtension(content.name);
        return content;
    }

    public static boolean sendFileMessage(@NonNull Context context, @NonNull String channelId, byte channelType, @NonNull String localPath, @Nullable String displayName) {
        if (TextUtils.isEmpty(localPath) || !new File(localPath).exists()) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.file_not_exists));
            return false;
        }
        if (WKFileUtils.getInstance().isFileOverSize(context, localPath)) {
            return false;
        }
        WKFileContent content = createFileContent(localPath, displayName);
        WKMsg wkMsg = new WKMsg();
        wkMsg.channelID = channelId;
        wkMsg.channelType = channelType;
        wkMsg.type = content.type;
        wkMsg.baseContentMsgModel = content;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel == null) {
            channel = new WKChannel(channelId, channelType);
        }
        wkMsg.setChannelInfo(channel);
        WKSendMsgUtils.getInstance().sendMessage(wkMsg);
        return true;
    }

    @Nullable
    public static String ensureLocalPath(@NonNull Context context, @Nullable Uri uri, @Nullable String currentPath, @Nullable String displayName) {
        if (!TextUtils.isEmpty(currentPath)) {
            File file = new File(currentPath);
            if (file.exists() && file.length() > 0L) {
                return file.getAbsolutePath();
            }
        }
        if (uri == null) {
            return null;
        }
        String choosePath = WKFileUtils.getInstance().getChooseFileResultPath(context, uri);
        if (!TextUtils.isEmpty(choosePath)) {
            File file = new File(choosePath);
            if (file.exists() && file.length() > 0L) {
                return choosePath;
            }
        }
        String fileName = !TextUtils.isEmpty(displayName) ? displayName : WKFileUtils.getInstance().getFileName(context, uri);
        File localFile = WKFileUtils.getInstance().generateFileName(fileName);
        if (localFile == null) {
            return null;
        }
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(localFile)) {
            if (inputStream == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            return localFile.getAbsolutePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    public static String buildDownloadPath(@Nullable String fileName) {
        String safeName = TextUtils.isEmpty(fileName) ? "file" : fileName;
        File file = WKFileUtils.getInstance().generateFileName(safeName);
        if (file != null) {
            return file.getAbsolutePath();
        }
        return WKConstants.chatDownloadFileDir + System.currentTimeMillis() + "_" + safeName;
    }

    public static boolean isLocalFileAvailable(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.length() > 0L;
    }

    public static void openFile(@NonNull Context context, @NonNull String filePath) {
        try {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileProvider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, getMimeType(context, filePath));
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_file)));
        } catch (ActivityNotFoundException e) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.file_open_failed));
        } catch (Exception e) {
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.file_open_failed));
        }
    }

    @NonNull
    public static String formatFileSize(long size) {
        if (size <= 0) {
            return "0.00KB";
        }
        if (size < 1024) {
            return FILE_SIZE_FORMAT.format(size) + "B";
        }
        if (size < 1024 * 1024) {
            return FILE_SIZE_FORMAT.format(size / 1024d) + "KB";
        }
        if (size < 1024 * 1024 * 1024L) {
            return FILE_SIZE_FORMAT.format(size / 1024d / 1024d) + "M";
        }
        return FILE_SIZE_FORMAT.format(size / 1024d / 1024d / 1024d) + "G";
    }

    @NonNull
    public static String formatFileTime(long modifiedAt) {
        return new SimpleDateFormat("M-d HH:mm", Locale.getDefault()).format(new Date(modifiedAt));
    }

    @NonNull
    public static String getFileExtension(@Nullable String fileName) {
        if (TextUtils.isEmpty(fileName) || !fileName.contains(".")) {
            return "";
        }
        int index = fileName.lastIndexOf(".");
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toUpperCase(Locale.getDefault());
    }

    @NonNull
    public static String getFileBadgeText(@Nullable String fileName) {
        String extension = getFileExtension(fileName);
        if (TextUtils.isEmpty(extension)) {
            return "FILE";
        }
        if (extension.length() > 4) {
            return extension.substring(0, 4);
        }
        return extension;
    }

    @NonNull
    private static String getMimeType(@NonNull Context context, @NonNull String path) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        if (!TextUtils.isEmpty(extension)) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.getDefault()));
            if (!TextUtils.isEmpty(mimeType)) {
                return mimeType;
            }
        }
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolver.getType(Uri.fromFile(new File(path)));
        return TextUtils.isEmpty(mimeType) ? "*/*" : mimeType;
    }
}
