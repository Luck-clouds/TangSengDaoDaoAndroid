package com.chat.uikit.contacts.label;

import com.xinbida.wukongim.entity.WKChannel;

public class LabelMemberItem {
    public static final int TYPE_MEMBER = 0;
    public static final int TYPE_ADD = 1;
    public static final int TYPE_REMOVE = 2;

    public int itemType;
    public WKChannel channel;

    public static LabelMemberItem member(WKChannel channel) {
        LabelMemberItem item = new LabelMemberItem();
        item.itemType = TYPE_MEMBER;
        item.channel = channel;
        return item;
    }

    public static LabelMemberItem addAction() {
        LabelMemberItem item = new LabelMemberItem();
        item.itemType = TYPE_ADD;
        return item;
    }

    public static LabelMemberItem removeAction() {
        LabelMemberItem item = new LabelMemberItem();
        item.itemType = TYPE_REMOVE;
        return item;
    }
}
