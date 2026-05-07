package com.chat.flagship.richtext;

/**
 * 富文本@成员选择页
 * Created by Luckclouds .
 */

import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipRichTextMentionLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.chat.base.config.WKConfig;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.interfaces.IChannelMemberListResult;

import java.util.ArrayList;
import java.util.List;

public class FlagshipRichTextMentionActivity extends WKBaseActivity<ActFlagshipRichTextMentionLayoutBinding> {
    private final List<WKChannelMember> sourceMembers = new ArrayList<>();
    private FlagshipRichTextMentionAdapter adapter;
    private String groupId;

    @Override
    protected ActFlagshipRichTextMentionLayoutBinding getViewBinding() {
        return ActFlagshipRichTextMentionLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_rich_text_mention_title);
    }

    @Override
    protected int getRightIvResourceId(android.widget.ImageView imageView) {
        return -1;
    }

    @Override
    protected void initView() {
        groupId = getIntent().getStringExtra("group_id");
        if (groupId == null) {
            groupId = "";
        }
        adapter = new FlagshipRichTextMentionAdapter(member -> {
            Intent intent = new Intent();
            intent.putExtra("uid", member.memberUID);
            intent.putExtra("name", getDisplayName(member));
            setResult(RESULT_OK, intent);
            finish();
        });
        wkVBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wkVBinding.recyclerView.setAdapter(adapter);
        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderMembers(s == null ? "" : s.toString().trim());
            }
        });
        loadMembers();
    }

    private void loadMembers() {
        sourceMembers.clear();
        sourceMembers.addAll(filterValidMembers(GroupModel.getInstance().getAllGroupMembers(groupId)));
        if (!sourceMembers.isEmpty()) {
            renderMembers("");
            return;
        }
        GroupModel.getInstance().getChannelMembers(groupId, "", 1, 200, new IChannelMemberListResult() {
            @Override
            public void onResult(List<WKChannelMember> list) {
                sourceMembers.clear();
                sourceMembers.addAll(filterValidMembers(list));
                runOnUiThread(() -> renderMembers(wkVBinding.searchEt.getText() == null ? "" : wkVBinding.searchEt.getText().toString().trim()));
            }
        });
    }

    private List<WKChannelMember> filterValidMembers(List<WKChannelMember> list) {
        List<WKChannelMember> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        String loginUid = WKConfig.getInstance().getUid();
        for (WKChannelMember member : list) {
            if (member == null || TextUtils.isEmpty(member.memberUID) || member.memberUID.equals(loginUid) || member.isDeleted == 1) {
                continue;
            }
            result.add(member);
        }
        return result;
    }

    private void renderMembers(String keyword) {
        List<WKChannelMember> filtered = new ArrayList<>();
        for (WKChannelMember member : sourceMembers) {
            String displayName = getDisplayName(member);
            if (TextUtils.isEmpty(keyword)
                    || displayName.contains(keyword)
                    || member.memberUID.contains(keyword)) {
                filtered.add(member);
            }
        }
        adapter.setData(filtered);
        wkVBinding.emptyTv.setVisibility(filtered.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private String getDisplayName(WKChannelMember member) {
        if (member == null) {
            return "";
        }
        if (!TextUtils.isEmpty(member.memberRemark)) {
            return member.memberRemark;
        }
        if (!TextUtils.isEmpty(member.memberName)) {
            return member.memberName;
        }
        return member.memberUID;
    }
}
