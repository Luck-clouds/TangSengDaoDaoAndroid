package com.chat.base.endpoint.entity;

import androidx.annotation.Nullable;

import com.chat.base.msg.IConversationContext;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.List;

/**
 * 多选双向删除共享参数。
 * wkuikit 只负责收集选中消息和 UI 回调，真正的删除逻辑由旗舰模块处理。
 */
public class BatchMutualDeleteMenu {
    private final List<WKMsg> messages;
    private final IConversationContext conversationContext;
    private final IResult result;

    public BatchMutualDeleteMenu(List<WKMsg> messages,
                                 IConversationContext conversationContext,
                                 @Nullable IResult result) {
        this.messages = messages;
        this.conversationContext = conversationContext;
        this.result = result;
    }

    public List<WKMsg> getMessages() {
        return messages;
    }

    public IConversationContext getConversationContext() {
        return conversationContext;
    }

    @Nullable
    public IResult getResult() {
        return result;
    }

    public interface IResult {
        void onResult(boolean success);
    }
}
