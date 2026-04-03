package com.chat.uikit.contacts.label;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChooseContactsMenu;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActCommonListLayoutBinding;
import com.xinbida.wukongim.entity.WKChannel;

import java.util.ArrayList;
import java.util.List;

public class LabelListActivity extends WKBaseActivity<ActCommonListLayoutBinding> {
    private final LabelListAdapter adapter = new LabelListAdapter();

    @Override
    protected ActCommonListLayoutBinding getViewBinding() {
        return ActCommonListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.label_title);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.label_new);
    }

    @Override
    protected void initView() {
        initAdapter(wkVBinding.recyclerView, adapter);
        wkVBinding.nodataTv.setText(R.string.label_empty);
    }

    @Override
    protected void initListener() {
        adapter.addChildClickViewIds(R.id.contentLayout);
        adapter.setOnItemChildClickListener((baseQuickAdapter, view, position) -> {
            LabelEntity entity = adapter.getItem(position);
            if (entity == null) {
                return;
            }
            Intent intent = new Intent(this, LabelEditActivity.class);
            intent.putExtra(LabelEditActivity.EXTRA_LABEL, entity);
            startActivity(intent);
        });
    }

    @Override
    protected void initData() {
        super.initData();
        loadLabels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLabels();
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        openChooseContacts(new ArrayList<>());
    }

    private void loadLabels() {
        LabelModel.getInstance().getLabels(true, (code, msg, list) -> {
            if (WKReader.isEmpty(list)) {
                adapter.setList(new ArrayList<>());
                wkVBinding.nodataTv.setVisibility(View.VISIBLE);
                return;
            }
            wkVBinding.nodataTv.setVisibility(View.GONE);
            adapter.setList(list);
        });
    }

    private void openChooseContacts(List<WKChannel> selectedList) {
        EndpointManager.getInstance().invoke("choose_contacts", new ChooseContactsMenu(-1, true, false, selectedList, result -> {
            if (WKReader.isEmpty(result)) {
                return;
            }
            Intent intent = new Intent(this, LabelEditActivity.class);
            intent.putParcelableArrayListExtra(LabelEditActivity.EXTRA_MEMBERS, new ArrayList<>(result));
            startActivity(intent);
        }));
    }
}
