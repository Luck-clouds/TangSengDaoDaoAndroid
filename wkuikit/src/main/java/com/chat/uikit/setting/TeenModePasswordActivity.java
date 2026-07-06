package com.chat.uikit.setting;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.utils.WKDialogUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActTeenModePasswordLayoutBinding;

import java.util.ArrayList;
import java.util.List;

public class TeenModePasswordActivity extends WKBaseActivity<ActTeenModePasswordLayoutBinding> {
    public static final int SCENE_CREATE = 1;
    public static final int SCENE_VERIFY_UNLOCK = 2;
    public static final int SCENE_VERIFY_DISABLE = 3;

    private static final String EXTRA_SCENE = "scene";

    private final List<String> keyValues = new ArrayList<>();
    private final EditText[] passViews = new EditText[6];
    private final StringBuilder passwordBuilder = new StringBuilder();

    private int scene;
    private boolean isConfirmStep;
    private String firstPassword = "";

    public static Intent buildIntent(Context context, int scene) {
        Intent intent = new Intent(context, TeenModePasswordActivity.class);
        intent.putExtra(EXTRA_SCENE, scene);
        return intent;
    }

    @Override
    protected ActTeenModePasswordLayoutBinding getViewBinding() {
        return ActTeenModePasswordLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean supportSlideBack() {
        return false;
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.teen_mode);
    }

    @Override
    protected void initPresenter() {
        scene = getScene();
    }

    @Override
    protected void initView() {
        TeenModeManager.getInstance().rememberPasswordActivity(this);
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
        if (scene == SCENE_VERIFY_UNLOCK) {
            return;
        }
        super.backListener(type);
    }

    @Override
    public void onBackPressed() {
        if (scene == SCENE_VERIFY_UNLOCK) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        TeenModeManager.getInstance().forgetPasswordActivity(this);
        super.onDestroy();
    }

    private int getScene() {
        return getIntent().getIntExtra(EXTRA_SCENE, SCENE_CREATE);
    }

    private void bindScene() {
        boolean isUnlockScene = scene == SCENE_VERIFY_UNLOCK;
        wkVBinding.titleLayout.getRoot().setVisibility(isUnlockScene ? View.GONE : View.VISIBLE);
        wkVBinding.avatarView.setVisibility(isUnlockScene ? View.VISIBLE : View.GONE);
        wkVBinding.forgetPwdTv.setVisibility(scene == SCENE_CREATE ? View.GONE : View.VISIBLE);
        wkVBinding.forgetPwdTv.setText(com.chat.base.R.string.str_forget_pwd);
        if (isUnlockScene) {
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
            wkVBinding.promptTitleTv.setText(isConfirmStep ? R.string.teen_mode_confirm_password : R.string.teen_mode_set_password);
            wkVBinding.promptDescTv.setVisibility(isConfirmStep ? View.VISIBLE : View.GONE);
            if (isConfirmStep) {
                wkVBinding.promptDescTv.setText(R.string.teen_mode_input_again);
            }
            return;
        }
        wkVBinding.promptTitleTv.setText(R.string.teen_mode_protection_title);
        wkVBinding.promptDescTv.setVisibility(View.VISIBLE);
        wkVBinding.promptDescTv.setText(scene == SCENE_VERIFY_DISABLE ? R.string.teen_mode_disable_password : R.string.teen_mode_verify_password);
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
                    com.chat.base.ui.Theme.setColorFilter(parent.getContext(), holder.deleteIv, com.chat.base.R.color.colorDark);
                    convertView.setTag(holder);
                } else {
                    holder = (KeyViewHolder) convertView.getTag();
                }
                final int itemPosition = position;
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
                convertView.setClickable(position != 9);
                convertView.setFocusable(false);
                convertView.setOnClickListener(v -> handleKeyboardItemClick(itemPosition));
                return convertView;
            }
        });
        wkVBinding.keyboardGridView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });
    }

    private void handleKeyboardItemClick(int position) {
        if (position == 9) {
            return;
        }
        if (position == 11) {
            deleteDigit();
            return;
        }
        appendDigit(keyValues.get(position));
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
        if (scene == SCENE_CREATE) {
            handleCreate(password);
            return;
        }
        handleVerify(password);
    }

    private void handleCreate(String password) {
        if (!isConfirmStep) {
            firstPassword = password;
            isConfirmStep = true;
            clearPasswordInput();
            updatePrompt();
            return;
        }
        if (!TextUtils.equals(firstPassword, password)) {
            showToast(R.string.teen_mode_password_not_match);
            clearPasswordInput();
            return;
        }
        TeenModeManager.getInstance().enable(com.chat.base.utils.WKCommonUtils.digest(password));
        setResult(RESULT_OK);
        finish();
    }

    private void handleVerify(String password) {
        if (!TeenModeManager.getInstance().verifyPassword(password)) {
            int remainCount = TeenModeManager.getInstance().consumeRetryCountOnError();
            clearPasswordInput();
            if (remainCount <= 0) {
                TeenModeManager.getInstance().forgetPasswordAndLogout();
                return;
            }
            showErrorTip(getString(R.string.teen_mode_password_error_with_count, remainCount));
            return;
        }
        hideErrorTip(false);
        TeenModeManager.getInstance().resetRetryCount();
        if (scene == SCENE_VERIFY_DISABLE) {
            TeenModeManager.getInstance().disable();
            setResult(RESULT_OK);
            finish();
            return;
        }
        TeenModeManager.getInstance().onPasswordVerified();
        finish();
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
        WKDialogUtils.getInstance().showDialog(
                this,
                getString(R.string.teen_mode_forget_password_title),
                getString(R.string.teen_mode_forget_password_desc),
                true,
                "",
                "",
                0,
                0,
                index -> {
                    if (index == 1) {
                        TeenModeManager.getInstance().forgetPasswordAndLogout();
                    }
                }
        );
    }

    private static class KeyViewHolder {
        TextView keyTv;
        ImageView deleteIv;
    }
}
