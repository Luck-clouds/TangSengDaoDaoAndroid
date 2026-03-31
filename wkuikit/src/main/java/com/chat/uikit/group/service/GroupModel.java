package com.chat.uikit.group.service;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.group.GroupEntity;
import com.chat.uikit.group.service.entity.GroupForbiddenTime;
import com.chat.uikit.group.service.entity.GroupMember;
import com.chat.uikit.group.service.entity.GroupQr;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelMemberExtras;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.interfaces.IChannelMemberListResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2019-11-30 10:25
 * 群相关处理
 */
public class GroupModel extends WKBaseModel {
    private final Map<String, HashMap<String, WKChannelMember>> allGroupMemberCache = new HashMap<>();

    private GroupModel() {
    }

    private static class GroupModelBinder {
        private final static GroupModel groupModel = new GroupModel();
    }

    public static GroupModel getInstance() {
        return GroupModelBinder.groupModel;
    }

    /**
     * 创建群组
     *
     * @param name 群名
     * @param ids  成员
     */
    public void createGroup(String name, List<String> ids, List<String> names, final IGroupInfo iGroupInfo) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", name);
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(ids);
        jsonObject.put("members", jsonArray);
        JSONArray jsonArray1 = new JSONArray();
        jsonArray1.addAll(names);
        jsonObject.put("member_names", jsonArray1);
        jsonObject.put("msg_auto_delete", WKConfig.getInstance().getUserInfo().msg_expire_second);
        request(createService(GroupService.class).createGroup(jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(GroupEntity groupEntity) {
                WKChannel channel = new WKChannel();
                channel.channelID = groupEntity.group_no;
                channel.channelType = WKChannelType.GROUP;
                channel.channelName = groupEntity.name;
                WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
                iGroupInfo.onResult(HttpResponseCode.success, "", groupEntity);
            }

            @Override
            public void onFail(int code, String msg) {
                iGroupInfo.onResult(code, msg, null);
            }
        });
    }

    public interface IGroupInfo {
        void onResult(int code, String msg, GroupEntity groupEntity);
    }

    /**
     * 添加群成员
     *
     * @param groupNo 群号
     * @param ids     成员
     */
    public void addGroupMembers(String groupNo, List<String> ids, List<String> names, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(ids);
        jsonObject.put("members", jsonArray);
        JSONArray nameArr = new JSONArray();
        nameArr.addAll(names);
        jsonObject.put("names", nameArr);
        request(createService(GroupService.class).addGroupMembers(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 邀请加入群聊
     *
     * @param groupNo         群编号
     * @param ids             用户id
     * @param iCommonListener 返回
     */
    public void inviteGroupMembers(String groupNo, List<String> ids, final ICommonListener iCommonListener) {
        JSONObject jsonObject1 = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(ids);
        jsonObject1.put("uids", jsonArray);
        jsonObject1.put("remark", "");
        request(createService(GroupService.class).inviteGroupMembers(groupNo, jsonObject1), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }


    /**
     * 获取群详情
     *
     * @param groupNo     群编号
     * @param iGetChannel 返回
     */
    public void getGroupInfo(String groupNo, final WKCommonModel.IGetChannel iGetChannel) {
        WKCommonModel.getInstance().getChannel(groupNo, WKChannelType.GROUP, (code, msg, entity) -> {
            if (iGetChannel != null) {
                iGetChannel.onResult(code, msg, entity);
            }
        });
    }


    public void getChannelMembers(String groupNO, String keyword, int page, int limit, IChannelMemberListResult iChannelMemberListResult) {
        request(createService(GroupService.class).groupMembers(groupNO, keyword, page, limit), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<GroupMember> result) {
                List<WKChannelMember> list = serialize(result);
                iChannelMemberListResult.onResult(list);
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    /**
     * 同步群成员
     *
     * @param groupNo 群编号
     */
    public synchronized void groupMembersSync(String groupNo, final ICommonListener iCommonListener) {
        long version = WKIM.getInstance().getChannelMembersManager().getMaxVersion(groupNo, WKChannelType.GROUP);
        groupMembersSync(groupNo, version, iCommonListener);
    }

    public synchronized void groupMembersFullSync(String groupNo, final ICommonListener iCommonListener) {
        groupMembersSync(groupNo, 0, iCommonListener);
    }

    private synchronized void groupMembersSync(String groupNo, long version, final ICommonListener iCommonListener) {
        request(createService(GroupService.class).syncGroupMembers(groupNo, 1000, version), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<GroupMember> list) {
                if (WKReader.isNotEmpty(list)) {
                    List<WKChannelMember> members = serialize(list);
                    WKIM.getInstance().getChannelMembersManager().save(members);
                    mergeGroupMembers(groupNo, members);
                    AndroidUtilities.runOnUIThread(() -> groupMembersSync(groupNo, iCommonListener), 500);
                } else {
                    if (iCommonListener != null)
                        iCommonListener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });

    }

    public synchronized List<WKChannelMember> getAllGroupMembers(String groupNo) {
        HashMap<String, WKChannelMember> memberMap = allGroupMemberCache.get(groupNo);
        if (memberMap == null) {
            memberMap = new HashMap<>();
            allGroupMemberCache.put(groupNo, memberMap);
        }
        List<WKChannelMember> localMembers = WKIM.getInstance().getChannelMembersManager().getMembers(groupNo, WKChannelType.GROUP);
        if (WKReader.isNotEmpty(localMembers)) {
            for (WKChannelMember member : localMembers) {
                if (member != null && !TextUtils.isEmpty(member.memberUID)) {
                    memberMap.put(member.memberUID, member);
                }
            }
        }
        return new ArrayList<>(memberMap.values());
    }

    private synchronized void mergeGroupMembers(String groupNo, List<WKChannelMember> members) {
        if (TextUtils.isEmpty(groupNo) || WKReader.isEmpty(members)) {
            return;
        }
        HashMap<String, WKChannelMember> memberMap = allGroupMemberCache.get(groupNo);
        if (memberMap == null) {
            memberMap = new HashMap<>();
            allGroupMemberCache.put(groupNo, memberMap);
        }
        for (WKChannelMember member : members) {
            if (member != null && !TextUtils.isEmpty(member.memberUID)) {
                memberMap.put(member.memberUID, member);
            }
        }
    }

    private String findInviteNo(org.json.JSONObject jsonObject) {
        if (jsonObject == null) {
            return "";
        }
        String inviteNo = jsonObject.optString("invite_no");
        if (!TextUtils.isEmpty(inviteNo)) {
            return inviteNo;
        }
        Object contentObject = jsonObject.opt("content");
        if (contentObject instanceof org.json.JSONObject) {
            inviteNo = findInviteNo((org.json.JSONObject) contentObject);
            if (!TextUtils.isEmpty(inviteNo)) {
                return inviteNo;
            }
        }
        Object paramObject = jsonObject.opt("param");
        if (paramObject instanceof org.json.JSONObject) {
            inviteNo = findInviteNo((org.json.JSONObject) paramObject);
            if (!TextUtils.isEmpty(inviteNo)) {
                return inviteNo;
            }
        }
        Object dataObject = jsonObject.opt("data");
        if (dataObject instanceof org.json.JSONObject) {
            inviteNo = findInviteNo((org.json.JSONObject) dataObject);
            if (!TextUtils.isEmpty(inviteNo)) {
                return inviteNo;
            }
        }
        return "";
    }

    private String findInviteNo(Map<?, ?> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        Object inviteNo = data.get("invite_no");
        if (inviteNo instanceof String && !TextUtils.isEmpty((String) inviteNo)) {
            return (String) inviteNo;
        }
        for (Object value : data.values()) {
            if (value instanceof Map) {
                String nestedInviteNo = findInviteNo((Map<?, ?>) value);
                if (!TextUtils.isEmpty(nestedInviteNo)) {
                    return nestedInviteNo;
                }
            }
        }
        return "";
    }

    private List<WKChannelMember> serialize(List<GroupMember> list) {
        List<WKChannelMember> members = new ArrayList<>();
        if (WKReader.isEmpty(list)) {
            return members;
        }
        for (int i = 0, size = list.size(); i < size; i++) {
            WKChannelMember member = new WKChannelMember();
            member.memberUID = list.get(i).uid;
            member.memberRemark = list.get(i).remark;
            member.memberName = list.get(i).name;
            member.channelID = list.get(i).group_no;
            member.channelType = WKChannelType.GROUP;
            member.isDeleted = list.get(i).is_deleted;
            member.version = list.get(i).version;
            member.role = list.get(i).role;
            member.status = list.get(i).status;
            member.memberInviteUID = list.get(i).invite_uid;
            member.robot = list.get(i).robot;
            member.forbiddenExpirationTime = list.get(i).forbidden_expir_time;
            if (member.robot == 1 && !TextUtils.isEmpty(list.get(i).username)) {
                member.memberName = list.get(i).username;
            }
            member.updatedAt = list.get(i).updated_at;
            member.createdAt = list.get(i).created_at;
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put(WKChannelMemberExtras.WKCode, list.get(i).vercode);
            member.extraMap = hashMap;
            members.add(member);
        }
        return members;
    }

    /**
     * 修改群设置
     *
     * @param groupNo         群编号
     * @param key             修改字段
     * @param value           修改值
     * @param iCommonListener 返回
     */
    public void updateGroupSetting(String groupNo, String key, int value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(GroupService.class).updateGroupSetting(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void updateGroupSetting(String groupNo, String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(GroupService.class).updateGroupSetting(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 修改群信息
     *
     * @param groupNo         群编号
     * @param key             修改字段
     * @param value           修改值
     * @param iCommonListener 返回
     */
    public void updateGroupInfo(String groupNo, String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(GroupService.class).updateGroupInfo(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 删除群成员
     *
     * @param groupNo         群编号
     * @param uidList         用户ID
     * @param iCommonListener 返回
     */
    public void deleteGroupMembers(String groupNo, List<String> uidList, List<String> names, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(uidList);
        jsonObject.put("members", jsonArray);
        JSONArray nameArr = new JSONArray();
        nameArr.addAll(names);
        jsonObject.put("names", nameArr);
        request(createService(GroupService.class).deleteGroupMembers(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                List<WKChannelMember> list = new ArrayList<>();
                for (int i = 0, size = uidList.size(); i < size; i++) {
                    WKChannelMember member = new WKChannelMember();
                    member.isDeleted = 1;
                    member.channelID = groupNo;
                    member.channelType = WKChannelType.GROUP;
                    member.memberUID = uidList.get(i);
                    list.add(member);
                }
                WKIM.getInstance().getChannelMembersManager().delete(list);
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 修改群成员信息
     *
     * @param groupNo         群号
     * @param uid             用户ID
     * @param key             主键
     * @param value           修改值
     * @param iCommonListener 返回
     */
    public void updateGroupMemberInfo(String groupNo, String uid, String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put(key, value);
        request(createService(GroupService.class).updateGroupMemberInfo(groupNo, uid, jsonObject1), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (key.equalsIgnoreCase("remark")) {
                    //sdk层数据库修改
                    WKIM.getInstance().getChannelMembersManager().updateRemarkName(groupNo, WKChannelType.GROUP, uid, value);
                }
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public interface IGetH5ConfirmUrl {
        void onResult(int code, String msg, String url);
    }

    public void getH5ConfirmUrl(String groupNo, String inviteNo, final IGetH5ConfirmUrl iGetH5ConfirmUrl) {
        if (TextUtils.isEmpty(groupNo) || TextUtils.isEmpty(inviteNo)) {
            if (iGetH5ConfirmUrl != null) {
                iGetH5ConfirmUrl.onResult(HttpResponseCode.error, "invite_no is empty", null);
            }
            return;
        }
        request(createService(GroupService.class).getH5confirmUrl(groupNo, inviteNo), new IRequestResultListener<>() {
            @Override
            public void onSuccess(com.chat.uikit.group.service.entity.H5ConfirmUrl result) {
                if (iGetH5ConfirmUrl != null) {
                    iGetH5ConfirmUrl.onResult(HttpResponseCode.success, "", result == null ? null : result.url);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iGetH5ConfirmUrl != null) {
                    iGetH5ConfirmUrl.onResult(code, msg, null);
                }
            }
        });
    }

    public String getInviteNoFromMessageContent(String contentJson) {
        if (TextUtils.isEmpty(contentJson)) {
            return "";
        }
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(contentJson);
            return findInviteNo(jsonObject);
        } catch (Exception ignored) {
        }
        return "";
    }

    public String getInviteNoFromMap(Map<?, ?> data) {
        return findInviteNo(data);
    }

    public void updateGroupMemberInfo(String groupNo, String uid, String key, long value, final ICommonListener iCommonListener) {
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put(key, value);
        request(createService(GroupService.class).updateGroupMemberInfo(groupNo, uid, jsonObject1), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void getForbiddenTimes(final IGetForbiddenTimes iGetForbiddenTimes) {
        request(createService(GroupService.class).getForbiddenTimes(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<GroupForbiddenTime> result) {
                if (iGetForbiddenTimes != null) {
                    iGetForbiddenTimes.onResult(HttpResponseCode.success, "", result);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iGetForbiddenTimes != null) {
                    iGetForbiddenTimes.onResult(code, msg, null);
                }
            }
        });
    }

    public void updateMemberForbidden(String groupNo, String uid, int action, int key, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("member_uid", uid);
        jsonObject.put("action", action);
        jsonObject.put("key", key);
        request(createService(GroupService.class).updateMemberForbidden(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void addGroupManagers(String groupNo, List<String> uidList, final ICommonListener iCommonListener) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(uidList);
        request(createService(GroupService.class).addGroupManagers(groupNo, jsonArray), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void removeGroupManagers(String groupNo, List<String> uidList, final ICommonListener iCommonListener) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(uidList);
        request(createService(GroupService.class).removeGroupManagers(groupNo, jsonArray), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void addBlacklist(String groupNo, List<String> uidList, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(uidList);
        jsonObject.put("uids", jsonArray);
        request(createService(GroupService.class).addBlacklist(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void removeBlacklist(String groupNo, List<String> uidList, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(uidList);
        jsonObject.put("uids", jsonArray);
        request(createService(GroupService.class).removeBlacklist(groupNo, jsonObject), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void transferOwner(String groupNo, String uid, final ICommonListener iCommonListener) {
        request(createService(GroupService.class).transferOwner(groupNo, uid), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

    public void uploadGroupAvatar(String groupNo, String filePath, final IUploadBack iUploadBack) {
        String url = WKApiConfig.getGroupUrl(groupNo) + "?uuid=" + WKTimeUtils.getInstance().getCurrentMills();
        WKUploader.getInstance().upload(url, filePath, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String url) {
                iUploadBack.onResult(HttpResponseCode.success);
            }

            @Override
            public void onError() {
                iUploadBack.onResult(HttpResponseCode.error);
            }
        });
    }

    public interface IUploadBack {
        void onResult(int code);
    }

    public interface IGetForbiddenTimes {
        void onResult(int code, String msg, List<GroupForbiddenTime> list);
    }

    /**
     * 群二维码
     *
     * @param groupID  群号
     * @param iGroupQr 返回
     */
    void getGroupQr(String groupID, final IGroupQr iGroupQr) {
        request(createService(GroupService.class).getGroupQr(groupID), new IRequestResultListener<>() {
            @Override
            public void onSuccess(GroupQr result) {
                iGroupQr.onResult(HttpResponseCode.success, "", result.day, result.qrcode, result.expire);
            }

            @Override
            public void onFail(int code, String msg) {
                iGroupQr.onResult(code, msg, 0, "", "");
            }
        });

    }

    public interface IGroupQr {
        void onResult(int code, String msg, int day, String qrCode, String expire);
    }

    /**
     * 我保存的群聊
     *
     * @param iGetMyGroups 返回
     */
    void getMyGroups(final IGetMyGroups iGetMyGroups) {
        request(createService(GroupService.class).getMyGroups(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<GroupEntity> result) {
                iGetMyGroups.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iGetMyGroups.onResult(code, msg, null);
            }
        });
    }

    public interface IGetMyGroups {
        void onResult(int code, String msg, List<GroupEntity> list);
    }

    public void exitGroup(String groupNo, final ICommonListener iCommonListener) {
        request(createService(GroupService.class).exitGroup(groupNo), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                iCommonListener.onResult(HttpResponseCode.success, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                iCommonListener.onResult(code, msg);
            }
        });
    }

}
