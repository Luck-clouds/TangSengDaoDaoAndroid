package com.chat.uikit.contacts.label;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.entity.WKChannel;

import java.util.ArrayList;
import java.util.List;

public class LabelEntity implements Parcelable {
    public String id;
    public String name;
    public int sortNo;
    public int count;
    public long version;
    public int isDeleted;
    public String createdAt;
    public String updatedAt;
    public final List<WKChannel> members = new ArrayList<>();

    public LabelEntity() {
    }

    protected LabelEntity(Parcel in) {
        id = in.readString();
        name = in.readString();
        sortNo = in.readInt();
        count = in.readInt();
        version = in.readLong();
        isDeleted = in.readInt();
        createdAt = in.readString();
        updatedAt = in.readString();
        in.readTypedList(members, WKChannel.CREATOR);
    }

    public static final Creator<LabelEntity> CREATOR = new Creator<>() {
        @Override
        public LabelEntity createFromParcel(Parcel in) {
            return new LabelEntity(in);
        }

        @Override
        public LabelEntity[] newArray(int size) {
            return new LabelEntity[size];
        }
    };

    public int getMemberCount() {
        if (!members.isEmpty()) {
            return members.size();
        }
        return Math.max(count, 0);
    }

    public void syncCount() {
        count = members.size();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeInt(sortNo);
        dest.writeInt(count);
        dest.writeLong(version);
        dest.writeInt(isDeleted);
        dest.writeString(createdAt);
        dest.writeString(updatedAt);
        dest.writeTypedList(members);
    }
}
