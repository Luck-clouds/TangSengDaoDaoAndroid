package com.chat.uikit.setting;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKCommonUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActLockScreenPasswordLayoutBinding;
import com.chat.uikit.user.service.UserModel;

import java.util.ArrayList;
import java.util.List;

public class LockScreenPasswordActivity extends WKBaseActivity<ActLockScreenPasswordLayoutBinding> {
    public static final int SCENE_CREATE = 1;
    public static final int SCENE_CHANGE = 2;
    public static final int SCENE_VERIFY_SETTINGS = 3;
    public static final int SCENE_UNLOCK = 4;

    private static final String EXTRA_SCENE = "scene";

    private final List<String> keyValues = new ArrayList<>();
    private final EditText[] passViews = new EditText[6];
    private final StringBuilder passwordBuilder = new StringBuilder();

    private int scene;
    private boolean isConfirmStep;
    private String firstPassword = "";

    public static Intent buildIntent(Context context, int scene) {
        Intent intent = new Intent(context, LockScreenPasswordActivity.class);
        intent.putExtra(EXTRA_SCENE, scene);
        return intent;
    }

    @Override
    protected ActLockScreenPasswordLayoutBinding getViewBinding() {
        return ActLockScreenPasswordLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean supportSlideBack() {
        return getScene() != SCENE_UNLOCK;
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_lock_screen_password);
    }

    @Override
    protected void initPresenter() {
        scene = getScene();
    }

    @Override
    protected void initView() {
        SecurityPrivacyManager.getInstance().rememberLockActivity(this);
        passViews[0] = wkVBinding.passEt1;
        passViews[1] = wkVBinding.passEt2;
        passViews[2] = wkVBinding.passEt3;
        passViews[3] = wkVBinding.passEt4;
        passViews[4] = wkVBinding.passEt5;
        passViews[5] = wkVBinding.passEt6;
        initKeyboard();
        bindScene();
    }

    @Override
    protected void initListener() {
        wkVBinding.forgetPwdTv.setOnClickListener(v -> showForgetPasswordDialog());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        scene = getScene();
        isConfirmStep = false;
        firstPassword = "";
        clearPasswordInput();
        hideErrorTip(false);
        bindScene();
    }

    @Override
    protected void backListener(int type) {
        if (scene == SCENE_UNLOCK) {
            return;
        }
        super.backListener(type);
    }

    @Override
    public void onBackPressed() {
        if (scene == SCENE_UNLOCK) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        SecurityPrivacyManager.getInstance().forgetLockActivity(this);
        super.onDestroy();
    }

    private int getScene() {
        return getIntent().getIntExtra(EXTRA_SCENE, SCENE_CREATE);
    }

    // 不同场景共用一个页面，在这里切换提示文案和可见状态。
    private void bindScene() {
        wkVBinding.titleLayout.getRoot().setVisibility(scene == SCENE_UNLOCK ? View.GONE : View.VISIBLE);
        wkVBinding.avatarView.setVisibility(scene == SCENE_UNLOCK ? View.VISIBLE : View.GONE);
        wkVBinding.forgetPwdTv.setVisibility((scene == SCENE_VERIFY_SETTINGS || scene == SCENE_UNLOCK) ? View.VISIBLE : View.GONE);
        wkVBinding.forgetPwdTv.setText(com.chat.base.R.string.str_forget_pwd);
        if (scene == SCENE_UNLOCK) {
            wkVBinding.avatarView.setFollowHostSize(true);
            wkVBinding.avatarView.showAvatar(WKConfig.getInstance().getUid(), (byte) 1);
        } else {
            wkVBinding.avatarView.setFollowHostSize(false);
        }
        updatePrompt();
    }

    private void updatePrompt() {
        hideErrorTip(false);
        if (scene == SCENE_CREATE) {
            wkVBinding.promptTitleTv.setText(isConfirmStep ? R.string.security_confirm_lock_screen_password : R.string.security_set_lock_screen_password);
            wkVBinding.promptDescTv.setVisibility(isConfirmStep ? View.VISIBLE : View.GONE);
            if (isConfirmStep) {
                wkVBinding.promptDescTv.setText(R.string.security_lock_password_input_again);
            }
            return;
        }
        if (scene == SCENE_CHANGE) {
            wkVBinding.promptTitleTv.setText(isConfirmStep ? R.string.security_confirm_lock_screen_password : R.string.security_update_lock_screen_password);
            wkVBinding.promptDescTv.setVisibility(isConfirmStep ? View.VISIBLE : View.GONE);
            if (isConfirmStep) {
                wkVBinding.promptDescTv.setText(R.string.security_lock_password_input_again);
            }
            return;
        }
        wkVBinding.promptDescTv.setVisibility(View.GONE);
        wkVBinding.promptTitleTv.setText(R.string.security_lock_screen_password);
    }

    private void initKeyboard() {
        for (int i = 1; i < 13; i++) {
            if (i < 10) {
                keyValues.add(String.valueOf(i));
            } else if (i == 11) {
                keyValues.add("0");
            } else {
                keyValues.add("");
            }
        }
        wkVBinding.keyboardGridView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return keyValues.size();
            }

