package com.chat.uikit.chat.file;

public class RecentFileEntity {
    public String name;
    public long size;
    public long modifiedAt;
    public String path;
    public String uriString;

    public String getIdentity() {
        if (path != null && !path.isEmpty()) {
            return path;
        }
        return uriString == null ? "" : uriString;
    }
}
