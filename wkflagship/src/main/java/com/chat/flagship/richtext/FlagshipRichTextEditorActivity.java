package com.chat.flagship.richtext;

/**
 * 富文本消息编辑页
 * Created by Luckclouds and chatGPT.
 */

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.ChooseResultModel;
import com.chat.base.glide.GlideUtils;
import com.chat.base.msg.ChatContentSpanType;
import com.chat.base.msg.IConversationContext;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.ui.components.ContactEditText;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipRichTextEditorLayoutBinding;
import com.chat.flagship.msgmodel.WKRichTextContent;
import com.chat.flagship.picture.util.ColorUtils;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMentionInfo;
import com.xinbida.wukongim.msgmodel.WKMsgEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class FlagshipRichTextEditorActivity extends WKBaseActivity<ActFlagshipRichTextEditorLayoutBinding> {
    private static final int REQUEST_MENTION = 8101;
    private static final int[] IMAGE_SCALE_OPTIONS = new int[]{25, 50, 75, 100};
    private static final char IMAGE_PLACEHOLDER = '\uFFFC';
    private static int nextImageLocalId = 1;

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
    private boolean sending;
    @Nullable
    private IConversationContext conversationContext;

    private interface UploadNodesCallback {
        void onSuccess(Map<Integer, WKRichTextContent.RichNode> nodes);

        void onFail();
    }

    private static class PendingImageItem {
        final int localId;
        final String path;
        int percent;

        PendingImageItem(String path) {
            this.localId = nextImageLocalId++;
            this.path = path;
            this.percent = 100;
        }
    }

    private static class RichPendingImageSpan extends ImageSpan {
        final PendingImageItem item;

        RichPendingImageSpan(Drawable drawable, PendingImageItem item) {
            super(drawable, ALIGN_BASELINE);
            this.item = item;
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
        initEditorImageTouch();
        initImeInsets();
        wkVBinding.underlineToolLabel.setPaintFlags(wkVBinding.underlineToolLabel.getPaintFlags() | TextPaint.UNDERLINE_TEXT_FLAG);
        wkVBinding.strikeToolLabel.setPaintFlags(wkVBinding.strikeToolLabel.getPaintFlags() | TextPaint.STRIKE_THRU_TEXT_FLAG);
        initEditorWatcher();
        hideLegacyAttachmentPreview();
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

    private void initImeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(wkVBinding.rootLayout, (v, insets) -> {
            WindowInsetsCompat imeInsets = insets;
            int imeBottom = imeInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = imeInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int rootBottomInset = Math.max(0, imeBottom - navBottom);
            int bottomPadding = imeBottom > 0 ? 0 : navBottom;
            wkVBinding.rootLayout.setPadding(
                    wkVBinding.rootLayout.getPaddingLeft(),
                    wkVBinding.rootLayout.getPaddingTop(),
                    wkVBinding.rootLayout.getPaddingRight(),
                    rootBottomInset
            );
            wkVBinding.bottomToolContainer.setPadding(
                    wkVBinding.bottomToolContainer.getPaddingLeft(),
                    wkVBinding.bottomToolContainer.getPaddingTop(),
                    wkVBinding.bottomToolContainer.getPaddingRight(),
                    bottomPadding
            );
            return insets;
        });
    }

    private void hideLegacyAttachmentPreview() {
        wkVBinding.attachmentHintTv.setVisibility(View.GONE);
        wkVBinding.imagePreviewScroll.setVisibility(View.GONE);
        wkVBinding.imagePreviewLayout.removeAllViews();
    }

    private void initEditorImageTouch() {
        wkVBinding.editorEt.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return false;
            }
            Layout layout = wkVBinding.editorEt.getLayout();
            Editable editable = wkVBinding.editorEt.getText();
            if (layout == null || editable == null) {
                return false;
            }
            int x = (int) event.getX() - wkVBinding.editorEt.getTotalPaddingLeft() + wkVBinding.editorEt.getScrollX();
            int y = (int) event.getY() - wkVBinding.editorEt.getTotalPaddingTop() + wkVBinding.editorEt.getScrollY();
            int line = layout.getLineForVertical(y);
            int offset = layout.getOffsetForHorizontal(line, x);
            RichPendingImageSpan span = findImageSpanAtOffset(editable, offset);
            if (span != null && isTouchInsideImageSpan(layout, span, x, y)) {
                showImageScaleSheet(span.item);
                return true;
            }
            return false;
        });
    }

    private boolean isTouchInsideImageSpan(Layout layout, RichPendingImageSpan span, int x, int y) {
        Editable editable = wkVBinding.editorEt.getText();
        if (editable == null) {
            return false;
        }
        int spanStart = editable.getSpanStart(span);
        if (spanStart < 0) {
            return false;
        }
        int line = layout.getLineForOffset(spanStart);
        int lineTop = layout.getLineTop(line);
        int lineBottom = layout.getLineBottom(line);
        Drawable drawable = span.getDrawable();
        int drawableWidth = drawable == null ? 0 : drawable.getBounds().width();
        int drawableHeight = drawable == null ? 0 : drawable.getBounds().height();
        float spanLeft = layout.getPrimaryHorizontal(spanStart);
        float spanRight = spanLeft + drawableWidth;
        int availableHeight = Math.max(0, lineBottom - lineTop);
        int spanTop = lineTop + Math.max(0, (availableHeight - drawableHeight) / 2);
        int spanBottom = spanTop + drawableHeight;
        return x >= spanLeft && x <= spanRight && y >= spanTop && y <= spanBottom;
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
        Editable editable = wkVBinding.editorEt.getText();
        String content = editable == null ? "" : editable.toString().replace(String.valueOf(IMAGE_PLACEHOLDER), "");
        if (content.trim().isEmpty() && pendingImages.isEmpty()) {
            showToast(R.string.flagship_rich_text_empty);
            return;
        }
        if (sending) {
            return;
        }
        sending = true;
        uploadPendingImages(context, new ArrayList<>(pendingImages), new UploadNodesCallback() {
            @Override
            public void onSuccess(Map<Integer, WKRichTextContent.RichNode> nodes) {
                WKRichTextContent richTextContent = buildRichTextContent(wkVBinding.editorEt.getText(), nodes);
                context.sendMessage(richTextContent);
                finish();
            }

            @Override
            public void onFail() {
                sending = false;
                WKToastUtils.getInstance().showToastNormal(getString(R.string.flagship_rich_text_upload_failed));
            }
        });
    }

    private WKRichTextContent buildRichTextContent(@Nullable Editable editable, Map<Integer, WKRichTextContent.RichNode> uploadedImageNodes) {
        WKRichTextContent richTextContent = new WKRichTextContent();
        richTextContent.nodes = buildOrderedNodes(editable, uploadedImageNodes);
        richTextContent.content = buildSummaryContent(editable, richTextContent.nodes);
        bindMentionInfo(richTextContent);
        return richTextContent;
    }

    private void bindMentionInfo(WKRichTextContent richTextContent) {
        List<String> uidList = ((ContactEditText) wkVBinding.editorEt).getAllUIDs();
        if (uidList == null || uidList.isEmpty()) {
            return;
        }
        List<String> mentionUids = new ArrayList<>();
        for (String uid : uidList) {
            if (TextUtils.isEmpty(uid)) {
                continue;
            }
            if ("-1".equals(uid)) {
                richTextContent.mentionAll = 1;
            } else if (!mentionUids.contains(uid)) {
                mentionUids.add(uid);
            }
        }
        if (!mentionUids.isEmpty()) {
            WKMentionInfo mentionInfo = new WKMentionInfo();
            mentionInfo.uids = mentionUids;
            richTextContent.mentionInfo = mentionInfo;
        }
    }

    private void uploadPendingImages(IConversationContext context, List<PendingImageItem> items, UploadNodesCallback callback) {
        if (items.isEmpty()) {
            callback.onSuccess(new HashMap<>());
            return;
        }
        Map<Integer, WKRichTextContent.RichNode> result = new HashMap<>();
        uploadPendingImages(context, items, 0, result, callback);
    }

    private void uploadPendingImages(IConversationContext context, List<PendingImageItem> items, int index, Map<Integer, WKRichTextContent.RichNode> result, UploadNodesCallback callback) {
        if (index >= items.size()) {
            callback.onSuccess(result);
            return;
        }
        PendingImageItem item = items.get(index);
        WKUploader.getInstance().getUploadFileUrl(context.getChatChannelInfo().channelID, context.getChatChannelInfo().channelType, item.path, (uploadUrl, fileUrl) -> {
            if (TextUtils.isEmpty(uploadUrl)) {
                callback.onFail();
                return;
            }
            WKUploader.getInstance().upload(uploadUrl, item.path, item.path, new WKUploader.IUploadBack() {
                @Override
                public void onSuccess(String url) {
                    result.put(item.localId, buildImageNode(item, url));
                    uploadPendingImages(context, items, index + 1, result, callback);
                }

                @Override
                public void onError() {
                    callback.onFail();
                }
            });
        });
    }

    private WKRichTextContent.RichNode buildImageNode(PendingImageItem item, String path) {
        WKRichTextContent.RichNode node = new WKRichTextContent.RichNode();
        node.kind = WKRichTextContent.NODE_KIND_IMAGE;
        node.path = path;
        node.widthPercent = item.percent;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(item.path, options);
        node.width = Math.max(0, options.outWidth);
        node.height = Math.max(0, options.outHeight);
        return node;
    }

    private List<WKRichTextContent.RichNode> buildOrderedNodes(@Nullable Editable editable, Map<Integer, WKRichTextContent.RichNode> uploadedImageNodes) {
        List<WKRichTextContent.RichNode> nodes = new ArrayList<>();
        if (editable == null) {
            return nodes;
        }
        List<WKMsgEntity> allEntities = buildAllTextEntities(editable);
        RichPendingImageSpan[] imageSpans = editable.getSpans(0, editable.length(), RichPendingImageSpan.class);
        List<RichPendingImageSpan> sortedSpans = new ArrayList<>();
        Collections.addAll(sortedSpans, imageSpans);
        sortedSpans.sort(Comparator.comparingInt(editable::getSpanStart));
        int cursor = 0;
        for (RichPendingImageSpan span : sortedSpans) {
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            if (start > cursor) {
                appendTextNode(nodes, editable.subSequence(cursor, start).toString(), buildSegmentEntities(allEntities, cursor, start));
            }
            WKRichTextContent.RichNode imageNode = uploadedImageNodes.get(span.item.localId);
            if (imageNode != null) {
                nodes.add(imageNode);
            }
            cursor = Math.max(cursor, end);
        }
        if (cursor < editable.length()) {
            appendTextNode(nodes, editable.subSequence(cursor, editable.length()).toString(), buildSegmentEntities(allEntities, cursor, editable.length()));
        }
        return nodes;
    }

    private void appendTextNode(List<WKRichTextContent.RichNode> nodes, String text, List<WKMsgEntity> entities) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        WKRichTextContent.RichNode node = new WKRichTextContent.RichNode();
        node.kind = WKRichTextContent.NODE_KIND_TEXT;
        node.text = text;
        node.entities = entities;
        nodes.add(node);
    }

    private String buildSummaryContent(@Nullable Editable editable, List<WKRichTextContent.RichNode> nodes) {
        String summary = editable == null ? "" : editable.toString().replace(String.valueOf(IMAGE_PLACEHOLDER), " ");
        summary = summary.replaceAll("\\s+", " ").trim();
        if (!TextUtils.isEmpty(summary)) {
            return summary;
        }
        for (WKRichTextContent.RichNode node : nodes) {
            if (node != null && WKRichTextContent.NODE_KIND_TEXT.equals(node.kind) && !TextUtils.isEmpty(node.text)) {
                String text = node.text.replaceAll("\\s+", " ").trim();
                if (!TextUtils.isEmpty(text)) {
                    return text;
                }
            }
        }
        return nodes.isEmpty() ? "" : getString(R.string.flagship_rich_text_image_only_summary);
    }

    private List<WKMsgEntity> buildAllTextEntities(@Nullable Editable editable) {
        List<WKMsgEntity> entities = new ArrayList<>(((ContactEditText) wkVBinding.editorEt).getAllEntity());
        entities.addAll(buildRichEntities(editable, Color.BLACK));
        entities.sort(Comparator.comparingInt(entity -> entity.offset));
        return mergeEntities(entities);
    }

    private List<WKMsgEntity> buildSegmentEntities(List<WKMsgEntity> source, int start, int end) {
        List<WKMsgEntity> result = new ArrayList<>();
        for (WKMsgEntity entity : source) {
            if (entity == null) {
                continue;
            }
            int entityStart = entity.offset;
            int entityEnd = entity.offset + entity.length;
            if (entityStart < start || entityEnd > end || entityEnd <= entityStart) {
                continue;
            }
            WKMsgEntity copy = copyEntity(entity);
            copy.offset = entityStart - start;
            result.add(copy);
        }
        return result;
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
        GlideUtils.getInstance().chooseIMG(this, 1, false, ChooseMimeType.img, false, true, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                for (ChooseResult result : paths) {
                    if (result.model == ChooseResultModel.image && !containsPendingImage(result.path)) {
                        PendingImageItem item = new PendingImageItem(result.path);
                        pendingImages.add(item);
                        insertPendingImage(item);
                    }
                }
                hideLegacyAttachmentPreview();
            }

            @Override
            public void onCancel() {
            }
        });
    }

    private void updateAttachmentState() {
        hideLegacyAttachmentPreview();
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
                    wkVBinding.editorEt.post(() -> {
                        wkVBinding.editorEt.requestFocus();
                        wkVBinding.editorEt.setGravity(Gravity.TOP | Gravity.START);
                        wkVBinding.editorEt.scrollTo(0, 0);
                        wkVBinding.editorEt.setSelection(wkVBinding.editorEt.getText() == null ? 0 : wkVBinding.editorEt.getText().length());
                        SoftKeyboardUtils.getInstance().showSoftKeyBoard(this, wkVBinding.editorEt);
                    });
                }
            }
        }
    }

    private boolean isGroupChat() {
        return conversationContext != null && conversationContext.getChatChannelInfo().channelType == WKChannelType.GROUP;
    }

    private void updateToolStates() {
        int activeColor = getColor(com.chat.base.R.color.blue);
        updateToolLabel(wkVBinding.colorToolLabel, colorEnabled, selectedColor);
        updateToolLabel(wkVBinding.sizeToolLabel, sizeEnabled, activeColor);
        updateToolLabel(wkVBinding.boldToolLabel, boldEnabled, activeColor);
        updateToolIcon(wkVBinding.imageToolLabel, false, activeColor);
        updateToolIcon(wkVBinding.italicToolLabel, italicEnabled, activeColor);
        updateToolLabel(wkVBinding.underlineToolLabel, underlineEnabled, activeColor);
        updateToolLabel(wkVBinding.strikeToolLabel, strikeEnabled, activeColor);
        updateToolLabel(wkVBinding.atToolLabel, isGroupChat(), activeColor);
        wkVBinding.sizeToolLabel.setText(String.valueOf(currentSizeSp));
        for (int i = 0; i < colorViews.size(); i++) {
            colorViews.get(i).setSelected(colorEnabled && ColorUtils.INSTANCE.getColorful().get(i) == selectedColor);
        }
        for (TextView view : sizeViews) {
            view.setTextColor(view.getText().toString().equals(String.valueOf(currentSizeSp)) && sizeEnabled ? activeColor : Color.parseColor("#666666"));
        }
    }

    private void updateToolLabel(TextView textView, boolean active, int activeColor) {
        textView.setAlpha(active ? 1f : 0.65f);
        textView.setTextColor(active ? activeColor : Color.parseColor("#8A8A8A"));
    }

    private void updateToolIcon(ImageView imageView, boolean active, int activeColor) {
        imageView.setAlpha(active ? 1f : 0.65f);
        imageView.setColorFilter(active ? activeColor : Color.parseColor("#8A8A8A"));
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
        return new View(this);
    }

    private void showImageScaleSheet(PendingImageItem item) {
        List<BottomSheetItem> list = new ArrayList<>();
        for (int percent : IMAGE_SCALE_OPTIONS) {
            list.add(new BottomSheetItem(getString(R.string.flagship_rich_text_image_scale_option, percent), 0, () -> {
                item.percent = percent;
                updateEditorImageSpan(item);
            }));
        }
        list.add(new BottomSheetItem(getString(R.string.flagship_rich_text_remove_image), 0, () -> {
            removePendingImage(item);
        }));
        WKDialogUtils.getInstance().showBottomSheet(this, getString(R.string.flagship_rich_text_image_scale_title), false, list);
    }

    private void insertPendingImage(PendingImageItem item) {
        Editable editable = wkVBinding.editorEt.getText();
        if (editable == null) {
            return;
        }
        int selection = Math.max(0, wkVBinding.editorEt.getSelectionStart());
        selection = Math.min(selection, editable.length());
        boolean needLeadingBreak = selection > 0 && editable.charAt(selection - 1) != '\n';
        boolean needTrailingBreak = selection < editable.length() && editable.charAt(selection) != '\n';
        StringBuilder tokenBuilder = new StringBuilder();
        if (needLeadingBreak) {
            tokenBuilder.append('\n');
        }
        int imageIndex = tokenBuilder.length();
        tokenBuilder.append(IMAGE_PLACEHOLDER);
        if (needTrailingBreak) {
            tokenBuilder.append('\n');
        }
        String token = tokenBuilder.toString();
        editable.insert(selection, token);
        int spanStart = selection + imageIndex;
        int spanEnd = spanStart + 1;
        editable.setSpan(createImageSpan(item), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        wkVBinding.editorEt.setSelection(Math.min(editable.length(), spanEnd + (needTrailingBreak ? 1 : 0)));
    }

    private RichPendingImageSpan createImageSpan(PendingImageItem item) {
        Drawable drawable = buildImagePreviewDrawable(item);
        return new RichPendingImageSpan(drawable, item);
    }

    private Drawable buildImagePreviewDrawable(PendingImageItem item) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(item.path, options);
        int maxWidth = Math.max(AndroidUtilities.dp(72f), (int) (AndroidUtilities.dp(220f) * Math.max(25, item.percent) / 100f));
        int targetWidth = options.outWidth > 0 ? Math.min(maxWidth, options.outWidth) : maxWidth;
        int targetHeight = options.outWidth > 0 && options.outHeight > 0
                ? Math.max(AndroidUtilities.dp(54f), targetWidth * options.outHeight / Math.max(1, options.outWidth))
                : AndroidUtilities.dp(140f);
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(item.path, decodeOptions);
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(Math.max(1, targetWidth), Math.max(1, targetHeight), Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.parseColor("#FFF2F2F2"));
        } else if (bitmap.getWidth() != targetWidth || bitmap.getHeight() != targetHeight) {
            bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        }
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setBounds(0, 0, targetWidth, targetHeight);
        return drawable;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        int height = Math.max(1, options.outHeight);
        int width = Math.max(1, options.outWidth);
        while ((height / inSampleSize) > reqHeight * 2 || (width / inSampleSize) > reqWidth * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    private void updateEditorImageSpan(PendingImageItem item) {
        Editable editable = wkVBinding.editorEt.getText();
        if (editable == null) {
            return;
        }
        RichPendingImageSpan[] spans = editable.getSpans(0, editable.length(), RichPendingImageSpan.class);
        for (RichPendingImageSpan span : spans) {
            if (span.item.localId != item.localId) {
                continue;
            }
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            editable.removeSpan(span);
            editable.setSpan(createImageSpan(item), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            break;
        }
    }

    private void removePendingImage(PendingImageItem item) {
        Editable editable = wkVBinding.editorEt.getText();
        if (editable == null) {
            pendingImages.remove(item);
            return;
        }
        RichPendingImageSpan[] spans = editable.getSpans(0, editable.length(), RichPendingImageSpan.class);
        for (RichPendingImageSpan span : spans) {
            if (span.item.localId != item.localId) {
                continue;
            }
            int start = editable.getSpanStart(span);
            int end = editable.getSpanEnd(span);
            editable.removeSpan(span);
            int deleteStart = start;
            int deleteEnd = end;
            if (deleteStart > 0 && editable.charAt(deleteStart - 1) == '\n') {
                deleteStart--;
            }
            if (deleteEnd < editable.length() && editable.charAt(deleteEnd) == '\n') {
                deleteEnd++;
            }
            editable.delete(deleteStart, deleteEnd);
            break;
        }
        pendingImages.remove(item);
    }

    @Nullable
    private RichPendingImageSpan findImageSpanAtOffset(Editable editable, int offset) {
        RichPendingImageSpan[] spans = editable.getSpans(offset, offset, RichPendingImageSpan.class);
        if (spans != null && spans.length > 0) {
            return spans[0];
        }
        if (offset > 0) {
            spans = editable.getSpans(offset - 1, offset - 1, RichPendingImageSpan.class);
            if (spans != null && spans.length > 0) {
                return spans[0];
            }
        }
        return null;
    }
}
