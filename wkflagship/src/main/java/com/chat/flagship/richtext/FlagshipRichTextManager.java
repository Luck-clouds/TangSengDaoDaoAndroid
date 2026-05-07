package com.chat.flagship.richtext;

/**
 * 富文本编辑入口管理
 * Created by Luckclouds .
 */

import android.content.Context;
import android.content.Intent;

import com.chat.base.msg.IConversationContext;

import java.lang.ref.WeakReference;

public class FlagshipRichTextManager {
    private WeakReference<IConversationContext> conversationContextRef;

    private FlagshipRichTextManager() {
    }

    private static class Binder {
        private static final FlagshipRichTextManager INSTANCE = new FlagshipRichTextManager();
    }

    public static FlagshipRichTextManager getInstance() {
        return Binder.INSTANCE;
    }

    public void open(IConversationContext conversationContext) {
        if (conversationContext == null || conversationContext.getChatActivity() == null) {
            return;
        }
        conversationContextRef = new WeakReference<>(conversationContext);
        Context context = conversationContext.getChatActivity();
        Intent intent = new Intent(context, FlagshipRichTextEditorActivity.class);
        context.startActivity(intent);
    }

    public IConversationContext getConversationContext() {
        return conversationContextRef == null ? null : conversationContextRef.get();
    }
}
