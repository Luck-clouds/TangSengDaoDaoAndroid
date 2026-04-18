package com.chat.flagship.picture;

/**
 * 图片编辑页面
 * Created by Luckclouds .
 */

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatChooseContacts;
import com.chat.base.endpoint.entity.ChooseChatMenu;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipPictureEditorLayoutBinding;
import com.chat.flagship.picture.bean.StickerAttrs;
import com.chat.flagship.picture.util.ColorUtils;
import com.chat.flagship.picture.view.FlagshipPictureClipView;
import com.chat.flagship.picture.view.FlagshipPictureEditorView;
import com.chat.flagship.picture.view.layer.OnStickerClickListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.msgmodel.WKImageContent;

import java.util.ArrayList;
import java.util.List;

public class FlagshipPictureEditorActivity extends WKBaseActivity<ActFlagshipPictureEditorLayoutBinding> {
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_FROM_VIEWER = "from_viewer";
    public static final String EXTRA_SHOW_SAVE_DIALOG = "show_save_dialog";
    public static final String EXTRA_CALLBACK_KEY = "callback_key";

    private String sourcePath;
    private boolean fromViewer;
    private boolean showSaveDialog;
    private String callbackKey;
    private Dialog textDialog;
    private Dialog clipDialog;
    private FlagshipPictureEditorView.Mode currentMode;
    private final List<View> colorViews = new ArrayList<>();
    private int selectedColor = Color.WHITE;

