package com.chat.uikit.fragment;

import android.content.Intent;
import android.text.TextUtils;

import com.chat.base.base.WKBaseFragment;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.PersonalInfoMenu;
import com.chat.base.ui.Theme;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.FragMyLayoutBinding;
import com.chat.uikit.user.MyInfoActivity;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 2019-11-12 14:58
 * 我的
 */
public class MyFragment extends WKBaseFragment<FragMyLayoutBinding> {
    private static final String INVITE_CODE_BOUND_KEY = "invite_code_bound";
    private PersonalItemAdapter adapter;

    @Override
    protected FragMyLayoutBinding getViewBinding() {
        return FragMyLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        wkVBinding.recyclerView.setNestedScrollingEnabled(false);
        adapter = new PersonalItemAdapter(new ArrayList<>());
        initAdapter(wkVBinding.recyclerView, adapter);
        //设置数据item
        setPersonalMenus();
    }

    @Override
    protected void initPresenter() {
        wkVBinding.avatarView.setSize(90);
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        Theme.setPressedBackground(wkVBinding.qrIv);
    }

    @Override
    protected void initListener() {
        adapter.setOnItemClickListener((adapter1, view, position) -> SingleClickUtil.determineTriggerSingleClick(view, view1 -> {
            PersonalInfoMenu menu = (PersonalInfoMenu) adapter1.getItem(position);
            if (menu != null && menu.iPersonalInfoMenuClick != null) {
                menu.iPersonalInfoMenuClick.onClick();
            }
        }));
        SingleClickUtil.onSingleClick(wkVBinding.avatarView, view -> gotoMyInfo());
        SingleClickUtil.onSingleClick(wkVBinding.qrIv, view -> gotoMyInfo());
        EndpointManager.getInstance().setMethod("refresh_personal_center", object -> {
            setPersonalMenus();
            return null;
        });
    }

    void gotoMyInfo() {
//        String str = WKDeviceUtils.getSignature(getActivity());
//        Log.e("签名",str+"");
        startActivity(new Intent(getActivity(), MyInfoActivity.class));
    }

    @Override
    public void onResume() {
        super.onResume();
        wkVBinding.nameTv.setText(WKConfig.getInstance().getUserInfo().name);
        wkVBinding.avatarView.showAvatar(WKConfig.getInstance().getUid(), WKChannelType.PERSONAL);
        if (null != adapter) {
            setPersonalMenus();
            // HUAWEI 分支不检查应用内更新，也不显示新版本角标。
        }
    }

    private void setPersonalMenus() {
        if (adapter == null) {
            return;
        }
        List<PersonalInfoMenu> endpoints = EndpointManager.getInstance().invokes(EndpointCategory.personalCenter, null);
        List<PersonalInfoMenu> menus = new ArrayList<>();
        if (endpoints != null) {
            menus.addAll(endpoints);
        }
        if (isInviteCodeBound()) {
            for (int i = menus.size() - 1; i >= 0; i--) {
                PersonalInfoMenu menu = menus.get(i);
                if (menu != null && "invite_code".equals(menu.sid)) {
                    menus.remove(i);
                }
            }
        }
        adapter.setList(menus);
    }

    private boolean isInviteCodeBound() {
        String uid = WKConfig.getInstance().getUid();
        return !TextUtils.isEmpty(uid) && WKSharedPreferencesUtil.getInstance().getBoolean(uid + "_" + INVITE_CODE_BOUND_KEY, false);
    }

    @Override
    public void onDestroy() {
        EndpointManager.getInstance().remove("refresh_personal_center");
        super.onDestroy();
    }
}
