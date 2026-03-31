package com.chat.uikit.group.manage;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActGroupForbiddenDurationLayoutBinding;
import com.chat.uikit.group.service.GroupModel;

import java.util.ArrayList;
import java.util.List;

import com.chat.uikit.group.service.entity.GroupForbiddenTime;

public class GroupForbiddenDurationActivity extends WKBaseActivity<ActGroupForbiddenDurationLayoutBinding> {
    private String groupId;
    private String uid;
    private int selectedForbiddenKey = 0;
    private final List<GroupForbiddenTime> forbiddenTimes = new ArrayList<>();

    @Override
    protected ActGroupForbiddenDurationLayoutBinding getViewBinding() {
        return ActGroupForbiddenDurationLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.group_forbidden_setting);
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        return getString(R.string.sure);
    }

    @Override
    protected void initPresenter() {
        groupId = getIntent().getStringExtra(GroupManageConstants.EXTRA_GROUP_ID);
        uid = getIntent().getStringExtra(GroupManageConstants.EXTRA_UID);
    }

    @Override
    protected void initData() {
        super.initData();
        showOrHideRightBtn(false);
        loadForbiddenTimes();
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();
        if (selectedForbiddenKey <= 0) {
            return;
        }
        updateForbidden();
    }

    private void loadForbiddenTimes() {
        GroupModel.getInstance().getForbiddenTimes((code, msg, list) -> {
            if (code == HttpResponseCode.success && list != null && !list.isEmpty()) {
                forbiddenTimes.clear();
                forbiddenTimes.addAll(list);
                selectedForbiddenKey = 0;
                renderDurationRows();
                return;
            }
            applyDefaultForbiddenTimes();
            renderDurationRows();
        });
    }

    private void applyDefaultForbiddenTimes() {
        forbiddenTimes.clear();
        forbiddenTimes.add(createForbiddenTime(getString(R.string.group_member_mute_1_min), 1));
        forbiddenTimes.add(createForbiddenTime(getString(R.string.group_member_mute_10_min), 2));
        forbiddenTimes.add(createForbiddenTime(getString(R.string.group_member_mute_1_hour), 3));
        forbiddenTimes.add(createForbiddenTime(getString(R.string.group_member_mute_1_day), 4));
        forbiddenTimes.add(createForbiddenTime(getString(R.string.group_member_mute_1_week), 5));
        forbiddenTimes.add(createForbiddenTime(getString(R.string.group_member_mute_1_month), 6));
        selectedForbiddenKey = 0;
    }

    private GroupForbiddenTime createForbiddenTime(String text, int key) {
        GroupForbiddenTime time = new GroupForbiddenTime();
        time.text = text;
        time.key = key;
        return time;
    }

    private void renderDurationRows() {
        wkVBinding.contentLayout.removeAllViews();
        for (int i = 0; i < forbiddenTimes.size(); i++) {
            wkVBinding.contentLayout.addView(createRow(forbiddenTimes.get(i)));
        }
    }

    private LinearLayout createRow(GroupForbiddenTime forbiddenTime) {
        LinearLayout rowView = new LinearLayout(this);
        rowView.setOrientation(LinearLayout.HORIZONTAL);
        rowView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rowView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(18), AndroidUtilities.dp(15), AndroidUtilities.dp(18));

        TextView titleTv = new TextView(this);
        titleTv.setText(forbiddenTime.text);
        titleTv.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        titleTv.setTextSize(16);
        rowView.addView(titleTv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        AppCompatImageView selectedIv = new AppCompatImageView(this);
        selectedIv.setImageResource(R.mipmap.msg_check);
        selectedIv.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent));
        selectedIv.setVisibility(selectedForbiddenKey == forbiddenTime.key ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        rowView.addView(selectedIv, LayoutHelper.createLinear(24, 24, android.view.Gravity.CENTER_VERTICAL));
        rowView.setOnClickListener(v -> {
            selectedForbiddenKey = forbiddenTime.key;
            showOrHideRightBtn(true);
            renderDurationRows();
        });
        return rowView;
    }

    private void updateForbidden() {
        GroupModel.getInstance().updateMemberForbidden(groupId, uid, 1, selectedForbiddenKey, (code, msg) -> {
            if (code != HttpResponseCode.success) {
                showToast(msg);
                return;
            }
            GroupModel.getInstance().groupMembersSync(groupId, (syncCode, syncMsg) -> {
                setResult(RESULT_OK);
                finish();
            });
        });
    }
}