    @Override
    protected ActFlagshipPictureEditorLayoutBinding getViewBinding() {
        return ActFlagshipPictureEditorLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText("");
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return -1;
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.flagship_picture_complete);
    }

    @Override
    protected void rightLayoutClick() {
        onCompleteClick();
    }

    @Override
    protected void initView() {
        sourcePath = getIntent().getStringExtra(EXTRA_PATH);
        fromViewer = getIntent().getBooleanExtra(EXTRA_FROM_VIEWER, false);
        showSaveDialog = getIntent().getBooleanExtra(EXTRA_SHOW_SAVE_DIALOG, false);
        callbackKey = getIntent().getStringExtra(EXTRA_CALLBACK_KEY);
        if (TextUtils.isEmpty(sourcePath)) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.file_read_failed));
            finish();
            return;
        }
        applyTransparentTitleBar();
        initToolViews();
        wkVBinding.editorView.setBitmapPathOrUri(sourcePath, null);
        wkVBinding.editorView.setGraffitiColor(selectedColor);
    }

    private void initToolViews() {
        colorViews.add(wkVBinding.colorWhiteView);
        colorViews.add(wkVBinding.colorBlackView);
        colorViews.add(wkVBinding.colorRedView);
        colorViews.add(wkVBinding.colorYellowView);
        colorViews.add(wkVBinding.colorGreenView);
        colorViews.add(wkVBinding.colorBlueView);
        colorViews.add(wkVBinding.colorPurpleView);
        for (int i = 0; i < colorViews.size(); i++) {
            final int index = i;
            colorViews.get(i).setOnClickListener(v -> selectColor(index));
        }
        selectColor(0);
        wkVBinding.textTool.setOnClickListener(v -> showTextDialog(null));
        wkVBinding.graffitiTool.setOnClickListener(v -> switchMode(FlagshipPictureEditorView.Mode.GRAFFITI));
        wkVBinding.mosaicTool.setOnClickListener(v -> switchMode(FlagshipPictureEditorView.Mode.MOSAIC));
        wkVBinding.clipTool.setOnClickListener(v -> showClipDialog());
        wkVBinding.undoTool.setOnClickListener(v -> {
            if (currentMode == FlagshipPictureEditorView.Mode.MOSAIC) {
                wkVBinding.editorView.mosaicUndo();
            } else {
                wkVBinding.editorView.graffitiUndo();
            }
        });
        switchMode(FlagshipPictureEditorView.Mode.GRAFFITI);
    }

    private void switchMode(FlagshipPictureEditorView.Mode mode) {
        currentMode = mode;
        wkVBinding.editorView.setMode(mode);
        boolean graffiti = mode == FlagshipPictureEditorView.Mode.GRAFFITI;
        boolean mosaic = mode == FlagshipPictureEditorView.Mode.MOSAIC;
        wkVBinding.colorBar.setVisibility(graffiti ? View.VISIBLE : View.INVISIBLE);
        wkVBinding.graffitiIcon.setSelected(graffiti);
        wkVBinding.mosaicIcon.setSelected(mosaic);
    }

    private void applyTransparentTitleBar() {
        View root = findViewById(com.chat.base.R.id.statusBarView);
        if (root != null) {
            root.setBackgroundColor(Color.TRANSPARENT);
        }
        View titleBar = findViewById(com.chat.base.R.id.titleBarLayout);
        if (titleBar != null) {
            titleBar.setBackgroundColor(Color.TRANSPARENT);
        }
        View titleBottom = findViewById(com.chat.base.R.id.titleBottomLinView);
        if (titleBottom != null) {
            titleBottom.setVisibility(View.GONE);
        }
        TextView titleCenterTv = findViewById(com.chat.base.R.id.titleCenterTv);
        if (titleCenterTv != null) {
            titleCenterTv.setText("");
            titleCenterTv.setTextColor(Color.WHITE);
        }
        TextView titleRightTv = findViewById(com.chat.base.R.id.titleRightTv);
        if (titleRightTv != null) {
            titleRightTv.setTextColor(Color.WHITE);
        }
        ImageView backIv = findViewById(com.chat.base.R.id.backIv);
        if (backIv != null && backIv.getDrawable() != null) {
            backIv.getDrawable().mutate().setTint(Color.WHITE);
            backIv.setColorFilter(Color.WHITE);
        }
    }

    private void selectColor(int index) {
        selectedColor = ColorUtils.INSTANCE.getColorful().get(index);
        for (int i = 0; i < colorViews.size(); i++) {
            colorViews.get(i).setSelected(i == index);
        }
        wkVBinding.editorView.setGraffitiColor(selectedColor);
    }

    private void onCompleteClick() {
        if (!wkVBinding.editorView.hasBitmap()) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.flagship_picture_editor_export_failed));
            return;
        }
        Bitmap bitmap = wkVBinding.editorView.saveBitmap();
        if (bitmap == null) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.flagship_picture_editor_export_failed));
            return;
        }
        if (showSaveDialog) {
            showCompleteSheet(bitmap);
            return;
        }
        ImageUtils.getInstance().saveBitmap(this, bitmap, false, path -> {
            if (!TextUtils.isEmpty(callbackKey)) {
                FlagshipPictureEditorManager.getInstance().dispatchEditResult(this, callbackKey, path);
            }
            finish();
        });
    }

    private void showCompleteSheet(Bitmap bitmap) {
        List<BottomSheetItem> list = new ArrayList<>();
        list.add(new BottomSheetItem(getString(R.string.flagship_picture_save_local), com.chat.base.R.mipmap.msg_download, () ->
                ImageUtils.getInstance().saveBitmap(this, bitmap, true, path -> {
                    WKToastUtils.getInstance().showToastNormal(getString(com.chat.base.R.string.saved_album));
                    finish();
                })));
        list.add(new BottomSheetItem(getString(R.string.flagship_picture_forward_friend), com.chat.base.R.mipmap.msg_forward, () ->
                ImageUtils.getInstance().saveBitmap(this, bitmap, false, this::forwardEditedImage)));
        WKDialogUtils.getInstance().showBottomSheet(this, null, false, list);
    }

    private void forwardEditedImage(String path) {
        WKImageContent imageContent = new WKImageContent(path);
        EndpointManager.getInstance().invoke(EndpointSID.showChooseChatView, new ChooseChatMenu(new ChatChooseContacts(list -> {
            if (list == null || list.isEmpty()) {
                return;
            }
            for (WKChannel channel : list) {
                WKIM.getInstance().getMsgManager().send(imageContent, channel);
            }
            WKToastUtils.getInstance().showToastNormal(getString(R.string.flagship_picture_forward_done));
            finish();
        }), imageContent));
    }

    private void showTextDialog(StickerAttrs currentAttrs) {
        if (textDialog != null && textDialog.isShowing()) {
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_flagship_picture_text, null, false);
        EditText editText = view.findViewById(R.id.editText);
        TextView cancelTv = view.findViewById(R.id.cancelTv);
        TextView confirmTv = view.findViewById(R.id.confirmTv);
        List<View> dialogColorViews = new ArrayList<>();
        dialogColorViews.add(view.findViewById(R.id.colorWhiteView));
        dialogColorViews.add(view.findViewById(R.id.colorBlackView));
        dialogColorViews.add(view.findViewById(R.id.colorRedView));
        dialogColorViews.add(view.findViewById(R.id.colorYellowView));
        dialogColorViews.add(view.findViewById(R.id.colorGreenView));
        dialogColorViews.add(view.findViewById(R.id.colorBlueView));
        dialogColorViews.add(view.findViewById(R.id.colorPurpleView));
        final int[] textColor = {selectedColor};
        for (int i = 0; i < dialogColorViews.size(); i++) {
            final int index = i;
            dialogColorViews.get(i).setOnClickListener(v -> {
                textColor[0] = ColorUtils.INSTANCE.getColorful().get(index);
                editText.setTextColor(textColor[0]);
                for (int j = 0; j < dialogColorViews.size(); j++) {
                    dialogColorViews.get(j).setSelected(j == index);
                }
            });
        }
        dialogColorViews.get(0).setSelected(true);
        editText.setTextColor(textColor[0]);
        if (currentAttrs != null) {
            editText.setText(currentAttrs.getDescription());
        }
        Dialog dialog = new Dialog(this);
        dialog.setContentView(view);
        textDialog = dialog;
        cancelTv.setOnClickListener(v -> dialog.dismiss());
        confirmTv.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                dialog.dismiss();
                return;
            }
            StickerAttrs attrs = currentAttrs == null
                    ? new StickerAttrs(buildTextBitmap(text, textColor[0]), text, 0f, 1f, 0f, 0f)
                    : new StickerAttrs(buildTextBitmap(text, textColor[0]), text, currentAttrs.getRotation(), currentAttrs.getScale(), currentAttrs.getTranslateX(), currentAttrs.getTranslateY());
            wkVBinding.editorView.setSticker(attrs, new OnStickerClickListener() {
                @Override
                public void onClick(StickerAttrs attrs) {
                    showTextDialog(attrs);
                }
            });
            wkVBinding.editorView.setMode(FlagshipPictureEditorView.Mode.STICKER);
            dialog.dismiss();
        });
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, 0);
            }
        });
        dialog.setOnDismissListener(d -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
            }
            textDialog = null;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private Bitmap buildTextBitmap(String text, int color) {
        AppCompatTextView textView = new AppCompatTextView(this);
        textView.setText(text);
        textView.setTextColor(color);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        textView.setPadding(24, 16, 24, 16);
        int widthSpec = View.MeasureSpec.makeMeasureSpec((int) (getResources().getDisplayMetrics().widthPixels * 0.7f), View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        textView.measure(widthSpec, heightSpec);
        textView.layout(0, 0, textView.getMeasuredWidth(), textView.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(textView.getMeasuredWidth(), textView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        textView.draw(canvas);
        return bitmap;
    }

    private void showClipDialog() {
        Bitmap current = wkVBinding.editorView.saveBitmap();
        View view = getLayoutInflater().inflate(R.layout.dialog_flagship_picture_clip, null, false);
        FlagshipPictureClipView clipView = view.findViewById(R.id.clipView);
        TextView cancelTv = view.findViewById(R.id.cancelTv);
        TextView rotateTv = view.findViewById(R.id.rotateTv);
        TextView confirmTv = view.findViewById(R.id.confirmTv);
        clipView.setBitmapResource(current);
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(view);
        clipDialog = dialog;
        cancelTv.setOnClickListener(v -> dialog.dismiss());
        rotateTv.setOnClickListener(v -> clipView.rotate());
        confirmTv.setOnClickListener(v -> {
            wkVBinding.editorView.setBitmapResource(clipView.saveBitmap());
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> clipDialog = null);
        dialog.show();
    }
}
