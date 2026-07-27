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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 首包探测桥（文档 5.10.4）—— 整个容错设计最精巧的部分。
 *
 * 装饰器模式 + 缓冲：夹在 {@link ChatClient} 和业务 callback 之间。
 *
 * 核心机制 bufferOrDispatch：首包到达前所有动作缓冲不转发，首包成功才 commit 重放。
 *
 * 为什么流式降级"用户无感知"：
 *  1. 首包到达前吞掉所有 onContent，下游（SSE 推前端）什么都没收到；
 *  2. 探测失败时 handle.cancel() 终止流，丢弃 buffer，换下一个候选重试；
 *  3. 探测成功才 commit，buffer 首包瞬间重放，之后正常推流；
 *  4. 切换发生在"前端还没收到任何字"的窗口内，用户只感知"开始输出时间略差"。
 *
 * 线程安全：所有「判断 committed + 转发下游」都用同一把 {@link #dispatchLock} 串行化，
 * 保证 commit 重放 buffer 与 SSE 线程的新 token 直通不会交错（否则 token 顺序会乱）。
 */
public final class ProbeStreamBridge implements StreamCallback {

    private final StreamCallback downstream;
    private final CompletableFuture<ProbeResult> probe = new CompletableFuture<>();
    private final List<Runnable> buffer = new ArrayList<>();
    /** 串行化所有转发：commit 重放 buffer、新 token 直通、onError/onComplete 转发都进这把锁 */
    private final ReentrantLock dispatchLock = new ReentrantLock();
    private volatile boolean committed = false;

    public ProbeStreamBridge(StreamCallback downstream) {
        this.downstream = downstream;
    }

    @Override
    public void onContent(String token) {
        handle(token, true, () -> downstream.onContent(token));
    }

    @Override
    public void onThinking(String token) {
        handle(token, false, () -> downstream.onThinking(token));
    }

    /**
     * 首包处理：成功完成探测 future，未提交则缓冲，已提交则直通。
     * <p>★ 顺序保证：整个「判断 committed + 缓冲/转发」在 {@link #dispatchLock} 内完成，
     * 与 {@link #commit()} 的 buffer 重放互斥，杜绝"commit 重放 token1 时 SSE 线程直通 token2"
     * 的交错乱序。
     *
     * <p>★★ 首包只认正式回答内容（{@code isContent=true}），不认思维链 token。
     * 推理模型（DeepSeek/小米 MiMo 等）开启 thinking 时会先吐几十秒 {@code reasoning_content}，
     * 若用思维链首字触发探测 SUCCESS，会让路由层误判「模型已正常响应」并 commit，
     * 但下游用户要等思维链跑完才看到第一个正式字——首包探测的"快速降级"承诺形同虚设。
     * 只认 content，才能让「闷头思考几十秒不吐正式字」的模型被首包超时机制正确降级。
     */
    private void handle(String token, boolean isContent, Runnable action) {
        dispatchLock.lock();
        try {
            if (!committed) {
                // ★ 仅正式回答内容（非思维链）才算首包成功
                if (isContent && token != null && !token.isEmpty() && !probe.isDone()) {
                    probe.complete(ProbeResult.success());
                }
                buffer.add(action);
                return;
            }
            // 已提交：直通下游（在锁内串行化，与 commit 重放顺序一致）
            action.run();
        } finally {
            dispatchLock.unlock();
        }
    }

    @Override
    public void onError(Throwable error) {
        // 探测期间出错：完成探测为 ERROR，清空 buffer（让路由层决定是否换模型）
        probe.complete(ProbeResult.error(error));
        dispatchLock.lock();
        try {
            buffer.clear();
        } finally {
            dispatchLock.unlock();
        }
        // 不立即转发给 downstream，由 RoutingLLMService 判断：探测失败→换模型；全失败→统一报错
        if (committed) {
            downstream.onError(error);
        }
    }

    @Override
    public void onComplete() {
        // 流结束但未收到任何内容 → NO_CONTENT（视为失败）
        if (!probe.isDone()) {
            probe.complete(ProbeResult.noContent());
        }
        // 仅在已提交后转发 onComplete（探测失败时 buffer 已被丢弃，不应触发完成）
        if (committed) {
            downstream.onComplete();
        }
    }

    /**
     * 阻塞等待首包结果（被 {@link LlmFirstPacketProbe} 调用）。
     * 成功则 commit 重放 buffer，超时/失败返回相应结果。
     */
    public ProbeResult awaitFirstPacket(long timeout, TimeUnit unit) {
        try {
            ProbeResult result = probe.get(timeout, unit);
            if (result.isSuccess()) {
                commit();
            }
            return result;
        } catch (TimeoutException e) {
            return ProbeResult.timeout();
        } catch (Exception e) {
            return ProbeResult.error(e);
        }
    }

    /**
     * 提交：重放所有缓冲动作，此后新动作直接转发下游。
     * <p>★ 重放循环在 {@link #dispatchLock} 内执行，与 {@link #handle} 的直通分支互斥，
     * 保证「buffer 里的旧 token」一定先于「commit 后到达的新 token」转发，顺序严格。
     */
    private void commit() {
        List<Runnable> toReplay;
        dispatchLock.lock();
        try {
            if (committed) return;
            committed = true;
            // 在锁内拷贝并重放：避免重放期间 SSE 线程的新 token 插队（它们会在锁外等待，committed 已 true 后进锁直通）
            toReplay = new ArrayList<>(buffer);
            buffer.clear();
            for (Runnable r : toReplay) {
                try { r.run(); } catch (Exception ignored) { }
            }
        } finally {
            dispatchLock.unlock();
        }
    }


    public enum ProbeResultType { SUCCESS, ERROR, TIMEOUT, NO_CONTENT }

    public record ProbeResult(ProbeResultType type, Throwable error) {
        public boolean isSuccess() { return type == ProbeResultType.SUCCESS; }

        public static ProbeResult success() { return new ProbeResult(ProbeResultType.SUCCESS, null); }
        public static ProbeResult error(Throwable e) { return new ProbeResult(ProbeResultType.ERROR, e); }
        public static ProbeResult timeout() { return new ProbeResult(ProbeResultType.TIMEOUT, null); }
        public static ProbeResult noContent() { return new ProbeResult(ProbeResultType.NO_CONTENT, null); }
    }
}
