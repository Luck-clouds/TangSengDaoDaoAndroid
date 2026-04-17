package com.chat.video;

/**
 * 小视频模块入口
 * Created by Luckclouds.
 */

import android.Manifest;
import android.app.Application;
import android.content.res.Configuration;

import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatFunctionMenu;
import com.chat.base.endpoint.entity.ChatToolBarMenu;
import com.chat.base.endpoint.entity.MsgConfig;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.utils.WKPermissions;
import com.chat.video.provider.WKVideoProvider;
import com.chat.video.session.VideoSendSession;

import java.lang.ref.WeakReference;

public class WKVideoApplication {
    private WeakReference<Application> applicationRef;

    private WKVideoApplication() {
    }

    private static class Binder {
        private static final WKVideoApplication INSTANCE = new WKVideoApplication();
    }

    public static WKVideoApplication getInstance() {
        return Binder.INSTANCE;
    }

    public void init(Application application) {
        applicationRef = new WeakReference<>(application);
        // 注册视频消息气泡，让聊天页真正认识 WK_VIDEO。
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_VIDEO, new WKVideoProvider());
        // 视频消息继续复用通用消息菜单能力，因此这里保持 MsgConfig 开启。
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.WK_VIDEO, object -> new MsgConfig(true));
        // 打开原项目相册选择视频的预留开关。
        EndpointManager.getInstance().setMethod("is_register_video", object -> true);
        // 聊天工具栏入口。
        EndpointManager.getInstance().setMethod(
                EndpointCategory.wkChatToolBar + "_video_capture",
                EndpointCategory.wkChatToolBar,
                98,
                object -> new ChatToolBarMenu(
                        "wk_chat_toolbar_video_capture",
                        R.mipmap.video_toolbar_capture,
                        R.mipmap.video_toolbar_capture,
                        null,
                        (isSelected, conversationContext) -> openCapture(conversationContext)
                )
        );
        // 功能面板入口。
        EndpointManager.getInstance().setMethod(
                EndpointCategory.chatFunction + "_video_capture",
                EndpointCategory.chatFunction,
                97,
                object -> new ChatFunctionMenu(
                        "video_capture",
                        getFunctionIcon(),
                        application.getString(R.string.video_capture),
                        this::openCapture
                )
        );
    }

    public Application getApplication() {
        return applicationRef == null ? null : applicationRef.get();
    }

    private int getFunctionIcon() {
        Application application = getApplication();
        if (application == null) {
            return R.drawable.video_func_capture_light;
        }
        int mode = application.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES
                ? R.drawable.video_func_capture_dark
                : R.drawable.video_func_capture_light;
    }

    private void openCapture(IConversationContext conversationContext) {
        if (conversationContext == null || conversationContext.getChatActivity() == null) return;
        Application application = getApplication();
        if (application == null) return;
        CharSequence appName = application.getApplicationInfo().loadLabel(application.getPackageManager());
        String desc = application.getString(R.string.video_capture_permission_desc, appName);
        // 小视频默认会尝试录制有声视频，所以这里一次性申请相机和麦克风。
        WKPermissions.getInstance().checkPermissions(new WKPermissions.IPermissionResult() {
            @Override
            public void onResult(boolean result) {
                if (result) {
                    VideoSendSession.INSTANCE.openCapture(conversationContext);
                }
            }

            @Override
            public void clickResult(boolean isCancel) {
            }
        }, conversationContext.getChatActivity(), desc, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);
    }
}
