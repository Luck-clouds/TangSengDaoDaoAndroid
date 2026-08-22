package com.chat.uikit.chat;

import android.content.Intent;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.msgitem.WKChannelMemberRole;
import com.chat.base.ui.Theme;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.R;
import com.chat.uikit.chat.adapter.ChooseChatAdapter;
import com.chat.uikit.contacts.ChooseContactsActivity;
import com.chat.uikit.databinding.ActChooseChatLayoutBinding;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelStatus;
import com.xinbida.wukongim.entity.WKUIConversationMsg;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2019-12-08 13:47
 * 选择会话页面
 */
public class ChooseChatActivity extends WKBaseActivity<ActChooseChatLayoutBinding> {
    ChooseChatAdapter chooseChatAdapter;
    Button rightBtn;
    TextView selectAllTv;
    private boolean isChoose;
    List<ChooseChatEntity> allList;

    @Override
    protected ActChooseChatLayoutBinding getViewBinding() {
        return ActChooseChatLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.choose_chat);
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        rightBtn = titleRightBtn;
        return getString(R.string.sure);
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();

        List<WKUIConversationMsg> selectedList = new ArrayList<>();
        // 必须从完整会话列表收集，保证搜索前后的多选结果不会丢失。
        for (int i = 0, size = allList.size(); i < size; i++) {
            if (allList.get(i).isCheck)
                selectedList.add(allList.get(i).uiConveursationMsg);
        }
        List<WKChannel> list = new ArrayList<>();
        if (WKReader.isNotEmpty(selectedList)) {
            for (int i = 0; i < selectedList.size(); i++) {
                list.add(selectedList.get(i).getWkChannel());
            }
            submitChannels(list);
        }
    }

    private void submitChannels(List<WKChannel> list) {
        if (!WKReader.isNotEmpty(list)) return;
        if (isChoose) {
            if (WKUIKitApplication.getInstance().getMessageContentList() != null) {
                WKUIKitApplication.getInstance().showChatConfirmDialog(this, list, WKUIKitApplication.getInstance().getMessageContentList(), new WKUIKitApplication.IShowChatConfirm() {
                    @Override
                    public void onBack(@NonNull List<WKChannel> list, @NonNull List<WKMessageContent> messageContentList) {
                        WKUIKitApplication.getInstance().sendChooseChatBack(list);
                        finish();
                    }
                });
            } else {
                WKUIKitApplication.getInstance().sendChooseChatBack(list);
                finish();
            }
        } else {
            Intent intent = new Intent();
            intent.putParcelableArrayListExtra("list", (ArrayList<? extends Parcelable>) list);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    protected void initPresenter() {
        isChoose = getIntent().getBooleanExtra("isChoose", false);
    }

    @Override
    protected void initView() {
        chooseChatAdapter = new ChooseChatAdapter(new ArrayList<>());
        chooseChatAdapter.setMultiSelectMode(true);
        initAdapter(wkVBinding.recyclerView, chooseChatAdapter);
        chooseChatAdapter.addHeaderView(getHeader());
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
    }

    @Override
    protected void initListener() {
        chooseChatAdapter.setOnItemClickListener((adapter, view1, position) -> {
            ChooseChatEntity chooseChatEntity = (ChooseChatEntity) adapter.getItem(position);
            if (chooseChatEntity != null) {
                boolean isSelect = !chooseChatEntity.isBan && !chooseChatEntity.isForbidden;
                if (isSelect) {
                    chooseChatEntity.isCheck = !chooseChatEntity.isCheck;
                    adapter.notifyItemChanged(position + adapter.getHeaderLayoutCount(),chooseChatEntity);


                    updateSelectedCount();
                }
            }

        });

        wkVBinding.searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        wkVBinding.searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SoftKeyboardUtils.getInstance().hideSoftKeyboard(ChooseChatActivity.this);
                return true;
            }
            return false;
        });
        wkVBinding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                searchUser(editable.toString());
            }
        });
    }

    private void updateSelectedCount() {
        int count = 0;
        for (int i = 0, size = allList.size(); i < size; i++) {
            if (allList.get(i).isCheck) count++;
        }
        if (count > 0) {
            rightBtn.setVisibility(View.VISIBLE);
            rightBtn.setText(String.format("%s(%s)", getString(R.string.sure), count));
        } else {
            rightBtn.setText(R.string.sure);
            rightBtn.setVisibility(View.INVISIBLE);
        }
        updateSelectAllButtonState();
    }

    private boolean areAllCurrentItemsSelected() {
        boolean hasSelectableItem = false;
        List<ChooseChatEntity> currentList = chooseChatAdapter.getData();
        for (int i = 0, size = currentList.size(); i < size; i++) {
            ChooseChatEntity entity = currentList.get(i);
            if (entity.isBan || entity.isForbidden) continue;
            hasSelectableItem = true;
            if (!entity.isCheck) return false;
        }
        return hasSelectableItem;
    }

    private void updateSelectAllButtonState() {
        if (selectAllTv == null) return;
        selectAllTv.setText(areAllCurrentItemsSelected()
                ? R.string.chat_cancel_select : R.string.chat_select_all);
    }

    private void searchUser(String content) {
        if (TextUtils.isEmpty(content)) {
            chooseChatAdapter.setList(allList);
            updateSelectAllButtonState();
            return;
        }
        List<ChooseChatEntity> tempList = new ArrayList<>();
        for (int i = 0, size = allList.size(); i < size; i++) {
            if ((!TextUtils.isEmpty(allList.get(i).uiConveursationMsg.getWkChannel().channelName) && allList.get(i).uiConveursationMsg.getWkChannel().channelName.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))
                    || (!TextUtils.isEmpty(allList.get(i).uiConveursationMsg.getWkChannel().channelRemark) && allList.get(i).uiConveursationMsg.getWkChannel().channelRemark.toLowerCase(Locale.getDefault())
                    .contains(content.toLowerCase(Locale.getDefault())))) {
                tempList.add(allList.get(i));
            }
        }
        chooseChatAdapter.setList(tempList);
        updateSelectAllButtonState();
    }

    @Override
    protected void initData() {
        super.initData();
        List<WKUIConversationMsg> list = WKIM.getInstance().getConversationManager().getAll();
        allList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            ChooseChatEntity chooseChatEntity = new ChooseChatEntity(list.get(i));
            if (list.get(i).getWkChannel() != null) {
                WKChannelMember mChannelMember = WKIM.getInstance().getChannelMembersManager().getMember(list.get(i).getWkChannel().channelID, list.get(i).getWkChannel().channelType, WKConfig.getInstance().getUid());
                if (list.get(i).getWkChannel().forbidden == 1) {
                    // 禁言中
                    if (mChannelMember != null) {
                        chooseChatEntity.isForbidden = mChannelMember.role == WKChannelMemberRole.normal;
                    }
                } else {
                    if (mChannelMember != null)
                        chooseChatEntity.isForbidden = mChannelMember.forbiddenExpirationTime > 0;
                    else chooseChatEntity.isForbidden = false;
                }
                chooseChatEntity.isBan = list.get(i).getWkChannel().status == WKChannelStatus.statusDisabled;
            }
            allList.add(chooseChatEntity);
        }

        chooseChatAdapter.setList(allList);
        rightBtn.setVisibility(View.GONE);
    }

    public static class ChooseChatEntity {
        ChooseChatEntity(WKUIConversationMsg uiConveursationMsg) {
            this.uiConveursationMsg = uiConveursationMsg;
        }

        public WKUIConversationMsg uiConveursationMsg;
        public boolean isCheck;
        // 禁言中
        public boolean isForbidden;
        // 禁用中
        public boolean isBan;
    }

    private View getHeader() {
        View view = LayoutInflater.from(this).inflate(R.layout.choose_chat_header_layout, wkVBinding.recyclerView, false);
        View headerView = view.findViewById(R.id.createTv);
        selectAllTv = view.findViewById(R.id.multiSelectTv);
        selectAllTv.setTextColor(Theme.colorAccount);
        selectAllTv.setOnClickListener(v -> {
            boolean shouldClear = areAllCurrentItemsSelected();
            List<ChooseChatEntity> currentList = chooseChatAdapter.getData();
            for (int i = 0, size = currentList.size(); i < size; i++) {
                ChooseChatEntity entity = currentList.get(i);
                if (!entity.isBan && !entity.isForbidden) {
                    entity.isCheck = !shouldClear;
                }
            }
            chooseChatAdapter.setMultiSelectMode(true);
            updateSelectedCount();
        });
        headerView.setOnClickListener(view1 -> {
            Intent intent = new Intent(this, ChooseContactsActivity.class);
            if (WKUIKitApplication.getInstance().getMessageContentList() != null)
                intent.putParcelableArrayListExtra("msgContentList", (ArrayList<? extends Parcelable>) WKUIKitApplication.getInstance().getMessageContentList());
            startActivity(intent);
        });
        return view;
    }
}
