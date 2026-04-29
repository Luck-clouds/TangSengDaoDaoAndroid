package com.chat.flagship.reaction;

/**
 * 消息回应 UI 与资源管理
 * Created by Luckclouds .
 */

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chat.base.R;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.entity.ShowMsgReactionMenu;
import com.chat.base.msg.ChatAdapter;
import com.chat.base.msgitem.ReactionSticker;
import com.chat.base.msgitem.WKChatIteMsgFromType;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.ui.components.BottomSheet;
import com.chat.flagship.entity.FlagshipReactionSyncEntity;
import com.chat.flagship.service.FlagshipReactionModel;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsgReaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FlagshipReactionManager {
    private static final List<ReactionSpec> DEFAULT_SPECS = buildDefaultSpecs();

    private static Dialog reactionUsersDialog;

    private FlagshipReactionManager() {
    }

    public static List<ReactionSticker> getReactionStickers() {
        List<ReactionSticker> list = new ArrayList<>();
        for (ReactionSpec spec : DEFAULT_SPECS) {
            // 菜单点击回调拿的是 name，这里放接口值；displayName 给菜单兜底显示预留。
            list.add(new ReactionSticker(spec.apiName, spec.displayValue, spec.resourceId));
        }
        return list;
    }

    public static void bindReactionView(ShowMsgReactionMenu menu) {
        if (menu == null || menu.getParentView() == null) {
            return;
        }
        FrameLayout parentView = menu.getParentView();
        FlagshipReactionModel.getInstance().ensureChannelReactionSynced(menu.getWkMsg(), menu.getChatAdapter());
        List<WKMsgReaction> activeList = getActiveReactionList(menu.getList());
        parentView.removeAllViews();
        if (activeList.isEmpty()) {
            parentView.setVisibility(View.GONE);
            return;
        }
        parentView.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams parentParams = ensureParentParams(parentView, menu.getFrom());
        parentView.setLayoutParams(parentParams);

        LinearLayout container = new LinearLayout(parentView.getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Set<String> currentUserEmojis = findCurrentUserEmojis(activeList);
        LinkedHashMap<String, Integer> reactionCountMap = sortReactionCountMap(countByEmoji(activeList));
        if (reactionCountMap.isEmpty()) {
            parentView.setVisibility(View.GONE);
            return;
        }
        View itemView = LayoutInflater.from(parentView.getContext()).inflate(com.chat.flagship.R.layout.item_flagship_msg_reaction_chip, parentView, false);
        LinearLayout contentLayout = itemView.findViewById(R.id.contentLayout);
        LinearLayout iconsContainer = itemView.findViewById(com.chat.flagship.R.id.iconsContainer);
        TextView countTv = itemView.findViewById(R.id.countTv);
        bindReactionBubbleIcons(parentView.getContext(), iconsContainer, reactionCountMap);
        countTv.setText(String.format(Locale.getDefault(), "%d", getTotalReactionCount(reactionCountMap)));
        if (!currentUserEmojis.isEmpty()) {
            contentLayout.setBackgroundResource(com.chat.flagship.R.drawable.shape_flagship_reaction_selected);
        }
        contentLayout.setOnClickListener(v -> showReactionUsersDialog(parentView.getContext(), activeList, null));
        container.addView(itemView);
        parentView.addView(container);
    }

    public static List<WKMsgReaction> getActiveReactionList(List<WKMsgReaction> source) {
        List<WKMsgReaction> list = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return list;
        }
        for (WKMsgReaction reaction : source) {
            if (reaction != null && reaction.isDeleted == 0 && !TextUtils.isEmpty(reaction.emoji)) {
                list.add(reaction);
            }
        }
        return list;
    }

    public static void showReactionUsersDialog(Context context, List<WKMsgReaction> list, String selectedEmoji) {
        if (!(context instanceof Activity)) {
            return;
        }
        dismissReactionUsersDialog();
        Activity activity = (Activity) context;
        LinearLayout dialogRoot = new LinearLayout(activity);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setBackgroundResource(com.chat.flagship.R.drawable.shape_flagship_reaction_dialog_bg);
        int padding = dp(activity, 16);
        dialogRoot.setPadding(padding, padding, padding, padding);
        dialogRoot.setBackgroundColor(Color.WHITE);

        List<WKMsgReaction> activeList = getActiveReactionList(list);
        LinkedHashMap<String, Integer> reactionCountMap = sortReactionCountMap(countByEmoji(activeList));

        HorizontalScrollView tabScrollView = new HorizontalScrollView(activity);
        tabScrollView.setHorizontalScrollBarEnabled(false);
        tabScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout tabContainer = new LinearLayout(activity);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabScrollView.addView(tabContainer, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialogRoot.addView(tabScrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(activity);
        divider.setBackgroundColor(Color.parseColor("#EEF1F5"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        dividerParams.topMargin = dp(activity, 12);
        dividerParams.bottomMargin = dp(activity, 10);
        dialogRoot.addView(divider, dividerParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout listRoot = new LinearLayout(activity);
        listRoot.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listRoot, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialogRoot.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final String[] selectedEmojiHolder = new String[]{selectedEmoji};
        final int[] selectedIndexHolder = new int[]{TextUtils.isEmpty(selectedEmoji) ? 0 : findReactionTabIndex(reactionCountMap, selectedEmoji)};
        List<View> tabViews = new ArrayList<>();
        View allTabView = createReactionFilterTab(activity, null, getTotalReactionCount(reactionCountMap), true, 0, selectedEmojiHolder, selectedIndexHolder, tabViews, listRoot, activeList);
        tabContainer.addView(allTabView);
        tabViews.add(allTabView);
        int tabIndex = 1;
        for (Map.Entry<String, Integer> entry : reactionCountMap.entrySet()) {
            View tabView = createReactionFilterTab(activity, entry.getKey(), entry.getValue(), false, tabIndex, selectedEmojiHolder, selectedIndexHolder, tabViews, listRoot, activeList);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tabParams.setMarginStart(dp(activity, 8));
            tabView.setLayoutParams(tabParams);
            tabContainer.addView(tabView);
            tabViews.add(tabView);
            tabIndex++;
        }
        renderReactionUserList(activity, listRoot, activeList, selectedEmojiHolder[0]);

        BottomSheet.Builder builder = new BottomSheet.Builder(activity, false);
        builder.setApplyTopPadding(false);
        builder.setApplyBottomPadding(false);
        builder.setCustomView(dialogRoot);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setCanceledOnTouchOutside(true);
        bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        reactionUsersDialog = bottomSheet;
        dialogRoot.post(() -> {
            ViewGroup.LayoutParams params = scrollView.getLayoutParams();
            params.height = (int) (activity.getResources().getDisplayMetrics().heightPixels / 3f);
            scrollView.setLayoutParams(params);
        });
    }

    public static void dismissReactionUsersDialog() {
        if (reactionUsersDialog != null) {
            try {
                if (reactionUsersDialog.isShowing()) {
                    reactionUsersDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            reactionUsersDialog = null;
        }
    }

    public static String buildReactionUserName(WKMsgReaction reaction) {
        if (reaction == null) {
            return "";
        }
        String name = reaction.name;
        if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(reaction.uid)) {
            WKChannel channel = com.xinbida.wukongim.WKIM.getInstance().getChannelManager().getChannel(reaction.uid, WKChannelType.PERSONAL);
            if (channel != null) {
                name = TextUtils.isEmpty(channel.channelRemark) ? channel.channelName : channel.channelRemark;
            }
        }
        if (TextUtils.isEmpty(name)) {
            name = reaction.uid;
        }
        return name;
    }

    public static List<WKMsgReaction> buildMessageActiveReactions(List<FlagshipReactionSyncEntity> source, String messageId) {
        List<WKMsgReaction> list = new ArrayList<>();
        if (source == null || source.isEmpty() || TextUtils.isEmpty(messageId)) {
            return list;
        }
        LinkedHashMap<String, FlagshipReactionSyncEntity> latestByUid = new LinkedHashMap<>();
        for (FlagshipReactionSyncEntity entity : source) {
            if (entity == null || !TextUtils.equals(messageId, entity.messageId) || TextUtils.isEmpty(entity.uid)) {
                continue;
            }
            FlagshipReactionSyncEntity current = latestByUid.get(entity.uid);
            if (current == null || compareCreatedAt(current.createdAt, entity.createdAt) <= 0) {
                latestByUid.put(entity.uid, entity);
            }
        }
        for (FlagshipReactionSyncEntity entity : latestByUid.values()) {
            if (entity != null && entity.isDeleted == 0 && !TextUtils.isEmpty(entity.emoji)) {
                WKMsgReaction reaction = new WKMsgReaction();
                reaction.messageID = entity.messageId;
                reaction.channelID = entity.channelId;
                reaction.channelType = entity.channelType;
                reaction.uid = entity.uid;
                reaction.name = entity.name;
                reaction.seq = entity.seq;
                reaction.emoji = entity.emoji;
                reaction.isDeleted = entity.isDeleted;
                reaction.createdAt = entity.createdAt;
                list.add(reaction);
            }
        }
        return list;
    }

    private static ViewGroup.LayoutParams ensureParentParams(FrameLayout parentView, WKChatIteMsgFromType from) {
        ViewGroup parent = parentView.getParent() instanceof ViewGroup ? (ViewGroup) parentView.getParent() : null;
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams params;
            if (parentView.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                params = (LinearLayout.LayoutParams) parentView.getLayoutParams();
            } else {
                params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            params.gravity = from == WKChatIteMsgFromType.RECEIVED ? (Gravity.END | Gravity.BOTTOM) : (Gravity.START | Gravity.BOTTOM);
            params.topMargin = dp(parentView.getContext(), -10);
            return params;
        }
        FrameLayout.LayoutParams params;
        if (parentView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            params = (FrameLayout.LayoutParams) parentView.getLayoutParams();
        } else {
            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        params.gravity = from == WKChatIteMsgFromType.RECEIVED ? (Gravity.END | Gravity.BOTTOM) : (Gravity.START | Gravity.BOTTOM);
        params.topMargin = dp(parentView.getContext(), 2);
        return params;
    }

    private static LinkedHashMap<String, Integer> countByEmoji(List<WKMsgReaction> list) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (WKMsgReaction reaction : list) {
            if (reaction == null || reaction.isDeleted == 1 || TextUtils.isEmpty(reaction.emoji)) {
                continue;
            }
            Integer count = map.get(reaction.emoji);
            map.put(reaction.emoji, count == null ? 1 : count + 1);
        }
        return map;
    }

    private static LinkedHashMap<String, Integer> sortReactionCountMap(LinkedHashMap<String, Integer> source) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (ReactionSpec spec : DEFAULT_SPECS) {
            Integer count = source.get(spec.apiName);
            if (count == null) {
                count = source.get(spec.displayValue);
            }
            if (count != null && count > 0) {
                result.put(spec.apiName, count);
            }
        }
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Set<String> findCurrentUserEmojis(List<WKMsgReaction> list) {
        Set<String> set = new LinkedHashSet<>();
        String uid = WKConfig.getInstance().getUid();
        for (WKMsgReaction reaction : list) {
            if (reaction != null && TextUtils.equals(uid, reaction.uid) && reaction.isDeleted == 0 && !TextUtils.isEmpty(reaction.emoji)) {
                set.add(reaction.emoji);
            }
        }
        return set;
    }

    private static int compareCreatedAt(String oldValue, String newValue) {
        if (TextUtils.isEmpty(oldValue) && TextUtils.isEmpty(newValue)) {
            return 0;
        }
        if (TextUtils.isEmpty(oldValue)) {
            return -1;
        }
        if (TextUtils.isEmpty(newValue)) {
            return 1;
        }
        return oldValue.compareTo(newValue);
    }

    private static int dp(Context context, int value) {
        return (int) (context.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    public static String getDisplayValue(String reactionKey) {
        if (TextUtils.isEmpty(reactionKey)) {
            return "";
        }
        for (ReactionSpec spec : DEFAULT_SPECS) {
            if (TextUtils.equals(spec.apiName, reactionKey) || TextUtils.equals(spec.displayValue, reactionKey)) {
                return spec.displayValue;
            }
        }
        return reactionKey;
    }

    private static void bindReactionBubbleIcon(ImageView reactionIv, TextView reactionTv, String reactionKey) {
        int imageResId = getBubbleImageResId(reactionKey);
        if (imageResId != 0) {
            reactionIv.setImageResource(imageResId);
            reactionIv.setVisibility(View.VISIBLE);
            reactionTv.setVisibility(View.GONE);
            return;
        }
        reactionIv.setVisibility(View.GONE);
        reactionTv.setVisibility(View.VISIBLE);
        reactionTv.setText(getDisplayValue(reactionKey));
    }

    private static int getBubbleImageResId(String reactionKey) {
        if (TextUtils.isEmpty(reactionKey)) {
            return 0;
        }
        for (ReactionSpec spec : DEFAULT_SPECS) {
            if (TextUtils.equals(spec.apiName, reactionKey) || TextUtils.equals(spec.displayValue, reactionKey)) {
                return spec.bubbleImageResId;
            }
        }
        return 0;
    }

    private static void bindReactionBubbleIcons(Context context, LinearLayout container, LinkedHashMap<String, Integer> reactionCountMap) {
        container.removeAllViews();
        int index = 0;
        for (String reactionKey : reactionCountMap.keySet()) {
            View badgeView = createReactionBadgeView(context, reactionKey);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (index > 0) {
                params.setMarginStart(-dp(context, 8));
            }
            badgeView.setLayoutParams(params);
            container.addView(badgeView);
            index++;
            if (index >= 3) {
                break;
            }
        }
    }

    private static View createReactionBadgeView(Context context, String reactionKey) {
        FrameLayout badgeLayout = new FrameLayout(context);
        int badgeSize = dp(context, 24);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(badgeSize, badgeSize);
        badgeLayout.setLayoutParams(badgeParams);

        View bgView = new View(context);
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setShape(GradientDrawable.OVAL);
        bgDrawable.setColor(getBubbleBadgeColor(reactionKey));
        bgView.setBackground(bgDrawable);
        badgeLayout.addView(bgView, badgeParams);

        ImageView reactionIv = new ImageView(context);
        reactionIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(context, 19), dp(context, 19));
        iconParams.gravity = Gravity.CENTER;
        TextView reactionTv = new TextView(context);
        reactionTv.setGravity(Gravity.CENTER);
        reactionTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        reactionTv.setIncludeFontPadding(false);
        reactionTv.setTextColor(Color.WHITE);
        bindReactionBubbleIcon(reactionIv, reactionTv, reactionKey);
        if (reactionIv.getVisibility() == View.VISIBLE) {
            badgeLayout.addView(reactionIv, iconParams);
        } else {
            badgeLayout.addView(reactionTv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return badgeLayout;
    }

    private static int getBubbleBadgeColor(String reactionKey) {
        if (TextUtils.isEmpty(reactionKey)) {
            return Color.parseColor("#7F8DE8");
        }
        for (ReactionSpec spec : DEFAULT_SPECS) {
            if (TextUtils.equals(spec.apiName, reactionKey) || TextUtils.equals(spec.displayValue, reactionKey)) {
                return spec.bubbleBadgeColor;
            }
        }
        return Color.parseColor("#7F8DE8");
    }

    private static int getTotalReactionCount(LinkedHashMap<String, Integer> reactionCountMap) {
        int total = 0;
        for (Integer count : reactionCountMap.values()) {
            if (count != null && count > 0) {
                total += count;
            }
        }
        return total;
    }

    private static View createReactionFilterTab(Activity activity, String emoji, int count, boolean isAll, int tabIndex,
                                                String[] selectedEmojiHolder, int[] selectedIndexHolder, List<View> tabViews,
                                                LinearLayout listRoot, List<WKMsgReaction> activeList) {
        LinearLayout tabView = new LinearLayout(activity);
        tabView.setOrientation(LinearLayout.HORIZONTAL);
        tabView.setGravity(Gravity.CENTER_VERTICAL);
        tabView.setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7));
        tabView.setTag(emoji);
        boolean selected = isTabSelected(isAll, emoji, selectedEmojiHolder[0]);
        tabView.setBackground(buildReactionFilterTabBackground(selected));

        if (isAll) {
            TextView allTv = new TextView(activity);
            allTv.setText(String.format(Locale.getDefault(), "全部 %d", count));
            allTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            allTv.setTypeface(Typeface.DEFAULT_BOLD);
            allTv.setTextColor(getReactionFilterTabTextColor(selected));
            tabView.addView(allTv);
        } else {
            ImageView reactionIv = new ImageView(activity);
            reactionIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18));
            reactionIv.setLayoutParams(iconParams);
            TextView reactionTv = new TextView(activity);
            reactionTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            reactionTv.setIncludeFontPadding(false);
            bindReactionBubbleIcon(reactionIv, reactionTv, emoji);
            if (reactionIv.getVisibility() == View.VISIBLE) {
                tabView.addView(reactionIv);
            } else {
                reactionTv.setTextColor(getReactionFilterTabTextColor(selected));
                tabView.addView(reactionTv);
            }

            TextView countTv = new TextView(activity);
            countTv.setText(String.format(Locale.getDefault(), " %d", count));
            countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            countTv.setTypeface(Typeface.DEFAULT_BOLD);
            countTv.setTextColor(getReactionFilterTabTextColor(selected));
            countTv.setPadding(dp(activity, 4), 0, 0, 0);
            tabView.addView(countTv);
        }

        tabView.setOnClickListener(v -> {
            int oldIndex = selectedIndexHolder[0];
            selectedEmojiHolder[0] = isAll ? null : emoji;
            selectedIndexHolder[0] = tabIndex;
            updateReactionFilterTabs(tabViews, selectedEmojiHolder[0]);
            animateReactionUserListSwitch(activity, listRoot, activeList, selectedEmojiHolder[0], tabIndex >= oldIndex);
        });
        return tabView;
    }

    private static void updateReactionFilterTabs(List<View> tabViews, String selectedEmoji) {
        for (View tabView : tabViews) {
            Object tag = tabView.getTag();
            boolean isAll = tag == null;
            boolean selected = isTabSelected(isAll, tag instanceof String ? (String) tag : null, selectedEmoji);
            tabView.setBackground(buildReactionFilterTabBackground(selected));
            if (tabView instanceof LinearLayout) {
                updateReactionFilterTabChildren((LinearLayout) tabView, selected);
            }
        }
    }

    private static void updateReactionFilterTabChildren(LinearLayout tabView, boolean selected) {
        int textColor = getReactionFilterTabTextColor(selected);
        for (int i = 0; i < tabView.getChildCount(); i++) {
            View child = tabView.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(textColor);
            }
        }
    }

    private static GradientDrawable buildReactionFilterTabBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(1000);
        drawable.setColor(selected ? Color.parseColor("#E8F1FF") : Color.parseColor("#F4F6F9"));
        return drawable;
    }

    private static boolean isTabSelected(boolean isAll, String emoji, String selectedEmoji) {
        return isAll ? TextUtils.isEmpty(selectedEmoji) : TextUtils.equals(emoji, selectedEmoji);
    }

    private static int getReactionFilterTabTextColor(boolean selected) {
        return Color.parseColor(selected ? "#2F6FE4" : "#303133");
    }

    private static void animateReactionUserListSwitch(Activity activity, LinearLayout listRoot, List<WKMsgReaction> activeList,
                                                      String selectedEmoji, boolean moveFromRight) {
        float startTranslation = moveFromRight ? dp(activity, 18) : -dp(activity, 18);
        listRoot.animate().cancel();
        listRoot.animate()
                .translationX(-startTranslation * 0.35f)
                .alpha(0f)
                .setDuration(90)
                .withEndAction(() -> {
                    renderReactionUserList(activity, listRoot, activeList, selectedEmoji);
                    listRoot.setTranslationX(startTranslation);
                    listRoot.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(160)
                            .start();
                })
                .start();
    }

    private static int findReactionTabIndex(LinkedHashMap<String, Integer> reactionCountMap, String selectedEmoji) {
        if (TextUtils.isEmpty(selectedEmoji)) {
            return 0;
        }
        int index = 1;
        for (String reactionKey : reactionCountMap.keySet()) {
            if (TextUtils.equals(reactionKey, selectedEmoji)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    private static void renderReactionUserList(Activity activity, LinearLayout root, List<WKMsgReaction> activeList, String selectedEmoji) {
        root.removeAllViews();
        List<WKMsgReaction> showList = new ArrayList<>();
        for (WKMsgReaction reaction : activeList) {
            if (TextUtils.isEmpty(selectedEmoji) || TextUtils.equals(selectedEmoji, reaction.emoji)) {
                showList.add(reaction);
            }
        }
        if (showList.isEmpty()) {
            TextView emptyView = new TextView(activity);
            emptyView.setText(com.chat.flagship.R.string.flagship_reaction_list_empty);
            emptyView.setTextColor(Color.parseColor("#666666"));
            emptyView.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
            root.addView(emptyView);
            return;
        }
        for (WKMsgReaction reaction : showList) {
            View itemView = LayoutInflater.from(activity).inflate(com.chat.flagship.R.layout.item_flagship_msg_reaction_user, root, false);
            AvatarView avatarView = itemView.findViewById(R.id.avatarView);
            TextView nameTv = itemView.findViewById(R.id.nameTv);
            ImageView reactionIv = itemView.findViewById(com.chat.flagship.R.id.reactionIv);
            TextView reactionTv = itemView.findViewById(com.chat.flagship.R.id.reactionTv);
            avatarView.showAvatar(reaction.uid, WKChannelType.PERSONAL);
            nameTv.setText(buildReactionUserName(reaction));
            if (TextUtils.isEmpty(selectedEmoji)) {
                bindReactionBubbleIcon(reactionIv, reactionTv, reaction.emoji);
            } else {
                reactionIv.setVisibility(View.GONE);
                reactionTv.setVisibility(View.GONE);
            }
            root.addView(itemView);
        }
    }

    private static List<ReactionSpec> buildDefaultSpecs() {
        List<ReactionSpec> list = new ArrayList<>();
        // 第一列是接口值，第二列是界面显示值。后端若有精确枚举差异，只改这里即可。
        list.add(new ReactionSpec("Laugh", "\uD83D\uDE01", com.chat.flagship.R.raw.flagship_reaction_01, com.chat.flagship.R.drawable.flagship_reaction_01, Color.parseColor("#7B89E9")));
        list.add(new ReactionSpec("Admire", "\uD83E\uDD29", com.chat.flagship.R.raw.flagship_reaction_02, com.chat.flagship.R.drawable.flagship_reaction_02, Color.parseColor("#C867F0")));
        list.add(new ReactionSpec("Heart", "\u2764\uFE0F", com.chat.flagship.R.raw.flagship_reaction_03, com.chat.flagship.R.drawable.flagship_reaction_03, Color.parseColor("#C867F0")));
        list.add(new ReactionSpec("ThumbsUp", "\uD83D\uDC4D", com.chat.flagship.R.raw.flagship_reaction_04, com.chat.flagship.R.drawable.flagship_reaction_04, Color.parseColor("#7B89E9")));
        list.add(new ReactionSpec("ThumbsDown", "\uD83D\uDC4E", com.chat.flagship.R.raw.flagship_reaction_05, com.chat.flagship.R.drawable.flagship_reaction_05, Color.parseColor("#7B89E9")));
        list.add(new ReactionSpec("Vomit", "\uD83E\uDD2E", com.chat.flagship.R.raw.flagship_reaction_06, com.chat.flagship.R.drawable.flagship_reaction_06, Color.parseColor("#76BB89")));
        list.add(new ReactionSpec("Fire", "\uD83D\uDD25", com.chat.flagship.R.raw.flagship_reaction_07, com.chat.flagship.R.drawable.flagship_reaction_07, Color.parseColor("#FF8A5A")));
        list.add(new ReactionSpec("PartyPopper", "\uD83C\uDF89", com.chat.flagship.R.raw.flagship_reaction_08, com.chat.flagship.R.drawable.flagship_reaction_08, Color.parseColor("#FF8EC5")));
        list.add(new ReactionSpec("TearSmile", "\uD83E\uDD72", com.chat.flagship.R.raw.flagship_reaction_09, com.chat.flagship.R.drawable.flagship_reaction_09, Color.parseColor("#7B89E9")));
        list.add(new ReactionSpec("Shock", "\uD83D\uDE31", com.chat.flagship.R.raw.flagship_reaction_10, com.chat.flagship.R.drawable.flagship_reaction_10, Color.parseColor("#F5A25D")));
        list.add(new ReactionSpec("Poop", "\uD83D\uDCA9", com.chat.flagship.R.raw.flagship_reaction_11, com.chat.flagship.R.drawable.flagship_reaction_11, Color.parseColor("#A07B5E")));
        return list;
    }

    private static final class ReactionSpec {
        final String apiName;
        final String displayValue;
        final int resourceId;
        final int bubbleImageResId;
        final int bubbleBadgeColor;

        ReactionSpec(String apiName, String displayValue, int resourceId, int bubbleImageResId, int bubbleBadgeColor) {
            this.apiName = apiName;
            this.displayValue = displayValue;
            this.resourceId = resourceId;
            this.bubbleImageResId = bubbleImageResId;
            this.bubbleBadgeColor = bubbleBadgeColor;
        }
    }
}
