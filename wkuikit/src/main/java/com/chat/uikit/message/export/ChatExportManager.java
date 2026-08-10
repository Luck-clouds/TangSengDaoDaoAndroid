package com.chat.uikit.message.export;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ChatExportManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int BUFFER_SIZE = 64 * 1024;

    private ChatExportManager() {
    }

    public static ExportHandle export(Context context, Uri target, boolean includeFiles, Callback callback) {
        Context appContext = context.getApplicationContext();
        ExportHandle handle = new ExportHandle(appContext, target);
        handle.future = EXECUTOR.submit(() -> runExport(appContext, target, includeFiles, callback, handle));
        return handle;
    }

    private static void runExport(Context context, Uri target, boolean includeFiles,
                                  Callback callback, ExportHandle handle) {
        File snapshot = null;
        try {
            String uid = WKConfig.getInstance().getUid();
            if (TextUtils.isEmpty(uid)) {
                throw new IllegalStateException("Not logged in");
            }
            postProgress(callback, 10);
            snapshot = createDatabaseSnapshot(context, uid);
            checkCancelled(handle);

            List<WKMsg> messages = WKIM.getInstance().getMsgManager().getAll();
            if (messages == null || messages.isEmpty()) {
                throw new NoChatDataException();
            }
            AttachmentCollection attachments = includeFiles
                    ? collectAttachments(context, messages)
                    : new AttachmentCollection();
            postProgress(callback, 35);
            checkCancelled(handle);

            ExportResult result = writeZip(context, target, snapshot, messages.size(), attachments, callback, handle);
            postProgress(callback, 100);
            MAIN.post(() -> callback.onSuccess(result));
        } catch (NoChatDataException e) {
            deleteTarget(context, target);
            MAIN.post(callback::onNoData);
        } catch (CancelledException e) {
            deleteTarget(context, target);
        } catch (Exception e) {
            deleteTarget(context, target);
            if (!handle.cancelled.get()) {
                MAIN.post(callback::onFail);
            }
        } finally {
            if (snapshot != null && snapshot.exists()) {
                //noinspection ResultOfMethodCallIgnored
                snapshot.delete();
            }
        }
    }

    private static File createDatabaseSnapshot(Context context, String uid) throws Exception {
        File dir = new File(context.getCacheDir(), "chat_export");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create export cache");
        }
        cleanupOldSnapshots(dir);
        File snapshot = new File(dir, UUID.randomUUID() + ".db");
        if (snapshot.exists() && !snapshot.delete()) {
            throw new IOException("Cannot reset snapshot");
        }

        try {
            SQLiteDatabase database = WKIMApplication.getInstance().getDbHelper().getDb();
            if (database == null || !database.isOpen()) {
                throw new IllegalStateException("Message database unavailable");
            }
            String path = sqlValue(snapshot.getAbsolutePath());
            String password = sqlValue(uid);
            boolean attached = false;
            synchronized (database) {
                try {
                    database.execSQL("ATTACH DATABASE '" + path + "' AS chat_export KEY '" + password + "'");
                    attached = true;
                    try (Cursor cursor = database.rawQuery("SELECT sqlcipher_export('chat_export')", new Object[0])) {
                        if (cursor != null) {
                            cursor.moveToFirst();
                        }
                    }
                } finally {
                    if (attached) {
                        database.execSQL("DETACH DATABASE chat_export");
                    }
                }
            }
            if (!snapshot.isFile() || snapshot.length() == 0) {
                throw new IOException("Empty database snapshot");
            }
            return snapshot;
        } catch (Exception e) {
            if (snapshot.exists()) {
                //noinspection ResultOfMethodCallIgnored
                snapshot.delete();
            }
            throw e;
        }
    }

    private static void cleanupOldSnapshots(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long expireBefore = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < expireBefore) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static AttachmentCollection collectAttachments(Context context, List<WKMsg> messages) {
        AttachmentCollection result = new AttachmentCollection();
        Set<String> paths = new HashSet<>();
        List<File> roots = allowedRoots(context);
        for (WKMsg message : messages) {
            if (message == null || message.isDeleted == 1) {
                continue;
            }
            try {
                WKMessageContent content = message.baseContentMsgModel;
                if (content != null) {
                    collectPaths(content.encodeMsg(), paths);
                }
                if (!TextUtils.isEmpty(message.content)) {
                    collectPaths(new JSONObject(message.content), paths);
                }
            } catch (Exception ignored) {
            }
        }
        for (String path : paths) {
            try {
                File file = new File(path).getCanonicalFile();
                if (file.isFile() && file.canRead() && isUnderRoots(file, roots)) {
                    result.files.add(file);
                } else {
                    result.missingCount++;
                }
            } catch (Exception ignored) {
                result.missingCount++;
            }
        }
        return result;
    }

    private static void collectPaths(Object value, Set<String> paths) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                if (("localPath".equalsIgnoreCase(key) || "coverLocalPath".equalsIgnoreCase(key))
                        && child instanceof String && !TextUtils.isEmpty((String) child)) {
                    paths.add((String) child);
                } else {
                    collectPaths(child, paths);
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                collectPaths(array.opt(i), paths);
            }
        }
    }

    private static ExportResult writeZip(Context context, Uri target, File snapshot, int messageCount,
                                         AttachmentCollection attachments, Callback callback,
                                         ExportHandle handle) throws Exception {
        int exportedFiles = 0;
        int missingFiles = attachments.missingCount;
        Set<String> usedEntries = new HashSet<>();
        OutputStream targetStream = context.getContentResolver().openOutputStream(target, "w");
        if (targetStream == null) {
            throw new IOException("Cannot open target");
        }
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(targetStream))) {
            addFile(zip, snapshot, "database/messages.db", handle);
            int index = 0;
            int total = attachments.files.size();
            for (File file : attachments.files) {
                checkCancelled(handle);
                String entry = uniqueEntry(file, usedEntries);
                try {
                    addFile(zip, file, entry, handle);
                    exportedFiles++;
                } catch (IOException e) {
                    missingFiles++;
                }
                index++;
                postProgress(callback, 40 + (total == 0 ? 50 : (index * 50 / total)));
            }
            addMeta(zip, messageCount, exportedFiles, missingFiles);
        }
        return new ExportResult(messageCount, exportedFiles, missingFiles);
    }

    private static void addFile(ZipOutputStream zip, File file, String entryName,
                                ExportHandle handle) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        byte[] buffer = new byte[BUFFER_SIZE];
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled(handle);
                zip.write(buffer, 0, count);
            }
        } finally {
            zip.closeEntry();
        }
    }

    private static void addMeta(ZipOutputStream zip, int messageCount, int fileCount,
                                int missingCount) throws IOException {
        zip.putNextEntry(new ZipEntry("backup.info"));
        DataOutputStream output = new DataOutputStream(zip);
        output.writeInt(0x5148494D);
        output.writeInt(1);
        output.writeLong(System.currentTimeMillis());
        output.writeInt(messageCount);
        output.writeInt(fileCount);
        output.writeInt(missingCount);
        output.flush();
        zip.closeEntry();
    }

    private static String uniqueEntry(File file, Set<String> usedEntries) throws Exception {
        String name = sanitize(file.getName());
        String hash = sha256(file.getCanonicalPath()).substring(0, 16);
        String entry = "files/" + hash + "_" + name;
        int suffix = 1;
        while (!usedEntries.add(entry)) {
            entry = "files/" + hash + "_" + suffix++ + "_" + name;
        }
        return entry;
    }

    private static List<File> allowedRoots(Context context) {
        ArrayList<File> roots = new ArrayList<>();
        addRoot(roots, context.getFilesDir());
        addRoot(roots, context.getCacheDir());
        addRoot(roots, context.getExternalFilesDir(null));
        addRoot(roots, context.getExternalCacheDir());
        File[] external = context.getExternalFilesDirs(null);
        if (external != null) {
            Collections.addAll(roots, external);
        }
        return roots;
    }

    private static void addRoot(List<File> roots, File root) {
        if (root == null) {
            return;
        }
        try {
            roots.add(root.getCanonicalFile());
        } catch (IOException ignored) {
        }
    }

    private static boolean isUnderRoots(File file, List<File> roots) {
        String path = file.getPath();
        for (File root : roots) {
            String rootPath = root.getPath();
            if (path.equals(rootPath) || path.startsWith(rootPath + File.separator)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String name) {
        String safe = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return TextUtils.isEmpty(safe) ? "file" : safe;
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : digest) {
            builder.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    private static String sqlValue(String value) {
        return value.replace("'", "''");
    }

    private static void postProgress(Callback callback, int progress) {
        MAIN.post(() -> callback.onProgress(progress));
    }

    private static void checkCancelled(ExportHandle handle) throws CancelledException {
        if (handle.cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new CancelledException();
        }
    }

    private static void deleteTarget(Context context, Uri target) {
        try {
            context.getContentResolver().delete(target, null, null);
        } catch (Exception ignored) {
        }
    }

    public interface Callback {
        void onProgress(int progress);

        void onSuccess(ExportResult result);

        void onNoData();

        void onFail();
    }

    public static final class ExportResult {
        public final int messageCount;
        public final int fileCount;
        public final int missingCount;

        ExportResult(int messageCount, int fileCount, int missingCount) {
            this.messageCount = messageCount;
            this.fileCount = fileCount;
            this.missingCount = missingCount;
        }
    }

    public static final class ExportHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Context context;
        private final Uri target;
        private Future<?> future;

        private ExportHandle(Context context, Uri target) {
            this.context = context;
            this.target = target;
        }

        public void cancel() {
            cancelled.set(true);
            if (future != null) {
                future.cancel(true);
            }
            deleteTarget(context, target);
        }
    }

    private static final class CancelledException extends Exception {
    }

    private static final class NoChatDataException extends Exception {
    }

    private static final class AttachmentCollection {
        private final LinkedHashSet<File> files = new LinkedHashSet<>();
        private int missingCount;
    }
}
