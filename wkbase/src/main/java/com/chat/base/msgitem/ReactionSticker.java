package com.chat.base.msgitem;

public class ReactionSticker {
    public String name;
    public String displayName;
    public int resourceID;

    public ReactionSticker(String name, int resourceID) {
        this(name, name, resourceID);
    }

    public ReactionSticker(String name, String displayName, int resourceID) {
        this.name = name;
        this.displayName = displayName;
        this.resourceID = resourceID;
    }
}
