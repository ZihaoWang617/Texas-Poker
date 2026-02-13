package com.wepoker.domain.service;

import com.wepoker.domain.model.*;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ActionTimer - 使用HashedWheelTimer管理玩家行动倒计时
 * 
 * 设计原理：
 * - HashedWheelTimer是Netty提供的高效时间轮实现
 * - 适合处理大量短期定时任务（如数百个玩家的倒计时）
 * - 精度：10ms (可配置)
 * 
 * 流程：
 * 1. 玩家开始行动时，添加一个倒计时任务
 * 2. 倒计时包括两个阶段：基础时间 (15s) + Time Bank (30s)
 * 3. 如果超时，自动执行FOLD操作
 */
@Slf4j
public class ActionTimer {

    private final HashedWheelTimer wheelTimer;
    private final int baseTimeToAct;          // 基础行动时间(秒) - 如15s
    private final int timeBankDuration;       // Time bank时间(秒) - 如30s
    private final int maxTimeBankPerSession;  // 每个session最多使用time bank次数

    // 追踪每个玩家的倒计时任务
    private final Map<String, TimerEntry> activeTimers = new HashMap<>();

    // 超时回调
    private final TimeoutCallback timeoutCallback;

    public ActionTimer(int baseTimeToAct, int timeBankDuration, TimeoutCallback callback) {
        this.baseTimeToAct = baseTimeToAct;
        this.timeBankDuration = timeBankDuration;
        this.maxTimeBankPerSession = 3;  // 每个牌局最多3次
        this.timeoutCallback = callback;

        // 创建时间轮：tickDuration=10ms, ticksPerWheel=256
        // 这样可以精确处理0.01s - 25.6s范围内的定时任务
        this.wheelTimer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS, 256);
    }

    /**
     * 开始玩家的倒计时
     * 
     * @param playerId 玩家ID
     * @param onTimeoutFold 是否超时自动fold
     */
    public void startActionTimer(String playerId, boolean onTimeoutFold) {
        // 取消之前的定时器
        cancelActionTimer(playerId);

        // 计算超时时间
        long timeoutMillis = (long) baseTimeToAct * 1000;  // 转换为毫秒

        // 创建定时任务
        Timeout timeout = wheelTimer.newTimeout(
            task -> handleTimeout(playerId, onTimeoutFold),
            timeoutMillis,
            TimeUnit.MILLISECONDS
        );

        activeTimers.put(playerId, new TimerEntry(timeout, System.currentTimeMillis(), timeoutMillis));
        log.debug("Action timer started for player {} with {} seconds", playerId, baseTimeToAct);
    }

    /**
     * 玩家激活Time Bank（延长倒计时）
     * 
     * @param playerId 玩家ID
     * @return 是否成功激活（检查是否还有剩余的time bank）
     */
    public boolean activateTimeBank(String playerId, Table table, int timeBankUsedCount) {
        if (timeBankUsedCount >= maxTimeBankPerSession) {
            log.warn("Player {} has used up all time banks", playerId);
            return false;
        }

        // 取消当前定时器
        TimerEntry entry = activeTimers.get(playerId);
        if (entry != null) {
            entry.timeout.cancel();
        }

        // 创建新的定时任务（延长的时间）
        long newTimeoutMillis = (long) timeBankDuration * 1000;
        Timeout newTimeout = wheelTimer.newTimeout(
            task -> handleTimeout(playerId, true),  // Time bank超时后自动fold
            newTimeoutMillis,
            TimeUnit.MILLISECONDS
        );

        activeTimers.put(playerId, new TimerEntry(newTimeout, System.currentTimeMillis(), newTimeoutMillis));
        table.setTimeBankUsedCount(timeBankUsedCount + 1);

        log.info("Player {} activated time bank (used: {}/{})", 
            playerId, timeBankUsedCount + 1, maxTimeBankPerSession);

        return true;
    }

    /**
     * 玩家完成行动，取消倒计时
     */
    public void cancelActionTimer(String playerId) {
        TimerEntry entry = activeTimers.remove(playerId);
        if (entry != null) {
            entry.timeout.cancel();
            long elapsedMs = System.currentTimeMillis() - entry.startTime;
            log.debug("Action timer cancelled for player {} (elapsed: {}ms)", playerId, elapsedMs);
        }
    }

    /**
     * 处理超时事件
     */
    private void handleTimeout(String playerId, boolean autoFold) {
        activeTimers.remove(playerId);
        log.warn("Player {} action timeout, auto-fold: {}", playerId, autoFold);

        // 调用超时回调
        if (timeoutCallback != null) {
            timeoutCallback.onActionTimeout(playerId, autoFold);
        }
    }

    /**
     * 获取玩家的剩余时间（毫秒）
     */
    public long getRemainingTime(String playerId) {
        TimerEntry entry = activeTimers.get(playerId);
        if (entry == null) {
            return 0;
        }

        long elapsedMs = System.currentTimeMillis() - entry.startTime;
        long remainingMs = entry.timeoutMillis - elapsedMs;
        return Math.max(0, remainingMs);
    }

    /**
     * 获取玩家的行动截止时间戳
     */
    public long getActionDeadlineTimestamp(String playerId) {
        TimerEntry entry = activeTimers.get(playerId);
        if (entry == null) {
            return 0;
        }
        return entry.startTime + entry.timeoutMillis;
    }

    /**
     * 检查玩家是否仍在倒计时中
     */
    public boolean hasActiveTimer(String playerId) {
        return activeTimers.containsKey(playerId);
    }

    /**
     * 获取所有活跃的倒计时玩家
     */
    public Set<String> getPlayersWithActiveTimers() {
        return new HashSet<>(activeTimers.keySet());
    }

    /**
     * 停止所有倒计时
     */
    public void stopAllTimers() {
        for (TimerEntry entry : activeTimers.values()) {
            entry.timeout.cancel();
        }
        activeTimers.clear();
        log.info("All action timers stopped");
    }

    /**
     * 关闭时间轮（程序关闭时调用）
     */
    public void shutdown() {
        stopAllTimers();
        wheelTimer.stop();
        log.info("ActionTimer shutdown complete");
    }

    /**
     * 内部类：定时器项
     */
    private static class TimerEntry {
        Timeout timeout;
        long startTime;
        long timeoutMillis;

        TimerEntry(Timeout timeout, long startTime, long timeoutMillis) {
            this.timeout = timeout;
            this.startTime = startTime;
            this.timeoutMillis = timeoutMillis;
        }
    }

    /**
     * 超时回调接口
     */
    public interface TimeoutCallback {
        /**
         * 玩家行动超时
         * 
         * @param playerId 玩家ID
         * @param autoFold 是否自动fold
         */
        void onActionTimeout(String playerId, boolean autoFold);
    }
}
