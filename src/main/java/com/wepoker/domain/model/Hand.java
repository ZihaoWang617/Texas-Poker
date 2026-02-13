package com.wepoker.domain.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一手牌的完整信息
 */
@Getter
@Setter
@ToString
public class Hand implements Serializable {
    private static final long serialVersionUID = 1L;

    private String handId;
    private int tableId;
    private long createdAt;

    private Card[] communityCards;
    private int communityCardCount;

    private Map<String, Card[]> playerHoleCards;

    private String currentStreet;
    private int smallBlindSeatNumber;
    private int bigBlindSeatNumber;
    private int buttonSeatNumber;

    private long totalPotSize;
    private List<Pot> pots;

    private Map<String, HandRank> playerRanks;
    private List<PotDistribution> distributions;

    private boolean isRunItTwice;
    private List<Card[]> alternativeCommunityCards;

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

    public void addPlayerHoleCards(String playerId, Card[] cards) {
        if (cards == null || cards.length != 2) {
            throw new IllegalArgumentException("Hole cards must be exactly 2");
        }
        this.playerHoleCards.put(playerId, cards);
    }

    public void setCommunityCards(Card[] cards) {
        if (cards.length > 5) {
            throw new IllegalArgumentException("Community cards cannot exceed 5");
        }
        for (int i = 0; i < cards.length; i++) {
            this.communityCards[i] = cards[i];
        }
        this.communityCardCount = cards.length;
    }

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

    public boolean isComplete() {
        return "RIVER".equals(currentStreet) || "SHOWDOWN".equals(currentStreet);
    }
}
