package com.chat.uikit.group.manage;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupManageMemberListLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

public class GroupBlacklistActivity extends WKBaseActivity<ActGroupManageMemberListLayoutBinding> {
    private String groupId;
    private final List<WKChannelMember> blacklistMembers = new ArrayList<>();

    @Override
    protected ActGroupManageMemberListLayoutBinding getViewBinding() {
        return ActGroupManageMemberListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_manage_blacklist_title);
    }

    @Override
    protected void initPresenter() {
        groupId = getIntent().getStringExtra(GroupManageConstants.EXTRA_GROUP_ID);
    }

    @Override
    protected void initData() {
        super.initData();
        refreshMembers(true);
    }

    private void refreshMembers(boolean syncMembers) {
        if (syncMembers) {
            GroupModel.getInstance().groupMembersFullSync(groupId, (code, msg) -> renderLocalMembers());
            return;
        }
        renderLocalMembers();
    }

    private void renderLocalMembers() {
        blacklistMembers.clear();
        List<WKChannelMember> members = GroupModel.getInstance().getAllGroupMembers(groupId);
        if (WKReader.isNotEmpty(members)) {
            for (WKChannelMember member : members) {
                if (member != null && member.status == 2) {
                    blacklistMembers.add(member);
                }
            }
        }
        renderMembers();
    }

    private void renderMembers() {
        wkVBinding.contentLayout.removeAllViews();
        if (WKReader.isEmpty(blacklistMembers)) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText(R.string.group_blacklist_empty);
            emptyTv.setTextColor(ContextCompat.getColor(this, R.color.color999));
            emptyTv.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(18), AndroidUtilities.dp(15), AndroidUtilities.dp(18));
            wkVBinding.contentLayout.addView(emptyTv);
        } else {
            for (int i = 0; i < blacklistMembers.size(); i++) {
                wkVBinding.contentLayout.addView(createMemberRow(blacklistMembers.get(i)));
                if (i < blacklistMembers.size() - 1) {
                    wkVBinding.contentLayout.addView(createDivider());
                }
            }
        }
        if (wkVBinding.contentLayout.getChildCount() > 0) {
            wkVBinding.contentLayout.addView(createDivider());
        }
        wkVBinding.contentLayout.addView(createAddRow());
    }

    private View createMemberRow(WKChannelMember member) {
        LinearLayout rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.HORIZONTAL);
        rootView.setGravity(Gravity.CENTER_VERTICAL);
        rootView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(12), AndroidUtilities.dp(15), AndroidUtilities.dp(12));

        AvatarView avatarView = new AvatarView(this);
        avatarView.setSize(44);
        avatarView.showAvatar(member.memberUID, WKChannelType.PERSONAL, member.memberAvatarCacheKey);
        rootView.addView(avatarView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView nameTv = new TextView(this);
        nameTv.setText(GroupManageUtils.getDisplayName(member));
        nameTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        nameTv.setTextSize(16);
        nameTv.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        rootView.addView(nameTv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        ImageView deleteIv = new ImageView(this);
        deleteIv.setImageResource(R.mipmap.icon_chat_delete);
        rootView.addView(deleteIv, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));
        deleteIv.setOnClickListener(v -> removeBlacklist(member));
        return rootView;
    }

    private View createAddRow() {
        LinearLayout rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.HORIZONTAL);
        rootView.setGravity(Gravity.CENTER_VERTICAL);
        rootView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15));

        ImageView addIv = new ImageView(this);
        addIv.setImageResource(R.mipmap.icon_chat_add);
        rootView.addView(addIv, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));

        TextView addTv = new TextView(this);
        addTv.setText(R.string.group_blacklist);
        addTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        addTv.setTextSize(16);
        addTv.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        rootView.addView(addTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        rootView.setOnClickListener(v -> openMemberSelect());
        return rootView;
    }

    private View createDivider() {
        View lineView = new View(this);
        lineView.setBackgroundColor(ContextCompat.getColor(this, R.color.homeColor));
        lineView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));
        return lineView;
    }

    private void openMemberSelect() {
        Intent intent = new Intent(this, GroupManageMemberSelectActivity.class);
        intent.putExtra(GroupManageConstants.EXTRA_GROUP_ID, groupId);
        intent.putExtra(GroupManageConstants.EXTRA_MODE, GroupManageConstants.MODE_BLACKLIST);
        intent.putExtra(GroupManageConstants.EXTRA_TITLE, getString(R.string.group_manage_blacklist_title));
        intent.putStringArrayListExtra(GroupManageConstants.EXTRA_EXCLUDED_UIDS, getBlacklistUidList());
        startActivityForResult(intent, GroupManageConstants.REQUEST_MEMBER_SELECT);
    }

    private ArrayList<String> getBlacklistUidList() {
        ArrayList<String> uidList = new ArrayList<>();
        for (WKChannelMember member : blacklistMembers) {
            uidList.add(member.memberUID);
        }
        return uidList;
    }

    private void removeBlacklist(WKChannelMember member) {
        List<String> uidList = new ArrayList<>();
        uidList.add(member.memberUID);
        GroupModel.getInstance().removeBlacklist(groupId, uidList, (code, msg) -> {
            if (code != HttpResponseCode.success) {
                showToast(msg);
                return;
            }
            setResult(RESULT_OK);
            refreshMembers(true);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == GroupManageConstants.REQUEST_MEMBER_SELECT) {
            setResult(RESULT_OK);
            refreshMembers(true);
        }
    }
}
