package com.chat.flagship.richtext;

/**
 * 富文本消息编辑页
 * Created by Luckclouds and chatGPT.
 */

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextPaint;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.ChooseResultModel;
import com.chat.base.glide.GlideUtils;
import com.chat.base.msg.ChatContentSpanType;
import com.chat.base.msg.IConversationContext;
import com.chat.base.ui.components.ContactEditText;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipRichTextEditorLayoutBinding;
import com.chat.flagship.picture.util.ColorUtils;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMentionInfo;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FlagshipRichTextEditorActivity extends WKBaseActivity<ActFlagshipRichTextEditorLayoutBinding> {
    private static final int REQUEST_MENTION = 8101;
    private static final int[] IMAGE_SCALE_OPTIONS = new int[]{25, 50, 75, 100};

    private final List<View> colorViews = new ArrayList<>();
    private final List<TextView> sizeViews = new ArrayList<>();
    private final List<PendingImageItem> pendingImages = new ArrayList<>();
    private boolean isInternalTextChange;
    private int lastChangeStart = -1;
    private int lastChangeCount = 0;
    private int selectedColor;
    private int currentSizeSp = 18;
    private boolean colorEnabled;
    private boolean sizeEnabled;
    private boolean boldEnabled;
    private boolean italicEnabled;
    private boolean underlineEnabled;
    private boolean strikeEnabled;
    @Nullable
    private IConversationContext conversationContext;

    private static class PendingImageItem {
        final String path;
        int percent;

        PendingImageItem(String path) {
            this.path = path;
            this.percent = 100;
        }
    }

    @Override
    protected ActFlagshipRichTextEditorLayoutBinding getViewBinding() {
        return ActFlagshipRichTextEditorLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_rich_text_title);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.flagship_rich_text_send);
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return -1;
    }

    @Override
    protected void initView() {
        conversationContext = FlagshipRichTextManager.getInstance().getConversationContext();
        selectedColor = Color.BLACK;
        View titleBottom = findViewById(com.chat.base.R.id.titleBottomLinView);
        if (titleBottom != null) {
            titleBottom.setVisibility(View.GONE);
        }
        initColorViews();
        initSizeViews();
        initToolViews();
        wkVBinding.underlineToolLabel.setPaintFlags(wkVBinding.underlineToolLabel.getPaintFlags() | TextPaint.UNDERLINE_TEXT_FLAG);
        wkVBinding.strikeToolLabel.setPaintFlags(wkVBinding.strikeToolLabel.getPaintFlags() | TextPaint.STRIKE_THRU_TEXT_FLAG);
        initEditorWatcher();
        updateAttachmentState();
        updateToolStates();
        wkVBinding.atTool.setVisibility(isGroupChat() ? View.VISIBLE : View.GONE);
        wkVBinding.editorEt.post(() -> {
            wkVBinding.editorEt.requestFocus();
            if (wkVBinding.editorEt.getText() != null) {
                wkVBinding.editorEt.setSelection(wkVBinding.editorEt.getText().length());
            }
            wkVBinding.editorEt.scrollTo(0, 0);
            SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, wkVBinding.editorEt);
        });
    }

    private void initColorViews() {
        colorViews.add(wkVBinding.colorWhiteView);
        colorViews.add(wkVBinding.colorBlackView);
        colorViews.add(wkVBinding.colorRedView);
        colorViews.add(wkVBinding.colorYellowView);
        colorViews.add(wkVBinding.colorGreenView);
        colorViews.add(wkVBinding.colorBlueView);
        colorViews.add(wkVBinding.colorPurpleView);
        for (int i = 0; i < colorViews.size(); i++) {
            final int index = i;
            colorViews.get(i).setOnClickListener(v -> {
                selectColorIndex(index);
                colorEnabled = true;
                updateToolStates();
            });
        }
        selectColorIndex(1);
    }

    private void initSizeViews() {
        sizeViews.add(wkVBinding.size14Tv);
        sizeViews.add(wkVBinding.size16Tv);
        sizeViews.add(wkVBinding.size18Tv);
        sizeViews.add(wkVBinding.size20Tv);
        sizeViews.add(wkVBinding.size24Tv);
        sizeViews.add(wkVBinding.size28Tv);
        sizeViews.add(wkVBinding.size32Tv);
        int[] sizes = new int[]{14, 16, 18, 20, 24, 28, 32};
        for (int i = 0; i < sizeViews.size(); i++) {
            final int size = sizes[i];
            sizeViews.get(i).setOnClickListener(v -> {
                currentSizeSp = size;
                sizeEnabled = true;
                updateToolStates();
            });
        }
        currentSizeSp = 18;
    }

    private void initToolViews() {
        wkVBinding.imageTool.setOnClickListener(v -> chooseImages());
        wkVBinding.colorTool.setOnClickListener(v -> {
            colorEnabled = !colorEnabled;
            wkVBinding.colorBar.setVisibility(colorEnabled ? View.VISIBLE : View.GONE);
            if (!colorEnabled) {
                wkVBinding.colorBar.setVisibility(View.GONE);
            }
            updateToolStates();
        });
        wkVBinding.sizeTool.setOnClickListener(v -> {
            sizeEnabled = !sizeEnabled;
            wkVBinding.sizeBar.setVisibility(sizeEnabled ? View.VISIBLE : View.GONE);
            if (!sizeEnabled) {
                wkVBinding.sizeBar.setVisibility(View.GONE);
            }
            updateToolStates();
        });
        wkVBinding.boldTool.setOnClickListener(v -> {
            boldEnabled = !boldEnabled;
            updateToolStates();
        });
        wkVBinding.italicTool.setOnClickListener(v -> {
            italicEnabled = !italicEnabled;
            updateToolStates();
        });
        wkVBinding.underlineTool.setOnClickListener(v -> {
            underlineEnabled = !underlineEnabled;
            updateToolStates();
        });
        wkVBinding.strikeTool.setOnClickListener(v -> {
            strikeEnabled = !strikeEnabled;
            updateToolStates();
        });
        wkVBinding.atTool.setOnClickListener(v -> chooseMentionMember());
    }

    private void initEditorWatcher() {
        wkVBinding.editorEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isInternalTextChange) {
                    return;
                }
                lastChangeStart = start;
                lastChangeCount = count;
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isInternalTextChange || lastChangeCount <= 0 || !hasActiveStyle()) {
                    return;
                }
                int end = Math.min(s.length(), lastChangeStart + lastChangeCount);
                if (lastChangeStart >= 0 && end > lastChangeStart) {
                    applyActiveStyles(s, lastChangeStart, end);
                }
                lastChangeStart = -1;
                lastChangeCount = 0;
            }
        });
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        sendRichText();
    }

    private void sendRichText() {
        IConversationContext context = FlagshipRichTextManager.getInstance().getConversationContext();
        if (context == null) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.flagship_rich_text_context_expired));
            finish();
            return;
        }
        String content = wkVBinding.editorEt.getText() == null ? "" : wkVBinding.editorEt.getText().toString();
        if (content.trim().isEmpty() && pendingImages.isEmpty()) {
            showToast(R.string.flagship_rich_text_empty);
            return;
        }
        for (PendingImageItem item : pendingImages) {
            context.sendMessage(new WKImageContent(item.path));
        }
        if (!content.trim().isEmpty()) {
            WKTextContent textContent = new WKTextContent(content);
            List<WKMsgEntity> entities = buildAllEntities(wkVBinding.editorEt.getText());
            if (!entities.isEmpty()) {
                textContent.entities = entities;
            }
            List<String> uidList = ((ContactEditText) wkVBinding.editorEt).getAllUIDs();
            if (uidList != null && !uidList.isEmpty()) {
                WKMentionInfo mentionInfo = new WKMentionInfo();
                mentionInfo.uids = new ArrayList<>(uidList);
                textContent.mentionInfo = mentionInfo;
            }
            context.sendMessage(textContent);
        }
        finish();
    }

    private List<WKMsgEntity> buildAllEntities(@Nullable Editable editable) {
        List<WKMsgEntity> entities = new ArrayList<>(((ContactEditText) wkVBinding.editorEt).getAllEntity());
        entities.addAll(buildRichEntities(editable, Color.BLACK));
        entities.sort(Comparator.comparingInt(entity -> entity.offset));
        return mergeEntities(entities);
    }

    private List<WKMsgEntity> buildRichEntities(@Nullable Editable editable, int defaultTextColor) {
        List<WKMsgEntity> entities = new ArrayList<>();
        if (editable == null || editable.length() == 0) {
            return entities;
        }
        StyleSpan[] styleSpans = editable.getSpans(0, editable.length(), StyleSpan.class);
        for (StyleSpan span : styleSpans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start < 0 || end <= start) {
                continue;
            }
            int style = span.getStyle();
            if (style == Typeface.BOLD || style == Typeface.BOLD_ITALIC) {
                entities.add(buildEntity(ChatContentSpanType.getRichBold(), start, end, null));
            }
            if (style == Typeface.ITALIC || style == Typeface.BOLD_ITALIC) {
                entities.add(buildEntity(ChatContentSpanType.getRichItalic(), start, end, null));
            }
        }
        ForegroundColorSpan[] colorSpans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : colorSpans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start < 0 || end <= start) {
                continue;
            }
            int color = span.getForegroundColor();
            if (color == defaultTextColor) {
                continue;
            }
            entities.add(buildEntity(ChatContentSpanType.getRichColor(), start, end, toColorValue(color)));
        }
        UnderlineSpan[] underlineSpans = editable.getSpans(0, editable.length(), UnderlineSpan.class);
        for (UnderlineSpan span : underlineSpans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start >= 0 && end > start) {
                entities.add(buildEntity(ChatContentSpanType.getRichUnderline(), start, end, null));
            }
        }
        StrikethroughSpan[] strikeSpans = editable.getSpans(0, editable.length(), StrikethroughSpan.class);
        for (StrikethroughSpan span : strikeSpans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start >= 0 && end > start) {
                entities.add(buildEntity(ChatContentSpanType.getRichStrike(), start, end, null));
            }
        }
        AbsoluteSizeSpan[] sizeSpans = editable.getSpans(0, editable.length(), AbsoluteSizeSpan.class);
        for (AbsoluteSizeSpan span : sizeSpans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start >= 0 && end > start) {
                entities.add(buildEntity(ChatContentSpanType.getRichSize(), start, end, String.valueOf(span.getSize())));
            }
        }
        return entities;
    }

    private List<WKMsgEntity> mergeEntities(List<WKMsgEntity> source) {
        if (source.isEmpty()) {
            return source;
        }
        source.sort((left, right) -> {
            if (left.offset != right.offset) {
                return Integer.compare(left.offset, right.offset);
            }
            if (left.length != right.length) {
                return Integer.compare(left.length, right.length);
            }
            return left.type.compareTo(right.type);
        });
        List<WKMsgEntity> result = new ArrayList<>();
        for (WKMsgEntity entity : source) {
            if (result.isEmpty()) {
                result.add(copyEntity(entity));
                continue;
            }
            WKMsgEntity last = result.get(result.size() - 1);
            int lastEnd = last.offset + last.length;
            int currentEnd = entity.offset + entity.length;
            if (Objects.equals(last.type, entity.type)
                    && Objects.equals(last.value, entity.value)
                    && entity.offset <= lastEnd) {
                last.length = Math.max(lastEnd, currentEnd) - last.offset;
            } else if (Objects.equals(last.type, entity.type)
                    && Objects.equals(last.value, entity.value)
                    && entity.offset == lastEnd) {
                last.length = currentEnd - last.offset;
            } else {
                result.add(copyEntity(entity));
            }
        }
        return result;
    }

    private WKMsgEntity copyEntity(WKMsgEntity source) {
        WKMsgEntity entity = new WKMsgEntity();
        entity.offset = source.offset;
        entity.length = source.length;
        entity.type = source.type;
        entity.value = source.value;
        return entity;
    }

    private WKMsgEntity buildEntity(String type, int start, int end, @Nullable String value) {
        WKMsgEntity entity = new WKMsgEntity();
        entity.offset = start;
        entity.length = end - start;
        entity.type = type;
        entity.value = value;
        return entity;
    }

    private String toColorValue(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private void applyActiveStyles(Editable editable, int start, int end) {
        isInternalTextChange = true;
        try {
            if (boldEnabled) {
                editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (italicEnabled) {
                editable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (underlineEnabled) {
                editable.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (strikeEnabled) {
                editable.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (colorEnabled) {
                editable.setSpan(new ForegroundColorSpan(selectedColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (sizeEnabled) {
                editable.setSpan(new AbsoluteSizeSpan(currentSizeSp, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } finally {
            isInternalTextChange = false;
        }
    }

    private boolean hasActiveStyle() {
        return boldEnabled || italicEnabled || underlineEnabled || strikeEnabled || colorEnabled || sizeEnabled;
    }

    private void chooseImages() {
        GlideUtils.getInstance().chooseIMG(this, 9, false, ChooseMimeType.img, false, true, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                for (ChooseResult result : paths) {
                    if (result.model == ChooseResultModel.image && !containsPendingImage(result.path)) {
                        pendingImages.add(new PendingImageItem(result.path));
                    }
                }
                updateAttachmentState();
            }

            @Override
            public void onCancel() {
            }
        });
    }

    private void updateAttachmentState() {
        if (pendingImages.isEmpty()) {
            wkVBinding.attachmentHintTv.setVisibility(View.GONE);
            wkVBinding.imagePreviewScroll.setVisibility(View.GONE);
            wkVBinding.imagePreviewLayout.removeAllViews();
            return;
        }
        wkVBinding.attachmentHintTv.setVisibility(View.VISIBLE);
        wkVBinding.attachmentHintTv.setText(getString(R.string.flagship_rich_text_selected_images, pendingImages.size()));
        wkVBinding.imagePreviewScroll.setVisibility(View.VISIBLE);
        wkVBinding.imagePreviewLayout.removeAllViews();
        for (PendingImageItem item : pendingImages) {
            wkVBinding.imagePreviewLayout.addView(buildPreviewItem(item));
        }
    }

    private void chooseMentionMember() {
        if (!isGroupChat()) {
            showToast(R.string.flagship_rich_text_at_group_only);
            return;
        }
        Intent intent = new Intent(this, FlagshipRichTextMentionActivity.class);
        intent.putExtra("group_id", conversationContext == null ? "" : conversationContext.getChatChannelInfo().channelID);
        startActivityForResult(intent, REQUEST_MENTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MENTION && resultCode == RESULT_OK && data != null) {
            String uid = data.getStringExtra("uid");
            String name = data.getStringExtra("name");
            if (!android.text.TextUtils.isEmpty(uid) && !android.text.TextUtils.isEmpty(name)) {
                isInternalTextChange = true;
                try {
                    wkVBinding.editorEt.requestFocus();
                    ((ContactEditText) wkVBinding.editorEt).addSpan("@" + name + " ", uid);
                } finally {
                    isInternalTextChange = false;
                    lastChangeStart = -1;
                    lastChangeCount = 0;
                }
            }
        }
    }

    private boolean isGroupChat() {
        return conversationContext != null && conversationContext.getChatChannelInfo().channelType == WKChannelType.GROUP;
    }

    private void updateToolStates() {
        updateToolLabel(wkVBinding.colorToolLabel, colorEnabled, selectedColor);
        updateToolLabel(wkVBinding.sizeToolLabel, sizeEnabled, getColor(com.chat.base.R.color.blue));
        updateToolLabel(wkVBinding.boldToolLabel, boldEnabled, getColor(com.chat.base.R.color.blue));
        updateToolLabel(wkVBinding.italicToolLabel, italicEnabled, getColor(com.chat.base.R.color.blue));
        updateToolLabel(wkVBinding.underlineToolLabel, underlineEnabled, getColor(com.chat.base.R.color.blue));
        updateToolLabel(wkVBinding.strikeToolLabel, strikeEnabled, getColor(com.chat.base.R.color.blue));
        updateToolLabel(wkVBinding.atToolLabel, isGroupChat(), getColor(com.chat.base.R.color.blue));
        wkVBinding.sizeToolLabel.setText(String.valueOf(currentSizeSp));
        for (int i = 0; i < colorViews.size(); i++) {
            colorViews.get(i).setSelected(colorEnabled && ColorUtils.INSTANCE.getColorful().get(i) == selectedColor);
        }
        for (TextView view : sizeViews) {
            view.setTextColor(view.getText().toString().equals(String.valueOf(currentSizeSp)) && sizeEnabled ? getColor(com.chat.base.R.color.blue) : Color.parseColor("#666666"));
        }
    }

    private void updateToolLabel(TextView textView, boolean active, int activeColor) {
        textView.setAlpha(active ? 1f : 0.65f);
        textView.setTextColor(active ? activeColor : Color.parseColor("#8A8A8A"));
    }

    private void selectColorIndex(int index) {
        if (index < 0 || index >= colorViews.size()) {
            return;
        }
        selectedColor = ColorUtils.INSTANCE.getColorful().get(index);
        updateToolStates();
    }

    private boolean containsPendingImage(String path) {
        for (PendingImageItem item : pendingImages) {
            if (Objects.equals(item.path, path)) {
                return true;
            }
        }
        return false;
    }

    private View buildPreviewItem(PendingImageItem item) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.rightMargin = AndroidUtilities.dp(10f);
        wrapper.setLayoutParams(wrapperParams);

        GradientDrawable cardDrawable = new GradientDrawable();
        cardDrawable.setColor(Color.WHITE);
        cardDrawable.setCornerRadius(AndroidUtilities.dp(10f));
        cardDrawable.setStroke(AndroidUtilities.dp(1f), Color.parseColor("#FFE7E7E7"));

        ImageView imageView = new ImageView(this);
        int previewWidth = Math.max(AndroidUtilities.dp(48f), AndroidUtilities.dp(96f * item.percent / 100f));
        int previewHeight = Math.max(AndroidUtilities.dp(36f), AndroidUtilities.dp(72f * item.percent / 100f));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(previewWidth, previewHeight);
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackground(cardDrawable);
        GlideUtils.getInstance().showImg(this, item.path, imageView);
        imageView.setOnClickListener(v -> showImageScaleSheet(item));

        TextView percentTv = new TextView(this);
        percentTv.setText(getString(R.string.flagship_rich_text_image_scale_option, item.percent));
        percentTv.setTextColor(Color.parseColor("#FF666666"));
        percentTv.setTextSize(11f);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        percentParams.topMargin = AndroidUtilities.dp(4f);
        percentTv.setLayoutParams(percentParams);

        wrapper.addView(imageView);
        wrapper.addView(percentTv);
        return wrapper;
    }

    private void showImageScaleSheet(PendingImageItem item) {
        List<BottomSheetItem> list = new ArrayList<>();
        for (int percent : IMAGE_SCALE_OPTIONS) {
            list.add(new BottomSheetItem(getString(R.string.flagship_rich_text_image_scale_option, percent), 0, () -> {
                item.percent = percent;
                updateAttachmentState();
            }));
        }
        list.add(new BottomSheetItem(getString(R.string.flagship_rich_text_remove_image), 0, () -> {
            pendingImages.remove(item);
            updateAttachmentState();
        }));
        WKDialogUtils.getInstance().showBottomSheet(this, getString(R.string.flagship_rich_text_image_scale_title), false, list);
    }
}
