package com.wepoker.domain.model;

import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 玩家状态枚举
 */
public enum PlayerStatus {
    SITTING,          // 已入座
    ACTIVE,           // 活跃（在本手中参与）
    FOLDED,           // 已弃牌
    ALL_IN,           // 全下
    LEFT,             // 离席
    DISCONNECTED      // 断线
}

/**
 * 玩家在当前手牌中的操作记录
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerAction implements Serializable {
    private static final long serialVersionUID = 1L;

    private long timestamp;      // 动作时间戳
    private String action;       // FOLD, CHECK, CALL, RAISE, ALL_IN, BET
    private long amount;         // 本次投注金额（仅适用于BET/RAISE/CALL）
    private String street;       // PRE_FLOP, FLOP, TURN, RIVER
    private long betAmount;      // 当前轮次累计投注额
}

/**
 * 玩家主要类
 * - 账户金额必须使用 long (以最小分值为单位，如：1元 = 100分)
 * - 处理并发时使用原子操作或锁保护
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Player implements Serializable {
    private static final long serialVersionUID = 1L;

    // 玩家身份信息
    private String playerId;                // 全局用户ID
    private String nickname;
    private int seatNumber;                 // 席位号 (0-8)
    private long accountBalance;            // 账户余额（最小分值单位）
    private LocalDateTime joinTime;

    // 当前牌局相关
    private long stackSize;                 // 筹码量（最小分值单位）
    private PlayerStatus status;            // 玩家状态
    private boolean isSmallBlind;
    private boolean isBigBlind;
    private boolean isButtonAndDealer;
    private boolean hasActed;              // 本轮是否已行动

    // 手牌信息
    private Card[] holeCards;              // 两张底牌 (null表示未发牌)
    private long betThisStreet;            // 当前轮街下注额（长期积累）
    private long currentBet;               // 本轮未跟注金额
    private long totalBetInPot;            // 本手牌总下注额

    // 快判断
    private boolean isWaitingForAction;    // 等待此玩家行动
    private long actionDeadline;           // 行动截止时间戳
    private PlayerAction lastAction;       // 最后一个动作记录

    // 统计数据（用于反作弊）
    private String lastIpAddress;
    private String lastDeviceId;
    private LocalDateTime lastActiveTime;

    // 五人/六人/九人比赛特定字段
    private int tableId;

    /**
     * 玩家是否仍在该手中活跃
     */
    public boolean isInHand() {
        return status == PlayerStatus.ACTIVE || status == PlayerStatus.ALL_IN;
    }

    /**
     * 玩家是否已弃牌
     */
    public boolean hasFolded() {
        return status == PlayerStatus.FOLDED;
    }

    /**
     * 玩家是否全下
     */
    public boolean isAllIn() {
        return status == PlayerStatus.ALL_IN;
    }

    /**
     * 原子性地扣除筹码（确保不会出现负筹码）
     * @throws IllegalArgumentException 如果筹码不足
     */
    public synchronized void deductStack(long amount) {
        if (stackSize < amount) {
            throw new IllegalArgumentException(
                String.format("Insufficient stack: %d < %d for player %s", 
                    stackSize, amount, playerId));
        }
        this.stackSize -= amount;
        this.totalBetInPot += amount;
    }

    /**
     * 原子性地增加筹码
     */
    public synchronized void addStack(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot add negative amount");
        }
        this.stackSize += amount;
    }

    /**
     * 重置玩家在新手牌中的临时数据
     */
    public void resetForNewHand() {
        this.holeCards = null;
        this.status = PlayerStatus.SITTING;
        this.betThisStreet = 0;
        this.currentBet = 0;
        this.totalBetInPot = 0;
        this.hasActed = false;
        this.isWaitingForAction = false;
        this.lastAction = null;
        this.isSmallBlind = false;
        this.isBigBlind = false;
    }

    /**
     * 获取本轮未跟最大金额
     */
    public long getAmountToCall(long currentBetThisStreet) {
        return Math.max(0, currentBetThisStreet - this.currentBet);
    }
}
