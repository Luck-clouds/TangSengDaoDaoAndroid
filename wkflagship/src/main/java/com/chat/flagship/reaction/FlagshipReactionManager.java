package com.chat.flagship.reaction;

/**
 * 消息回应 UI 与资源管理
 * Created by Luckclouds .
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

    private static AlertDialog reactionUsersDialog;

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
        for (Map.Entry<String, Integer> entry : sortReactionCountMap(countByEmoji(activeList)).entrySet()) {
            View itemView = LayoutInflater.from(parentView.getContext()).inflate(com.chat.flagship.R.layout.item_flagship_msg_reaction_chip, parentView, false);
            LinearLayout contentLayout = itemView.findViewById(R.id.contentLayout);
            TextView reactionTv = itemView.findViewById(com.chat.flagship.R.id.reactionTv);
            TextView countTv = itemView.findViewById(R.id.countTv);
            String reactionKey = entry.getKey();
            reactionTv.setText(getDisplayValue(reactionKey));
            countTv.setText(String.format(Locale.getDefault(), "%d", entry.getValue()));
            if (currentUserEmojis.contains(reactionKey)) {
                contentLayout.setBackgroundResource(com.chat.flagship.R.drawable.shape_flagship_reaction_selected);
                countTv.setTextColor(Color.parseColor("#2F6FE4"));
            } else {
                countTv.setTextColor(Color.parseColor("#303133"));
            }
            contentLayout.setOnClickListener(v -> showReactionUsersDialog(parentView.getContext(), activeList, reactionKey));
            container.addView(itemView);
        }
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
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundResource(com.chat.flagship.R.drawable.shape_flagship_reaction_dialog_bg);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 16);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.WHITE);
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(activity);
        titleView.setText(com.chat.flagship.R.string.flagship_reaction_list_title);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTextColor(Color.parseColor("#222222"));
        titleView.setPadding(0, 0, 0, dp(activity, 12));
        root.addView(titleView);

        List<WKMsgReaction> showList = new ArrayList<>();
        for (WKMsgReaction reaction : getActiveReactionList(list)) {
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
        } else {
            for (WKMsgReaction reaction : showList) {
                View itemView = LayoutInflater.from(activity).inflate(com.chat.flagship.R.layout.item_flagship_msg_reaction_user, root, false);
                AvatarView avatarView = itemView.findViewById(R.id.avatarView);
                TextView nameTv = itemView.findViewById(R.id.nameTv);
                TextView reactionTv = itemView.findViewById(com.chat.flagship.R.id.reactionTv);
                avatarView.showAvatar(reaction.uid, WKChannelType.PERSONAL);
                nameTv.setText(buildReactionUserName(reaction));
                reactionTv.setText(getDisplayValue(reaction.emoji));
                root.addView(itemView);
            }
        }
        reactionUsersDialog = new AlertDialog.Builder(activity, R.style.AlertDialog)
                .setView(scrollView)
                .create();
        reactionUsersDialog.setCanceledOnTouchOutside(true);
        reactionUsersDialog.show();
        if (reactionUsersDialog.getWindow() != null) {
            reactionUsersDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            reactionUsersDialog.getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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
            params.topMargin = dp(parentView.getContext(), 2);
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

    private static List<ReactionSpec> buildDefaultSpecs() {
        List<ReactionSpec> list = new ArrayList<>();
        // 第一列是接口值，第二列是界面显示值。后端若有精确枚举差异，只改这里即可。
        list.add(new ReactionSpec("Laugh", "\uD83D\uDE01", com.chat.flagship.R.raw.flagship_reaction_01));
        list.add(new ReactionSpec("Admire", "\uD83E\uDD29", com.chat.flagship.R.raw.flagship_reaction_02));
        list.add(new ReactionSpec("Heart", "\u2764\uFE0F", com.chat.flagship.R.raw.flagship_reaction_03));
        list.add(new ReactionSpec("ThumbsUp", "\uD83D\uDC4D", com.chat.flagship.R.raw.flagship_reaction_04));
        list.add(new ReactionSpec("ThumbsDown", "\uD83D\uDC4E", com.chat.flagship.R.raw.flagship_reaction_05));
        list.add(new ReactionSpec("Vomit", "\uD83E\uDD2E", com.chat.flagship.R.raw.flagship_reaction_06));
        list.add(new ReactionSpec("Fire", "\uD83D\uDD25", com.chat.flagship.R.raw.flagship_reaction_07));
        list.add(new ReactionSpec("PartyPopper", "\uD83C\uDF89", com.chat.flagship.R.raw.flagship_reaction_08));
        list.add(new ReactionSpec("TearSmile", "\uD83E\uDD72", com.chat.flagship.R.raw.flagship_reaction_09));
        list.add(new ReactionSpec("Shock", "\uD83D\uDE31", com.chat.flagship.R.raw.flagship_reaction_10));
        list.add(new ReactionSpec("Poop", "\uD83D\uDCA9", com.chat.flagship.R.raw.flagship_reaction_11));
        return list;
    }

    private static final class ReactionSpec {
        final String apiName;
        final String displayValue;
        final int resourceId;

        ReactionSpec(String apiName, String displayValue, int resourceId) {
            this.apiName = apiName;
            this.displayValue = displayValue;
            this.resourceId = resourceId;
        }
    }
}
