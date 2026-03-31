package com.chat.uikit.group.manage;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.chat.base.WKBaseApplication;
import com.chat.base.act.WKCropImageActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKPermissions;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActMyHeadPortraitLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GroupAvatarActivity extends WKBaseActivity<ActMyHeadPortraitLayoutBinding> {
    private String groupId;

    @Override
    protected ActMyHeadPortraitLayoutBinding getViewBinding() {
        return ActMyHeadPortraitLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_avatar);
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return R.mipmap.ic_ab_other;
    }

    @Override
    protected void initPresenter() {
        groupId = getIntent().getStringExtra(GroupManageConstants.EXTRA_GROUP_ID);
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        showBottomDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WKBaseApplication.getInstance().disconnect = true;
    }

    @Override
    protected void initView() {
        showAvatar();
    }

    @Override
    protected void initListener() {
        wkVBinding.avatarIv.setOnLongClickListener(v -> {
            showBottomDialog();
            return true;
        });
    }

    private void showAvatar() {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupId, WKChannelType.GROUP);
        String url = WKApiConfig.getGroupUrl(groupId);
        if (channel != null && !TextUtils.isEmpty(channel.channelID)) {
            GlideUtils.getInstance().showAvatarImg(this, channel.channelID, channel.channelType, channel.avatarCacheKey, wkVBinding.avatarIv);
        } else {
            GlideUtils.getInstance().showImg(this, url + "?width=500&height=500", wkVBinding.avatarIv);
        }
    }

    private void showBottomDialog() {
        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getString(R.string.update_avatar), R.mipmap.msg_edit, () -> {
            WKBaseApplication.getInstance().disconnect = false;
            chooseIMG();
        }));
        list.add(new PopupMenuItem(getString(R.string.save_img), R.mipmap.msg_download, () -> {
            String avatarURL = WKApiConfig.getGroupUrl(groupId) + "?key=" + UUID.randomUUID().toString().replace("-", "");
            ImageUtils.getInstance().downloadImg(this, avatarURL, bitmap -> {
                if (bitmap != null) {
                    ImageUtils.getInstance().saveBitmap(GroupAvatarActivity.this, bitmap, true, path -> showToast(R.string.saved_album));
                }
            });
        }));
        ImageView rightIV = findViewById(R.id.titleRightIv);
        WKDialogUtils.getInstance().showScreenPopup(rightIV, list);
    }

    private void chooseIMG() {
        String desc = String.format(getString(R.string.file_permissions_des), getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {
                    if (result) {
                        selectImage();
                    }
                }

                @Override
                public void clickResult(boolean isCancel) {
                }
            }, this, desc, Manifest.permission.CAMERA);
        } else {
            WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
                @Override
                public void onResult(boolean result) {
                    if (result) {
                        selectImage();
                    }
                }

                @Override
                public void clickResult(boolean isCancel) {
                }
            }, this, desc, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void selectImage() {
        GlideUtils.getInstance().chooseIMG(this, 1, true, ChooseMimeType.img, false, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (WKReader.isNotEmpty(paths)) {
                    String path = paths.get(0).path;
                    if (!TextUtils.isEmpty(path)) {
                        Intent intent = new Intent(GroupAvatarActivity.this, WKCropImageActivity.class);
                        intent.putExtra("path", path);
                        chooseResultLac.launch(intent);
                    }
                }
            }

            @Override
            public void onCancel() {
            }
        });
    }

    ActivityResultLauncher<Intent> chooseResultLac = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            String path = result.getData().getStringExtra("path");
            GroupModel.getInstance().uploadGroupAvatar(groupId, path, code -> {
                if (code != HttpResponseCode.success) {
                    showToast(R.string.network_error_tips);
                    return;
                }
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(groupId, WKChannelType.GROUP);
                if (channel == null || TextUtils.isEmpty(channel.channelID)) {
                    channel = new WKChannel();
                    channel.channelType = WKChannelType.GROUP;
                    channel.channelID = groupId;
                    WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                }
                channel.avatarCacheKey = UUID.randomUUID().toString().replace("-", "");
                WKIM.getInstance().getChannelManager().updateAvatarCacheKey(groupId, WKChannelType.GROUP, channel.avatarCacheKey);
                WKCommonModel.getInstance().getChannel(groupId, WKChannelType.GROUP, null);
                showAvatar();
                EndpointManager.getInstance().invoke("group_avatar_updated", groupId);
                setResult(RESULT_OK);
            });
        }
    });
}