            @Override
            public Object getItem(int position) {
                return keyValues.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                KeyViewHolder holder;
                if (convertView == null) {
                    convertView = View.inflate(parent.getContext(), com.chat.base.R.layout.item_num_pwd, null);
                    holder = new KeyViewHolder();
                    holder.keyTv = convertView.findViewById(com.chat.base.R.id.btn_keys);
                    holder.deleteIv = convertView.findViewById(com.chat.base.R.id.delete_iv);
                    Theme.setColorFilter(parent.getContext(), holder.deleteIv, com.chat.base.R.color.colorDark);
                    convertView.setTag(holder);
                } else {
                    holder = (KeyViewHolder) convertView.getTag();
                }
                holder.keyTv.setText(position == 11 ? "" : keyValues.get(position));
                if (position == 9) {
                    holder.keyTv.setVisibility(View.GONE);
                    holder.deleteIv.setVisibility(View.GONE);
                } else if (position == 11) {
                    holder.keyTv.setVisibility(View.GONE);
                    holder.deleteIv.setVisibility(View.VISIBLE);
                } else {
                    holder.keyTv.setVisibility(View.VISIBLE);
                    holder.deleteIv.setVisibility(View.GONE);
                }
                return convertView;
            }
        });
        wkVBinding.keyboardGridView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 9) {
                return;
            }
            if (position == 11) {
                deleteDigit();
                return;
            }
            appendDigit(keyValues.get(position));
        });
    }

    private void appendDigit(String digit) {
        if (TextUtils.isEmpty(digit) || passwordBuilder.length() >= 6) {
            return;
        }
        passwordBuilder.append(digit);
        renderPassword();
        if (passwordBuilder.length() == 6) {
            handleCompleteInput(passwordBuilder.toString());
        }
    }

    private void deleteDigit() {
        if (passwordBuilder.length() <= 0) {
            return;
        }
        passwordBuilder.deleteCharAt(passwordBuilder.length() - 1);
        renderPassword();
    }

    private void renderPassword() {
        for (int i = 0; i < passViews.length; i++) {
            passViews[i].setText(i < passwordBuilder.length() ? "●" : "");
        }
    }

    private void clearPasswordInput() {
        passwordBuilder.setLength(0);
        renderPassword();
    }

    private void handleCompleteInput(String password) {
        if (scene == SCENE_CREATE || scene == SCENE_CHANGE) {
            handleCreateOrChange(password);
            return;
        }
        handleVerify(password);
    }

    private void handleCreateOrChange(String password) {
        if (!isConfirmStep) {
            firstPassword = password;
            isConfirmStep = true;
            clearPasswordInput();
            updatePrompt();
            return;
        }
        if (!TextUtils.equals(firstPassword, password)) {
            showToast(R.string.security_lock_password_not_match);
            clearPasswordInput();
            return;
        }
        String digestPassword = WKCommonUtils.digest(password);
        UserModel.getInstance().setLockScreenPwd(digestPassword, (code, msg) -> {
            if (code != HttpResponseCode.success) {
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.unknown_error) : msg);
                clearPasswordInput();
                return;
            }
            updateLocalLockPassword(digestPassword);
            WKSharedPreferencesUtil.getInstance().putInt("wk_lock_screen_pwd_count", 5);
            setResult(RESULT_OK);
            finish();
        });
    }

    private void handleVerify(String password) {
        String digestPassword = WKCommonUtils.digest(password);
        UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
        if (userInfo == null || !TextUtils.equals(digestPassword, userInfo.lock_screen_pwd)) {
            int remainCount = WKSharedPreferencesUtil.getInstance().getInt("wk_lock_screen_pwd_count", 5) - 1;
            WKSharedPreferencesUtil.getInstance().putInt("wk_lock_screen_pwd_count", Math.max(remainCount, 0));
            clearPasswordInput();
            if (remainCount <= 0) {
                SecurityPrivacyManager.getInstance().forgetPasswordAndLogout(this);
                return;
            }
            showErrorTip(getString(R.string.security_lock_password_error_with_count, remainCount));
            return;
        }
        hideErrorTip(false);
        WKSharedPreferencesUtil.getInstance().putInt("wk_lock_screen_pwd_count", 5);
        if (scene == SCENE_VERIFY_SETTINGS) {
            startActivity(new Intent(this, LockScreenPwdSettingActivity.class));
            finish();
            return;
        }
        SecurityPrivacyManager.getInstance().onLockScreenVerified();
        finish();
    }

    private void updateLocalLockPassword(String digestPassword) {
        UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
        if (userInfo == null) {
            return;
        }
        userInfo.lock_screen_pwd = digestPassword;
        WKConfig.getInstance().saveUserInfo(userInfo);
    }

    private void showErrorTip(String text) {
        wkVBinding.errorTipTv.setText(text);
        if (wkVBinding.errorTipTv.getVisibility() != View.VISIBLE) {
            wkVBinding.errorTipTv.setAlpha(0f);
            wkVBinding.errorTipTv.setTranslationY(-dpToPx(6));
            wkVBinding.errorTipTv.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.errorTipTv.animate().cancel();
        }
        wkVBinding.errorTipTv.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .start();
    }

    private void hideErrorTip(boolean animate) {
        wkVBinding.errorTipTv.animate().cancel();
        if (!animate || wkVBinding.errorTipTv.getVisibility() != View.VISIBLE) {
            wkVBinding.errorTipTv.setAlpha(1f);
            wkVBinding.errorTipTv.setTranslationY(0f);
            wkVBinding.errorTipTv.setVisibility(View.GONE);
            return;
        }
        wkVBinding.errorTipTv.animate()
                .alpha(0f)
                .translationY(-dpToPx(4))
                .setDuration(120L)
                .withEndAction(() -> {
                    wkVBinding.errorTipTv.setVisibility(View.GONE);
                    wkVBinding.errorTipTv.setAlpha(1f);
                    wkVBinding.errorTipTv.setTranslationY(0f);
                })
                .start();
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void showForgetPasswordDialog() {
        SecurityPrivacyManager.getInstance().forgetPasswordAndLogout(this);
    }

    private static class KeyViewHolder {
        TextView keyTv;
        ImageView deleteIv;
    }
}
