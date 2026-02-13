package com.wepoker.domain.model;

import lombok.*;
import java.io.Serializable;
import java.util.*;

/**
 * 玩家在牌局中的手牌评分
 * HandEvaluator 最后计算出的结果
 */
@Getter
@AllArgsConstructor
@ToString
public class HandRank implements Comparable<HandRank>, Serializable {
    private static final long serialVersionUID = 1L;

    private int rankValue;        // 1-2598960 (unique hand ranks)
    private int handType;         // 0=High Card, 1=Pair, 2=TwoPair, ..., 8=RoyalFlush
    private String description;   // "Pair of Aces"
    private List<Card> bestFive;  // 最好的5张牌组合

    @Override
    public int compareTo(HandRank other) {
        return Integer.compare(this.rankValue, other.rankValue);
    }

    /**
     * 获取简单描述适配WePoker UI
     */
    public String getSimpleDescription() {
        return description;
    }
}

/**
 * 单个结算分配记录
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PotDistribution implements Serializable {
    private static final long serialVersionUID = 1L;

    private String playerId;
    private long amount;
    private int rank;  // 该玩家的排名
    private String reason;  // "Main Pot Winner", "Side Pot #1 Winner", etc.
}

/**
 * 一手牌的完整信息
 */
@Getter
@Setter
@ToString
public class Hand implements Serializable {
    private static final long serialVersionUID = 1L;

    private String handId;              // 唯一手番ID (tableId_timestamp)
    private int tableId;
    private long createdAt;

    // 底牌信息
    private Card[] communityCards;      // 5张公共牌（初始为null，逐步填充）
    private int communityCardCount;     // 已发公共牌数 (0/3/4/5)

    // 玩家手牌映射
    private Map<String, Card[]> playerHoleCards;  // playerId -> [card1, card2]

    // 当前牌局信息
    private String currentStreet;       // PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN
    private int smallBlindSeatNumber;
    private int bigBlindSeatNumber;
    private int buttonSeatNumber;

    // 底池信息
    private long totalPotSize;
    private List<Pot> pots;             // 多个Pot结构（支持All-in拆分）

    // 玩家投票/结算
    private Map<String, HandRank> playerRanks;     // playerId -> HandRank
    private List<PotDistribution> distributions;   // 最终分配结果

    // Run It Twice
    private boolean isRunItTwice;
    private List<Card[]> alternativeCommunityCards;  // 第二次run的公共牌

    // 历史记录
    private List<PlayerAction> actionHistory;

    public Hand() {
        this.playerHoleCards = new HashMap<>();
        this.playerRanks = new HashMap<>();
        this.distributions = new ArrayList<>();
        this.pots = new ArrayList<>();
        this.actionHistory = new ArrayList<>();
        this.communityCards = new Card[5];
        this.communityCardCount = 0;
    }

    /**
     * 添加玩家的底牌
     */
    public void addPlayerHoleCards(String playerId, Card[] cards) {
        if (cards == null || cards.length != 2) {
            throw new IllegalArgumentException("Hole cards must be exactly 2");
        }
        this.playerHoleCards.put(playerId, cards);
    }

    /**
     * 设置公共牌（逐步填充：flop 3张，turn 1张，river 1张）
     */
    public void setCommunityCards(Card[] cards) {
        if (cards.length > 5) {
            throw new IllegalArgumentException("Community cards cannot exceed 5");
        }
        for (int i = 0; i < cards.length; i++) {
            this.communityCards[i] = cards[i];
        }
        this.communityCardCount = cards.length;
    }

    /**
     * 获取玩家的7张牌 (底牌2张 + 公共牌5张)
     */
    public Card[] getPlayerSevenCards(String playerId) {
        Card[] holeCards = playerHoleCards.get(playerId);
        if (holeCards == null) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }
        Card[] sevenCards = new Card[7];
        System.arraycopy(holeCards, 0, sevenCards, 0, 2);
        System.arraycopy(communityCards, 0, sevenCards, 2, communityCardCount);
        return sevenCards;
    }

    /**
     * 手牌是否已完成（river或showdown）
     */
    public boolean isComplete() {
        return "RIVER".equals(currentStreet) || "SHOWDOWN".equals(currentStreet);
    }
}

/**
 * 底池结构 - 支持多个Pot拆分（All-in场景）
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Pot implements Serializable {
    private static final long serialVersionUID = 1L;

    private int potSequence;            // 0=Main Pot, 1,2,3... = Side Pot
    private long potSize;               // 本Pot的总金额
    private Set<String> eligiblePlayers;  // 可以赢取此pot的玩家列表
    private long minRaiseAmount;        // 该pot的最小下注金额阈值

    /**
     * 该玩家是否有资格赢取此pot
     */
    public boolean isPlayerEligible(String playerId) {
        return eligiblePlayers.contains(playerId);
    }

    /**
     * 为此Pot添加筹码
     */
    public void addAmount(long amount) {
        this.potSize += amount;
    }
}
