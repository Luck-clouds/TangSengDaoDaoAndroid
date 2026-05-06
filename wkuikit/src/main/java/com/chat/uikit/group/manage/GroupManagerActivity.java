package com.chat.uikit.group.manage;

import android.content.res.ColorStateList;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.SwitchView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupManageLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GroupManagerActivity extends WKBaseActivity<ActGroupManageLayoutBinding> {
    private String groupNo;
    private WKChannel groupChannel;
    private WKChannelMember loginMember;

    @Override
    protected ActGroupManageLayoutBinding getViewBinding() {
        return ActGroupManageLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_manage);
    }

    @Override
    protected void initPresenter() {
        groupNo = getIntent().getStringExtra(GroupManageConstants.EXTRA_GROUP_ID);
    }

    @Override
    protected void initListener() {
        wkVBinding.inviteSwitchView.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (compoundButton.isPressed()) {
                updateSetting("invite", checked, wkVBinding.inviteSwitchView, false);
            }
        });
        wkVBinding.forbiddenSwitchView.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (compoundButton.isPressed()) {
                updateSetting("forbidden", checked, wkVBinding.forbiddenSwitchView, false);
            }
        });
        wkVBinding.forbiddenAddFriendSwitchView.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (compoundButton.isPressed()) {
                updateSetting(GroupManageConstants.KEY_FORBIDDEN_ADD_FRIEND, checked, wkVBinding.forbiddenAddFriendSwitchView, true);
            }
        });
        wkVBinding.allowViewHistorySwitchView.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (compoundButton.isPressed()) {
                updateSetting(GroupManageConstants.KEY_ALLOW_VIEW_HISTORY_MSG, checked, wkVBinding.allowViewHistorySwitchView, true);
            }
        });
        wkVBinding.blacklistLayout.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupBlacklistActivity.class);
            intent.putExtra(GroupManageConstants.EXTRA_GROUP_ID, groupNo);
            startActivityForResult(intent, GroupManageConstants.REQUEST_BLACKLIST);
        });
        wkVBinding.transferOwnerLayout.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupManageMemberSelectActivity.class);
            intent.putExtra(GroupManageConstants.EXTRA_GROUP_ID, groupNo);
            intent.putExtra(GroupManageConstants.EXTRA_MODE, GroupManageConstants.MODE_TRANSFER_OWNER);
            intent.putExtra(GroupManageConstants.EXTRA_TITLE, getString(R.string.group_manage_transfer_title));
            startActivityForResult(intent, GroupManageConstants.REQUEST_TRANSFER_OWNER);
        });
    }

    @Override
    protected void initData() {
        super.initData();
        refreshPage(true);
    }

    private void refreshPage(boolean syncMembers) {
        groupChannel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
        loginMember = WKIM.getInstance().getChannelMembersManager().getMember(groupNo, WKChannelType.GROUP, WKConfig.getInstance().getUid());
        applySwitchData();
        renderManagerMembers();
        WKCommonModel.getInstance().getChannel(groupNo, WKChannelType.GROUP, (code, msg, entity) -> {
            groupChannel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
            applySwitchData();
        });
        if (syncMembers) {
            GroupModel.getInstance().groupMembersFullSync(groupNo, (code, msg) -> renderManagerMembers());
        }
    }

    private void applySwitchData() {
        groupChannel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
        loginMember = WKIM.getInstance().getChannelMembersManager().getMember(groupNo, WKChannelType.GROUP, WKConfig.getInstance().getUid());
        if (groupChannel == null) {
            return;
        }
        wkVBinding.inviteSwitchView.setChecked(groupChannel.invite == 1);
        wkVBinding.forbiddenSwitchView.setChecked(groupChannel.forbidden == 1);
        wkVBinding.forbiddenAddFriendSwitchView.setChecked(GroupManageUtils.getIntFromMap(groupChannel.remoteExtraMap, GroupManageConstants.KEY_FORBIDDEN_ADD_FRIEND) == 1);
        wkVBinding.allowViewHistorySwitchView.setChecked(GroupManageUtils.getIntFromMap(groupChannel.remoteExtraMap, GroupManageConstants.KEY_ALLOW_VIEW_HISTORY_MSG) == 1);
        boolean isOwner = loginMember != null && loginMember.role == WKChannelMemberRole.admin;
        wkVBinding.transferOwnerLayout.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        wkVBinding.addManagerLayout.setVisibility(View.GONE);
    }

    private void renderManagerMembers() {
        List<WKChannelMember> members = GroupModel.getInstance().getAllGroupMembers(groupNo);
        List<WKChannelMember> managerMembers = new ArrayList<>();
        int blacklistCount = 0;
        if (WKReader.isNotEmpty(members)) {
            for (WKChannelMember member : members) {
                if (member == null) {
                    continue;
                }
                if (member.status == 2) {
                    blacklistCount++;
                }
                if (member.isDeleted == 1) {
                    continue;
                }
                if (member.role == WKChannelMemberRole.admin || member.role == WKChannelMemberRole.manager) {
                    managerMembers.add(member);
                }
            }
        }
        managerMembers.sort(Comparator.comparingInt(value -> value.role == WKChannelMemberRole.admin ? 0 : 1));
        int managerCount = 0;
        for (WKChannelMember member : managerMembers) {
            if (member.role == WKChannelMemberRole.manager) {
                managerCount++;
            }
        }
        wkVBinding.managerCountTv.setText(String.format(getString(R.string.group_manager_count_desc), managerCount));
        wkVBinding.blacklistCountTv.setText(String.format(getString(R.string.group_blacklist_count_desc), blacklistCount));
        wkVBinding.managerContainerLayout.removeAllViews();
        boolean isOwner = loginMember != null && loginMember.role == WKChannelMemberRole.admin;
        for (int i = 0; i < managerMembers.size(); i++) {
            wkVBinding.managerContainerLayout.addView(createMemberItemView(managerMembers.get(i), isOwner));
            wkVBinding.managerContainerLayout.addView(createDividerView());
        }
        if (isOwner) {
            wkVBinding.managerContainerLayout.addView(createAddManagerView());
            return;
        }
        if (wkVBinding.managerContainerLayout.getChildCount() > 0) {
            wkVBinding.managerContainerLayout.removeViewAt(wkVBinding.managerContainerLayout.getChildCount() - 1);
        }
    }

    private View createMemberItemView(WKChannelMember member, boolean isOwner) {
        LinearLayout rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.HORIZONTAL);
        rootView.setGravity(Gravity.CENTER_VERTICAL);
        rootView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(12), AndroidUtilities.dp(15), AndroidUtilities.dp(12));

        AvatarView avatarView = new AvatarView(this);
        avatarView.setSize(44);
        avatarView.showAvatar(member.memberUID, WKChannelType.PERSONAL, member.memberAvatarCacheKey);
        rootView.addView(avatarView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView roleTv = new TextView(this);
        roleTv.setText(member.role == WKChannelMemberRole.admin ? R.string.group_owner_role_desc : R.string.group_manager_role_desc);
        roleTv.setTextColor(ContextCompat.getColor(this, R.color.white));
        roleTv.setTextSize(12);
        roleTv.setGravity(Gravity.CENTER);
        roleTv.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(3), AndroidUtilities.dp(10), AndroidUtilities.dp(3));
        roleTv.setBackground(createRoleBg(member.role == WKChannelMemberRole.admin ? ContextCompat.getColor(this, R.color.colorFFC107) : ContextCompat.getColor(this, R.color.colorAccent)));
        LinearLayout.LayoutParams roleParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        roleParams.leftMargin = AndroidUtilities.dp(12);
        rootView.addView(roleTv, roleParams);

        TextView nameTv = new TextView(this);
        nameTv.setText(GroupManageUtils.getDisplayName(member));
        nameTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        nameTv.setTextSize(16);
        nameTv.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        rootView.addView(nameTv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        if (isOwner && member.role == WKChannelMemberRole.manager) {
            AppCompatImageView deleteIv = createActionIcon(R.drawable.ic_group_manage_remove);
            LinearLayout.LayoutParams deleteParams = LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL);
            deleteParams.leftMargin = AndroidUtilities.dp(12);
            rootView.addView(deleteIv, deleteParams);
            deleteIv.setOnClickListener(v -> removeManager(member));
        }
        return rootView;
    }

    private View createAddManagerView() {
        LinearLayout rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.HORIZONTAL);
        rootView.setGravity(Gravity.CENTER_VERTICAL);
        rootView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15));

        AppCompatImageView addIv = createActionIcon(R.drawable.ic_group_manage_add);
        rootView.addView(addIv, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));

        TextView addTv = new TextView(this);
        addTv.setText(R.string.group_add_manager);
        addTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        addTv.setTextSize(16);
        addTv.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        rootView.addView(addTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        rootView.setOnClickListener(v -> openAddManagerPage());
        return rootView;
    }

    private View createDividerView() {
        View line = new View(this);
        line.setBackgroundColor(ContextCompat.getColor(this, R.color.homeColor));
        line.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));
        return line;
    }

    private AppCompatImageView createActionIcon(int resId) {
        AppCompatImageView imageView = new AppCompatImageView(this);
        imageView.setImageResource(resId);
        imageView.setScaleType(AppCompatImageView.ScaleType.FIT_CENTER);
        imageView.setSupportImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.titleBarIcon)));
        imageView.setSupportImageTintMode(PorterDuff.Mode.SRC_IN);
        return imageView;
    }

    private GradientDrawable createRoleBg(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(10));
        return drawable;
    }

    private void openAddManagerPage() {
        Intent intent = new Intent(this, GroupManageMemberSelectActivity.class);
        intent.putExtra(GroupManageConstants.EXTRA_GROUP_ID, groupNo);
        intent.putExtra(GroupManageConstants.EXTRA_MODE, GroupManageConstants.MODE_MANAGER);
        intent.putExtra(GroupManageConstants.EXTRA_TITLE, getString(R.string.group_manage_manager_title));
        intent.putStringArrayListExtra(GroupManageConstants.EXTRA_EXCLUDED_UIDS, getManagerUidList());
        startActivityForResult(intent, GroupManageConstants.REQUEST_MANAGERS);
    }

    private ArrayList<String> getManagerUidList() {
        ArrayList<String> uidList = new ArrayList<>();
        List<WKChannelMember> members = WKIM.getInstance().getChannelMembersManager().getMembers(groupNo, WKChannelType.GROUP);
        if (WKReader.isEmpty(members)) {
            return uidList;
        }
        for (WKChannelMember member : members) {
            if (member != null && member.role == WKChannelMemberRole.manager) {
                uidList.add(member.memberUID);
            }
        }
        return uidList;
    }

    private void removeManager(WKChannelMember member) {
        GroupModel.getInstance().removeGroupManagers(groupNo, Collections.singletonList(member.memberUID), (code, msg) -> {
            if (code != HttpResponseCode.success) {
                showToast(msg);
                return;
            }
            refreshPage(true);
        });
    }

    private void updateSetting(String key, boolean checked, SwitchView switchView, boolean refreshChannel) {
        GroupModel.getInstance().updateGroupSetting(groupNo, key, checked ? 1 : 0, (code, msg) -> {
            if (code != HttpResponseCode.success) {
                switchView.setChecked(!checked);
                showToast(msg);
                return;
            }
            WKCommonModel.getInstance().getChannel(groupNo, WKChannelType.GROUP, (refreshCode, refreshMsg, entity) -> {
                groupChannel = WKIM.getInstance().getChannelManager().getChannel(groupNo, WKChannelType.GROUP);
                applySwitchData();
                if (refreshChannel) {
                    renderManagerMembers();
                }
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == GroupManageConstants.REQUEST_TRANSFER_OWNER) {
            finish();
            return;
        }
        if (requestCode == GroupManageConstants.REQUEST_MANAGERS || requestCode == GroupManageConstants.REQUEST_BLACKLIST) {
            refreshPage(true);
        }
    }
}
