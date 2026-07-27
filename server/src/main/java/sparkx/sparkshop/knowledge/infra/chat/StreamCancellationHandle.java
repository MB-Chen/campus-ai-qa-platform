// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra.chat;

/**
 * 流式取消句柄（文档 5.10）。首包探测失败时调用 cancel() 终止在途流。
 */
public interface StreamCancellationHandle {

    /** 取消在途的流式请求 */
    void cancel();

    /** 是否已取消 */
    boolean isCancelled();

    /** 空操作句柄（全候选失败时返回） */
    static StreamCancellationHandle noop() {
        return NoopHandle.INSTANCE;
    }

    /** 基于标志位的简单实现 */
    class Simple implements StreamCancellationHandle {
        private volatile boolean cancelled;
        private final Runnable cancelAction;

        public Simple(Runnable cancelAction) {
            this.cancelAction = cancelAction != null ? cancelAction : () -> { };
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                try { cancelAction.run(); } catch (Exception ignored) { }
            }
        }

        @Override
        public boolean isCancelled() { return cancelled; }
    }

    enum NoopHandle implements StreamCancellationHandle {
        INSTANCE;
        @Override public void cancel() { }
        @Override public boolean isCancelled() { return false; }
    }
}
