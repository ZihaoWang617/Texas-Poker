package com.wepoker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 玩家主要类
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Player implements Serializable {
    private static final long serialVersionUID = 1L;

    private String playerId;
    private String nickname;
    private int seatNumber;
    private long accountBalance;
    private LocalDateTime joinTime;

    private long stackSize;
    private PlayerStatus status;
    private boolean isSmallBlind;
    private boolean isBigBlind;
    private boolean isButtonAndDealer;
    private boolean hasActed;

    private Card[] holeCards;
    private long betThisStreet;
    private long currentBet;
    private long totalBetInPot;

    private boolean isWaitingForAction;
    private long actionDeadline;
    private PlayerAction lastAction;

    private String lastIpAddress;
    private String lastDeviceId;
    private LocalDateTime lastActiveTime;

    private int tableId;

    public boolean isInHand() {
        return status == PlayerStatus.ACTIVE || status == PlayerStatus.ALL_IN;
    }

    public boolean hasFolded() {
        return status == PlayerStatus.FOLDED;
    }

    public boolean isAllIn() {
        return status == PlayerStatus.ALL_IN;
    }

    public synchronized void deductStack(long amount) {
        if (stackSize < amount) {
            throw new IllegalArgumentException(
                String.format("Insufficient stack: %d < %d for player %s", stackSize, amount, playerId));
        }
        this.stackSize -= amount;
        this.totalBetInPot += amount;
    }

    public synchronized void addStack(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot add negative amount");
        }
        this.stackSize += amount;
    }

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

    public long getAmountToCall(long currentBetThisStreet) {
        return Math.max(0, currentBetThisStreet - this.currentBet);
    }

    public boolean hasActed() {
        return this.hasActed;
    }

    // Compatibility helpers for service layer.
    public void setPlayerId(Long playerId) {
        this.playerId = playerId == null ? null : String.valueOf(playerId);
    }

    public Long getPlayerIdAsLong() {
        return playerId == null ? null : Long.parseLong(playerId);
    }

    public void setStack(long stack) {
        this.stackSize = stack;
    }

    public long getStack() {
        return this.stackSize;
    }

    public void setSeat(int seat) {
        this.seatNumber = seat;
    }

    public int getSeat() {
        return this.seatNumber;
    }
}
