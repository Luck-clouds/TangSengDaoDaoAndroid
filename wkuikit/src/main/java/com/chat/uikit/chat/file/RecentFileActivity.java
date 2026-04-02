package com.chat.uikit.chat.file;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActRecentFileLayoutBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecentFileActivity extends WKBaseActivity<ActRecentFileLayoutBinding> {
    private static final int MAX_COUNT = 120;

    private final ActivityResultLauncher<Intent> openDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) {
                    return;
                }
                sendWithUri(uri, null, null);
            }
    );

    private RecentFileAdapter adapter;
    private String channelId;
    private byte channelType;
    private RecentFileEntity selectedItem;

    @Override
    protected ActRecentFileLayoutBinding getViewBinding() {
        return ActRecentFileLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.recent_file);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(com.chat.base.R.string.str_send);
    }

    @Override
    protected void initView() {
        channelId = getIntent().getStringExtra("channelId");
        channelType = getIntent().getByteExtra("channelType", (byte) 1);
        adapter = new RecentFileAdapter(new RecentFileAdapter.IListener() {
            @Override
            public void onOpenDetail(@NonNull RecentFileEntity item) {
                openDetail(item);
            }

            @Override
            public void onSelect(@NonNull RecentFileEntity item, boolean selected) {
                selectedItem = selected ? item : null;
                if (selected && item != null) {
                    adapter.setSelectedIdentity(item.getIdentity());
                } else {
                    adapter.setSelectedIdentity(null);
                }
                updateSendState();
            }
        });
        initAdapter(wkVBinding.recyclerView, adapter);
        updateSendState();
        requestRecentFiles();
    }

    @Override
    protected void initListener() {
        wkVBinding.chooseSystemFileTv.setOnClickListener(v -> openSystemFilePicker());
    }

    @Override
    protected void rightLayoutClick() {
        if (selectedItem == null) {
            return;
        }
        sendWithUri(TextUtils.isEmpty(selectedItem.uriString) ? null : Uri.parse(selectedItem.uriString), selectedItem.path, selectedItem.name);
    }

    private void requestRecentFiles() {
        String[] permissionStr = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        if (Build.VERSION.SDK_INT >= 33) {
            permissionStr = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO};
        }
        String desc = String.format(getString(com.chat.base.R.string.file_permissions_des), getString(com.chat.base.R.string.app_name));
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                if (result) {
                    loadRecentFiles();
                } else {
                    showEmptyState(new ArrayList<>());
                }
            }

            @Override
            public void clickResult(boolean isCancel) {
            }
        }, this, desc, permissionStr);
    }

    private void loadRecentFiles() {
        new Thread(() -> {
            List<RecentFileEntity> list = queryRecentFiles();
            runOnUiThread(() -> showEmptyState(list));
        }).start();
    }

    private void showEmptyState(@NonNull List<RecentFileEntity> list) {
        adapter.setList(list);
        wkVBinding.nodataTv.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private List<RecentFileEntity> queryRecentFiles() {
        List<RecentFileEntity> list = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        String[] projection = new String[]{
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
        };
        Uri contentUri = MediaStore.Files.getContentUri("external");
        String selection = MediaStore.Files.FileColumns.SIZE + " > 0 AND " + MediaStore.Files.FileColumns.DISPLAY_NAME + " IS NOT NULL";
        try (Cursor cursor = getContentResolver().query(contentUri, projection, selection, null, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return list;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);
            int modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED);
            int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
            while (cursor.moveToNext() && list.size() < MAX_COUNT) {
                String name = cursor.getString(nameIndex);
                if (TextUtils.isEmpty(name) || name.startsWith(".")) {
                    continue;
                }
                String path = cursor.getString(dataIndex);
                if (!TextUtils.isEmpty(path)) {
                    File file = new File(path);
                    if (!file.exists() || file.isDirectory()) {
                        continue;
                    }
                }
                long id = cursor.getLong(idIndex);
                RecentFileEntity entity = new RecentFileEntity();
                entity.name = name;
                entity.size = cursor.getLong(sizeIndex);
                entity.modifiedAt = cursor.getLong(modifiedIndex) * 1000L;
                entity.path = path;
                entity.uriString = ContentUris.withAppendedId(contentUri, id).toString();
                String identity = entity.getIdentity();
                if (TextUtils.isEmpty(identity) || identities.contains(identity)) {
                    continue;
                }
                identities.add(identity);
                list.add(entity);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private void updateSendState() {
        View titleRightLayout = findViewById(com.chat.base.R.id.titleRightLayout);
        TextView titleRightTv = findViewById(com.chat.base.R.id.titleRightTv);
        boolean enabled = selectedItem != null;
        if (titleRightLayout != null) {
            titleRightLayout.setEnabled(enabled);
            titleRightLayout.setAlpha(enabled ? 1f : 0.4f);
        }
        if (titleRightTv != null) {
            titleRightTv.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void openSystemFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        openDocumentLauncher.launch(intent);
    }

    private void openDetail(@NonNull RecentFileEntity item) {
        Intent intent = new Intent(this, FileDetailActivity.class);
        intent.putExtra(FileDetailActivity.EXTRA_NAME, item.name);
        intent.putExtra(FileDetailActivity.EXTRA_SIZE, item.size);
        intent.putExtra(FileDetailActivity.EXTRA_LOCAL_PATH, item.path);
        intent.putExtra(FileDetailActivity.EXTRA_URI, item.uriString);
        intent.putExtra(FileDetailActivity.EXTRA_FROM_RECENT, true);
        startActivity(intent);
    }

    private void sendWithUri(@Nullable Uri uri, @Nullable String currentPath, @Nullable String displayName) {
        new Thread(() -> {
            String path = FileMessageUtils.ensureLocalPath(this, uri, currentPath, displayName);
            runOnUiThread(() -> {
                if (TextUtils.isEmpty(path)) {
                    WKToastUtils.getInstance().showToastNormal(getString(R.string.file_not_exists));
                    return;
                }
                if (FileMessageUtils.sendFileMessage(this, channelId, channelType, path, displayName)) {
                    finish();
                }
            });
        }).start();
    }
}
