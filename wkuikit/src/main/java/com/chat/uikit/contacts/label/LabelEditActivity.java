package com.chat.uikit.contacts.label;

import android.content.Intent;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChooseContactsMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.StringUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActLabelEditLayoutBinding;
import com.xinbida.wukongim.entity.WKChannel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LabelEditActivity extends WKBaseActivity<ActLabelEditLayoutBinding> {
    public static final String EXTRA_LABEL = "extra_label";
    public static final String EXTRA_MEMBERS = "extra_members";
    private static final int LABEL_NAME_MAX_LENGTH = 50;

    private final ArrayList<WKChannel> originalMembers = new ArrayList<>();
    private final ArrayList<WKChannel> currentMembers = new ArrayList<>();
    private final LabelMemberAdapter memberAdapter = new LabelMemberAdapter();
    private LabelEntity labelEntity;
    private TextView rightTv;
    private EditText nameEt;
    private boolean deleteMode;
    private boolean isCreateMode;

    @Override
    protected ActLabelEditLayoutBinding getViewBinding() {
        return ActLabelEditLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        labelEntity = getIntent().getParcelableExtra(EXTRA_LABEL);
        ArrayList<WKChannel> selectedList = getIntent().getParcelableArrayListExtra(EXTRA_MEMBERS);
        isCreateMode = labelEntity == null;
        if (labelEntity != null) {
            originalMembers.addAll(labelEntity.members);
            currentMembers.addAll(labelEntity.members);
        } else if (selectedList != null) {
            originalMembers.addAll(selectedList);
            currentMembers.addAll(selectedList);
        }
    }

    @Override
    protected void setTitle(TextView titleTv) {
        if (labelEntity != null && !TextUtils.isEmpty(labelEntity.name)) {
            titleTv.setText(labelEntity.name);
        } else {
            titleTv.setText(R.string.label_save_title);
        }
    }

    @Override
    protected String getRightTvText(TextView textView) {
        rightTv = textView;
        return getString(R.string.group_member_manage_save);
    }

    @Override
    protected void initView() {
        nameEt = wkVBinding.nameEt;
        nameEt.setFilters(new InputFilter[]{StringUtils.getInputFilter(LABEL_NAME_MAX_LENGTH)});
        wkVBinding.memberRecyclerView.setLayoutManager(new GridLayoutManager(this, 5));
        wkVBinding.memberRecyclerView.setNestedScrollingEnabled(false);
        wkVBinding.memberRecyclerView.setAdapter(memberAdapter);
        wkVBinding.deleteBtn.setVisibility(labelEntity == null ? View.GONE : View.VISIBLE);
        if (labelEntity != null) {
            nameEt.setText(labelEntity.name);
            nameEt.setSelection(nameEt.getText().length());
        }
        renderMembers();
        updateSaveState();
    }

    @Override
    protected void initListener() {
        nameEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveState();
            }
        });
        memberAdapter.setOnItemClickListener((adapter, view, position) -> {
            LabelMemberItem item = memberAdapter.getItem(position);
            if (item == null) {
                return;
            }
            if (item.itemType == LabelMemberItem.TYPE_ADD) {
                openChooseContacts();
                return;
            }
            if (item.itemType == LabelMemberItem.TYPE_REMOVE) {
                if (WKReader.isEmpty(currentMembers)) {
                    return;
                }
                deleteMode = !deleteMode;
                memberAdapter.setDeleteMode(deleteMode);
                return;
            }
            if (deleteMode) {
                removeMember(item.channel.channelID);
            }
        });
        wkVBinding.deleteBtn.setOnClickListener(v -> WKDialogUtils.getInstance().showDialog(this, getString(R.string.message_tips), getString(R.string.label_delete_confirm), true, getString(R.string.cancel), getString(R.string.sure), 0, Theme.colorAccount, index -> {
            if (index == 1 && labelEntity != null) {
                deleteLabel();
            }
        }));
    }

    @Override
    protected void initData() {
        super.initData();
        updateSaveState();
        if (labelEntity != null && !TextUtils.isEmpty(labelEntity.id)) {
            loadLatestLabel();
        }
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        if (!canSave()) {
            return;
        }
        if (labelEntity == null) {
            createLabel();
        } else {
            updateLabel();
        }
    }

    private void loadLatestLabel() {
        LabelModel.getInstance().getLabels(true, (code, msg, list) -> {
            if (code != HttpResponseCode.success || WKReader.isEmpty(list)) {
                return;
            }
            for (LabelEntity entity : list) {
                if (entity != null && entity.id.equals(labelEntity.id)) {
                    labelEntity = entity;
                    originalMembers.clear();
                    originalMembers.addAll(entity.members);
                    currentMembers.clear();
                    currentMembers.addAll(entity.members);
                    nameEt.setText(entity.name);
                    nameEt.setSelection(nameEt.getText().length());
                    renderMembers();
                    updateSaveState();
                    break;
                }
            }
        });
    }

    private void openChooseContacts() {
        EndpointManager.getInstance().invoke("choose_contacts", new ChooseContactsMenu(-1, false, false, new ArrayList<>(currentMembers), selectedList -> {
            currentMembers.clear();
            if (WKReader.isNotEmpty(selectedList)) {
                currentMembers.addAll(selectedList);
            }
            deleteMode = false;
            renderMembers();
            updateSaveState();
        }));
    }

    private void createLabel() {
        showTitleRightLoading();
        setRightViewEnabled(false);
        LabelModel.getInstance().createLabelWithContacts(getNameValue(), currentMembers, (code, msg) -> {
            hideTitleRightLoading();
            setRightViewEnabled(true);
            if (code != HttpResponseCode.success) {
                if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
                return;
            }
            setResult(RESULT_OK, new Intent());
            finish();
        });
    }

    private void updateLabel() {
        String newName = getNameValue();
        List<String> addedMembers = diffMembers(currentMembers, originalMembers);
        List<String> removedMembers = diffMembers(originalMembers, currentMembers);
        List<LabelAction> actions = new ArrayList<>();
        if (!TextUtils.equals(labelEntity.name, newName)) {
            actions.add(callback -> LabelModel.getInstance().updateLabelName(labelEntity.id, newName, callback));
        }
        if (WKReader.isNotEmpty(addedMembers)) {
            actions.add(callback -> LabelModel.getInstance().addContacts(labelEntity.id, addedMembers, callback));
        }
        if (WKReader.isNotEmpty(removedMembers)) {
            for (String uid : removedMembers) {
                actions.add(callback -> LabelModel.getInstance().removeContact(labelEntity.id, uid, callback));
            }
        }
        if (actions.isEmpty()) {
            finish();
            return;
        }
        showTitleRightLoading();
        setRightViewEnabled(false);
        executeActions(actions, 0);
    }

    private void executeActions(List<LabelAction> actions, int index) {
        if (index >= actions.size()) {
            hideTitleRightLoading();
            setRightViewEnabled(true);
            setResult(RESULT_OK, new Intent());
            finish();
            return;
        }
        actions.get(index).run((code, msg) -> {
            if (code != HttpResponseCode.success) {
                hideTitleRightLoading();
                setRightViewEnabled(true);
                if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
                return;
            }
            executeActions(actions, index + 1);
        });
    }

    private void deleteLabel() {
        showTitleRightLoading();
        setRightViewEnabled(false);
        LabelModel.getInstance().deleteLabel(labelEntity.id, (code, msg) -> {
            hideTitleRightLoading();
            setRightViewEnabled(true);
            if (code != HttpResponseCode.success) {
                if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
                return;
            }
            setResult(RESULT_OK, new Intent());
            finish();
        });
    }

    private void renderMembers() {
        List<LabelMemberItem> items = new ArrayList<>();
        for (WKChannel channel : currentMembers) {
            items.add(LabelMemberItem.member(channel));
        }
        items.add(LabelMemberItem.addAction());
        items.add(LabelMemberItem.removeAction());
        memberAdapter.setDeleteMode(deleteMode);
        memberAdapter.setList(items);
        wkVBinding.deleteBtn.setVisibility(isCreateMode ? View.GONE : View.VISIBLE);
    }

    private void removeMember(String uid) {
        for (int i = 0; i < currentMembers.size(); i++) {
            if (uid.equals(currentMembers.get(i).channelID)) {
                currentMembers.remove(i);
                break;
            }
        }
        if (currentMembers.isEmpty()) {
            deleteMode = false;
        }
        renderMembers();
        updateSaveState();
    }

    private boolean canSave() {
        return !TextUtils.isEmpty(getNameValue()) && WKReader.isNotEmpty(currentMembers);
    }

    private void updateSaveState() {
        boolean enable = canSave();
        setRightViewEnabled(enable);
        if (rightTv != null) {
            rightTv.setEnabled(enable);
            rightTv.setAlpha(enable ? 1f : 0.35f);
            rightTv.setTextColor(enable ? Theme.colorAccount : ContextCompat.getColor(this, R.color.color999));
        }
    }

    private String getNameValue() {
        return nameEt == null ? "" : nameEt.getText().toString().trim();
    }

    private List<String> diffMembers(List<WKChannel> source, List<WKChannel> target) {
        Set<String> targetIds = new HashSet<>();
        if (WKReader.isNotEmpty(target)) {
            for (WKChannel channel : target) {
                if (channel != null && !TextUtils.isEmpty(channel.channelID)) {
                    targetIds.add(channel.channelID);
                }
            }
        }
        List<String> result = new ArrayList<>();
        if (WKReader.isNotEmpty(source)) {
            for (WKChannel channel : source) {
                if (channel != null && !TextUtils.isEmpty(channel.channelID) && !targetIds.contains(channel.channelID)) {
                    result.add(channel.channelID);
                }
            }
        }
        return result;
    }

    private interface LabelAction {
        void run(com.chat.base.net.ICommonListener callback);
    }
}
