package com.chat.uikit.group.manage;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.contacts.ChooseUserSelectedAdapter;
import com.chat.uikit.contacts.FriendUIEntity;
import com.chat.uikit.databinding.ActDeleteMemberLayoutBinding;
import com.chat.uikit.group.GroupMemberEntity;
import com.chat.uikit.group.adapter.DeleteGroupMemberAdapter;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupManageMemberSelectActivity extends WKBaseActivity<ActDeleteMemberLayoutBinding> {
    private DeleteGroupMemberAdapter groupMemberAdapter;
    private ChooseUserSelectedAdapter selectedAdapter;
    private String groupId;
    private int mode;
    private String title;
    private String searchKey = "";
    private int loginMemberRole = WKChannelMemberRole.normal;
    private final List<WKChannelMember> sourceMembers = new ArrayList<>();
    private final ArrayList<String> excludedIds = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();

    @Override
    protected ActDeleteMemberLayoutBinding getViewBinding() {
        return ActDeleteMemberLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(title);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.group_member_manage_save);
    }

    @Override
    protected void initPresenter() {
        groupId = getIntent().getStringExtra(GroupManageConstants.EXTRA_GROUP_ID);
        mode = getIntent().getIntExtra(GroupManageConstants.EXTRA_MODE, GroupManageConstants.MODE_MANAGER);
        title = getIntent().getStringExtra(GroupManageConstants.EXTRA_TITLE);
        ArrayList<String> temp = getIntent().getStringArrayListExtra(GroupManageConstants.EXTRA_EXCLUDED_UIDS);
        if (temp != null) {
            excludedIds.addAll(temp);
        }
        if (TextUtils.isEmpty(title)) {
            if (mode == GroupManageConstants.MODE_BLACKLIST) {
                title = getString(R.string.group_manage_blacklist_title);
            } else if (mode == GroupManageConstants.MODE_TRANSFER_OWNER) {
                title = getString(R.string.group_manage_transfer_title);
            } else {
                title = getString(R.string.group_manage_manager_title);
            }
        }
    }

    @Override
    protected void initView() {
        groupMemberAdapter = new DeleteGroupMemberAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, groupMemberAdapter);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.refreshLayout.setEnableLoadMore(false);

        selectedAdapter = new ChooseUserSelectedAdapter(new ChooseUserSelectedAdapter.IGetEdit() {
            @Override
            public void onDeleted(String uid) {
                selectedIds.remove(uid);
                removeSelectedChip(uid);
                renderMembers();
            }

            @Override
            public void searchUser(String key) {
                searchKey = key;
                groupMemberAdapter.setSearch(searchKey);
                renderMembers();
            }
        });
        FriendUIEntity searchEntity = new FriendUIEntity(new WKChannel("", WKChannelType.PERSONAL));
        searchEntity.itemType = 1;
        selectedAdapter.addData(searchEntity);
        wkVBinding.selectUserRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        wkVBinding.selectUserRecyclerView.setAdapter(selectedAdapter);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initListener() {
        selectedAdapter.setOnItemClickListener((adapter, view, position) -> {
            FriendUIEntity entity = selectedAdapter.getItem(position);
            if (entity == null || entity.itemType != 0) {
                return;
            }
            selectedIds.remove(entity.channel.channelID);
            removeSelectedChip(entity.channel.channelID);
            renderMembers();
        });
        groupMemberAdapter.setOnItemClickListener((adapter, view, position) -> {
            GroupMemberEntity entity = (GroupMemberEntity) adapter.getItem(position);
            if (entity == null || entity.member == null) {
                return;
            }
            String uid = entity.member.memberUID;
            if (mode == GroupManageConstants.MODE_TRANSFER_OWNER) {
                selectedIds.clear();
                clearSelectedChips();
                selectedIds.add(uid);
                addSelectedChip(entity.member);
            } else if (selectedIds.contains(uid)) {
                selectedIds.remove(uid);
                removeSelectedChip(uid);
            } else {
                selectedIds.add(uid);
                addSelectedChip(entity.member);
            }
            renderMembers();
            SoftKeyboardUtils.getInstance().hideSoftKeyboard(this);
        });
        wkVBinding.selectUserRecyclerView.setOnTouchListener((view, motionEvent) -> {
            View childView = wkVBinding.selectUserRecyclerView.getChildAt(selectedAdapter.getData().size() - 1);
            if (childView != null) {
                EditText editText = childView.findViewById(R.id.searchEt);
                SoftKeyboardUtils.getInstance().showSoftKeyBoard(GroupManageMemberSelectActivity.this, editText);
            }
            return false;
        });
    }

    @Override
    protected void initData() {
        super.initData();
        syncAndLoadMembers();
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        if (mode == GroupManageConstants.MODE_TRANSFER_OWNER) {
            applyTransferOwner();
        } else {
            applyBatchUpdate();
        }
    }

    private void syncAndLoadMembers() {
        if (mode == GroupManageConstants.MODE_BLACKLIST) {
            GroupModel.getInstance().groupMembersFullSync(groupId, (code, msg) -> loadMembers());
        } else {
            GroupModel.getInstance().groupMembersSync(groupId, (code, msg) -> loadMembers());
        }
        loadMembers();
    }

    private void loadMembers() {
        sourceMembers.clear();
        selectedIds.clear();
        clearSelectedChips();
        List<WKChannelMember> members = GroupModel.getInstance().getAllGroupMembers(groupId);
        WKChannelMember loginMember = WKIM.getInstance().getChannelMembersManager().getMember(groupId, WKChannelType.GROUP, WKConfig.getInstance().getUid());
        if (loginMember != null) {
            loginMemberRole = loginMember.role;
        }
        if (WKReader.isNotEmpty(members)) {
            for (WKChannelMember member : members) {
                if (member != null && member.isDeleted == 0) {
                    sourceMembers.add(member);
                }
            }
            sourceMembers.sort(Comparator.comparingInt(value -> {
                if (value.role == WKChannelMemberRole.admin) {
                    return 0;
                }
                if (value.role == WKChannelMemberRole.manager) {
                    return 1;
                }
                return 2;
            }));
        }
        renderMembers();
    }

    private void renderMembers() {
        List<GroupMemberEntity> data = new ArrayList<>();
        for (WKChannelMember member : sourceMembers) {
            if (!canShow(member)) {
                continue;
            }
            String showName = GroupManageUtils.getDisplayName(member);
            if (!TextUtils.isEmpty(searchKey) && !showName.contains(searchKey) && !member.memberUID.contains(searchKey)) {
                continue;
            }
            GroupMemberEntity entity = new GroupMemberEntity(member);
            entity.checked = selectedIds.contains(member.memberUID) ? 1 : 0;
            data.add(entity);
        }
        groupMemberAdapter.setList(data);
    }

    private boolean canShow(WKChannelMember member) {
        if (member == null || member.memberUID.equals(WKConfig.getInstance().getUid()) || excludedIds.contains(member.memberUID)) {
            return false;
        }
        if (mode == GroupManageConstants.MODE_TRANSFER_OWNER || mode == GroupManageConstants.MODE_MANAGER) {
            return member.role != WKChannelMemberRole.admin;
        }
        if (loginMemberRole == WKChannelMemberRole.manager) {
            return member.role == WKChannelMemberRole.normal;
        }
        return member.role != WKChannelMemberRole.admin;
    }

    private void applyBatchUpdate() {
        if (selectedIds.isEmpty()) {
            showToast(R.string.group_select_member_empty);
            return;
        }
        List<String> uidList = new ArrayList<>(selectedIds);
        showTitleRightLoading();
        setRightViewEnabled(false);
        if (mode == GroupManageConstants.MODE_MANAGER) {
            GroupModel.getInstance().addGroupManagers(groupId, uidList, (code, msg) -> {
                if (code != HttpResponseCode.success) {
                    finishAction(false, msg);
                    return;
                }
                syncResultAndFinish();
            });
            return;
        }
        GroupModel.getInstance().addBlacklist(groupId, uidList, (code, msg) -> {
            if (code != HttpResponseCode.success) {
                finishAction(false, msg);
                return;
            }
            syncResultAndFinish();
        });
    }

    private void applyTransferOwner() {
        if (selectedIds.isEmpty()) {
            showToast(R.string.group_select_owner_empty);
            return;
        }
        String uid = selectedIds.iterator().next();
        WKChannelMember member = findMember(uid);
        String name = GroupManageUtils.getDisplayName(member);
        WKDialogUtils.getInstance().showDialog(this, getString(R.string.group_transfer_owner), String.format(getString(R.string.group_transfer_owner_confirm), name), true, "", getString(R.string.sure), 0, 0, index -> {
            if (index != 1) {
                return;
            }
            showTitleRightLoading();
            setRightViewEnabled(false);
            GroupModel.getInstance().transferOwner(groupId, uid, (code, msg) -> {
                if (code != HttpResponseCode.success) {
                    finishAction(false, msg);
                    return;
                }
                syncResultAndFinish();
            });
        });
    }

    private void syncResultAndFinish() {
        if (mode == GroupManageConstants.MODE_BLACKLIST) {
            GroupModel.getInstance().groupMembersFullSync(groupId, (code, msg) -> {
                WKCommonModel.getInstance().getChannel(groupId, WKChannelType.GROUP, null);
                finishAction(code == HttpResponseCode.success, msg);
            });
            return;
        }
        GroupModel.getInstance().groupMembersSync(groupId, (code, msg) -> {
            WKCommonModel.getInstance().getChannel(groupId, WKChannelType.GROUP, null);
            finishAction(code == HttpResponseCode.success, msg);
        });
    }

    private void finishAction(boolean success, String msg) {
        hideTitleRightLoading();
        setRightViewEnabled(true);
        if (!success) {
            if (!TextUtils.isEmpty(msg)) {
                showToast(msg);
            }
            return;
        }
        setResult(RESULT_OK, new Intent());
        finish();
    }

    private void clearSelectedChips() {
        while (selectedAdapter != null && selectedAdapter.getData().size() > 1) {
            selectedAdapter.removeAt(0);
        }
    }

    private void addSelectedChip(WKChannelMember member) {
        if (member == null || hasSelectedChip(member.memberUID)) {
            return;
        }
        selectedAdapter.addData(selectedAdapter.getData().size() - 1, toFriendEntity(member));
        wkVBinding.selectUserRecyclerView.scrollToPosition(selectedAdapter.getData().size() - 1);
    }

    private void removeSelectedChip(String uid) {
        for (int i = 0; i < selectedAdapter.getData().size(); i++) {
            FriendUIEntity entity = selectedAdapter.getData().get(i);
            if (entity.itemType == 0 && entity.channel.channelID.equals(uid)) {
                selectedAdapter.removeAt(i);
                return;
            }
        }
    }

    private boolean hasSelectedChip(String uid) {
        for (FriendUIEntity entity : selectedAdapter.getData()) {
            if (entity.itemType == 0 && entity.channel.channelID.equals(uid)) {
                return true;
            }
        }
        return false;
    }

    private FriendUIEntity toFriendEntity(WKChannelMember member) {
        WKChannel channel = new WKChannel();
        channel.channelID = member.memberUID;
        channel.channelType = WKChannelType.PERSONAL;
        channel.channelName = member.memberName;
        channel.channelRemark = member.memberRemark;
        channel.avatar = member.memberAvatar;
        channel.avatarCacheKey = member.memberAvatarCacheKey;
        return new FriendUIEntity(channel);
    }

    private WKChannelMember findMember(String uid) {
        for (WKChannelMember member : sourceMembers) {
            if (member.memberUID.equals(uid)) {
                return member;
            }
        }
        return null;
    }
}
