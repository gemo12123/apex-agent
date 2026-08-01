package org.gemo.apex.extension.model;

import org.gemo.apex.common.model.ModelStreamChunk;
import org.gemo.apex.common.tool.CancellationToken;

public interface ModelStreamObserver {
    void onChunk(ModelStreamChunk chunk);

    /**
     * 返回当前请求唯一的取消令牌，模型适配器应向其注册底层取消动作。
     */
    CancellationToken cancellationToken();
}
