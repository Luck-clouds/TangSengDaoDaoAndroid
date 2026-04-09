package com.chat.uikit.setting;

import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupManageMemberListLayoutBinding;
import com.chat.uikit.enity.BlacklistUser;
import com.chat.uikit.user.UserDetailActivity;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.chat.base.ui.components.AvatarView;

import java.util.ArrayList;
import java.util.List;

public class BlacklistActivity extends WKBaseActivity<ActGroupManageMemberListLayoutBinding> {
    private final List<BlacklistUser> blacklistUsers = new ArrayList<>();

    @Override
    protected ActGroupManageMemberListLayoutBinding getViewBinding() {
        return ActGroupManageMemberListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_blacklist);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBlacklists();
    }

    private void loadBlacklists() {
        try {
            loadingPopup.show();
            UserModel.getInstance().getBlacklists((code, msg, list) -> {
                loadingPopup.dismiss();
                if (list != null) {
                    blacklistUsers.clear();
                    blacklistUsers.addAll(list);
                    for (BlacklistUser user : blacklistUsers) {
                        if (user != null && !TextUtils.isEmpty(user.uid)) {
                            WKCommonModel.getInstance().getChannel(user.uid, WKChannelType.PERSONAL, null);
                        }
                    }
                }
                renderUsers();
                if (code != HttpResponseCode.success && !TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
            });
        } catch (Exception e) {
            loadingPopup.dismiss();
            showToast(R.string.unknown_error);
        }
    }

    private void renderUsers() {
        wkVBinding.contentLayout.removeAllViews();
        if (WKReader.isEmpty(blacklistUsers)) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText(R.string.security_blacklist_empty);
            emptyTv.setTextColor(ContextCompat.getColor(this, R.color.color999));
            emptyTv.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(18), AndroidUtilities.dp(15), AndroidUtilities.dp(18));
            wkVBinding.contentLayout.addView(emptyTv);
            return;
        }
        for (int i = 0; i < blacklistUsers.size(); i++) {
            BlacklistUser item = blacklistUsers.get(i);
            wkVBinding.contentLayout.addView(createUserRow(item));
            if (i < blacklistUsers.size() - 1) {
                wkVBinding.contentLayout.addView(createDivider());
            }
        }
    }

    private View createUserRow(BlacklistUser user) {
        LinearLayout rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.HORIZONTAL);
        rootView.setGravity(Gravity.CENTER_VERTICAL);
        rootView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(12), AndroidUtilities.dp(15), AndroidUtilities.dp(12));

        AvatarView avatarView = new AvatarView(this);
        avatarView.setSize(44);
        avatarView.showAvatar(user.uid, WKChannelType.PERSONAL);
        rootView.addView(avatarView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView nameTv = new TextView(this);
        nameTv.setText(resolveDisplayName(user));
        nameTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        nameTv.setTextSize(16);
        nameTv.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        rootView.addView(nameTv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        rootView.setOnClickListener(v -> openUserDetail(user.uid));
        return rootView;
    }

    private String resolveDisplayName(BlacklistUser user) {
        if (user == null) {
            return "";
        }
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(user.uid, WKChannelType.PERSONAL);
        if (channel != null) {
            if (!TextUtils.isEmpty(channel.channelRemark)) {
                return channel.channelRemark;
            }
            if (!TextUtils.isEmpty(channel.channelName)) {
                return channel.channelName;
            }
        }
        if (!TextUtils.isEmpty(user.name)) {
            return user.name;
        }
        if (!TextUtils.isEmpty(user.username)) {
            return user.username;
        }
        return user.uid;
    }

    private View createDivider() {
        View lineView = new View(this);
        lineView.setBackgroundColor(ContextCompat.getColor(this, R.color.homeColor));
        lineView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));
        return lineView;
    }

    private void openUserDetail(String uid) {
        try {
            Intent intent = new Intent(this, UserDetailActivity.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
        } catch (Exception e) {
            showToast(R.string.unknown_error);
        }
    }
}
