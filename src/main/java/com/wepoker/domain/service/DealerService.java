package com.wepoker.domain.service;

import com.wepoker.domain.model.*;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.*;

/**
 * DealerService - 负责牌局的发牌逻辑
 * 
 * 核心功能：
 * 1. 使用SecureRandom确保真随机
 * 2. Fisher-Yates洗牌算法
 * 3. 发底牌
 * 4. 发翻牌、转牌、河牌
 * 5. 支持Run It Twice的两次发牌
 */
@Slf4j
public class DealerService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Queue<Card> deck = new LinkedList<>();

    // 牌库缓存（预生成的牌序列，用于断线重连恢复）
    private final List<Card> cachedDeckForRecovery = new ArrayList<>();

    /**
     * 初始化牌库（52张标准牌）
     */
    public void initializeDeck() {
        deck.clear();
        List<Card> cards = new ArrayList<>();

        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(Card.of(suit, rank));
            }
        }

        // Fisher-Yates洗牌算法
        shuffleDeckFisherYates(cards);

        deck.addAll(cards);
        cachedDeckForRecovery.addAll(cards);

        log.debug("Deck initialized and shuffled with {} cards", deck.size());
    }

    /**
     * Fisher-Yates洗牌算法
     * 时间复杂度: O(n)
     * 保证每种排列的概率相等（均匀分布）
     * 
     * 算法：从后往前遍历，每次将当前位置与[0, i]之间的随机位置交换
     */
    private void shuffleDeckFisherYates(List<Card> cards) {
        for (int i = cards.size() - 1; i > 0; i--) {
            // 生成[0, i]之间的随机数
            int randomIndex = secureRandom.nextInt(i + 1);

            // 交换
            Card temp = cards.get(i);
            cards.set(i, cards.get(randomIndex));
            cards.set(randomIndex, temp);
        }

        log.debug("Deck shuffled using Fisher-Yates algorithm");
    }

    /**
     * 发底牌给指定玩家
     * 
     * @param player 玩家
     * @param holeCardCount 发几张牌（通常是2）
     */
    public Card[] dealHoleCards(Player player, int holeCardCount) {
        if (holeCardCount != 2) {
            throw new IllegalArgumentException("Hole cards must be exactly 2");
        }

        Card[] holeCards = new Card[2];
        for (int i = 0; i < 2; i++) {
            Card card = deck.poll();
            if (card == null) {
                throw new RuntimeException("Deck exhausted while dealing hole cards");
            }
            holeCards[i] = card;
        }

        log.debug("Dealt hole cards to player {}: {}{} {}{}", 
            player.getPlayerId(), 
            holeCards[0].toShortString(), 
            holeCards[1].toShortString());

        return holeCards;
    }

    /**
     * 发翻牌 (Flop) - 3张牌
     */
    public Card[] dealFlop() {
        Card[] flop = new Card[3];
        
        // Burn card (丢一张牌)
        Card burned = deck.poll();
        log.debug("Burned card before flop: {}", burned.toShortString());

        // 发3张翻牌
        for (int i = 0; i < 3; i++) {
            Card card = deck.poll();
            if (card == null) {
                throw new RuntimeException("Deck exhausted while dealing flop");
            }
            flop[i] = card;
        }

        log.debug("Flop dealt: {}{} {}{} {}{}", 
            flop[0].getToShortString(), 
            flop[1].getToShortString(), 
            flop[2].getToShortString());

        return flop;
    }

    /**
     * 发转牌 (Turn) - 1张牌
     */
    public Card dealTurn() {
        // Burn card
        Card burned = deck.poll();
        log.debug("Burned card before turn: {}", burned.toShortString());

        Card turn = deck.poll();
        if (turn == null) {
            throw new RuntimeException("Deck exhausted while dealing turn");
        }

        log.debug("Turn dealt: {}", turn.toShortString());
        return turn;
    }

    /**
     * 发河牌 (River) - 1张牌
     */
    public Card dealRiver() {
        // Burn card
        Card burned = deck.poll();
        log.debug("Burned card before river: {}", burned.toShortString());

        Card river = deck.poll();
        if (river == null) {
            throw new RuntimeException("Deck exhausted while dealing river");
        }

        log.debug("River dealt: {}", river.toShortString());
        return river;
    }

    /**
     * 生成Run It Twice的第二套公共牌
     * 从当前牌库继续发牌
     */
    public Card[] dealAlternativeCommunityCards(Hand hand) {
        // 当前已发了多少张公共牌
        int alreadyDealt = hand.getCommunityCardCount();

        Card[] altCommunity = new Card[5];

        // 复制已有的牌
        if (alreadyDealt > 0) {
            System.arraycopy(hand.getCommunityCards(), 0, altCommunity, 0, alreadyDealt);
        }

        // 从剩余牌库发新牌（跳过burn cards）
        int cardsToAdd = 5 - alreadyDealt;
        for (int i = 0; i < cardsToAdd; i++) {
            // 发牌前burn一张
            if (deck.poll() == null) {
                throw new RuntimeException("Deck exhausted");
            }

            Card card = deck.poll();
            if (card == null) {
                throw new RuntimeException("Deck exhausted");
            }
            altCommunity[alreadyDealt + i] = card;
        }

        log.debug("Alternative community cards generated for Run It Twice");
        return altCommunity;
    }

    /**
     * 获取剩余牌数
     */
    public int getRemainingCards() {
        return deck.size();
    }

    /**
     * 恢复牌库状态（用于断线重连）
     * 从缓存中恢复到指定位置
     */
    public void recoverDeckState(int cardsDealt) {
        deck.clear();
        int startIndex = cardsDealt;
        if (startIndex < cachedDeckForRecovery.size()) {
            deck.addAll(cachedDeckForRecovery.subList(startIndex, cachedDeckForRecovery.size()));
        }
        log.info("Deck recovered with {} cards remaining", deck.size());
    }

    /**
     * 验证已发牌的真实性（反作弊）
     * 检查已发的牌是否在原始52张牌中
     */
    public boolean verifyDealtCards(List<Card> dealtCards) {
        Set<Card> allCards = new HashSet<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                allCards.add(Card.of(suit, rank));
            }
        }

        for (Card card : dealtCards) {
            if (!allCards.contains(card)) {
                log.warn("Invalid card detected: {}", card.toShortString());
                return false;
            }
        }

        // 检查是否有重复的牌
        if (new HashSet<>(dealtCards).size() != dealtCards.size()) {
            log.warn("Duplicate cards detected");
            return false;
        }

        return true;
    }

    /**
     * 获取当前牌库序列的哈希值（用于验证）
     */
    public String getDeckHash() {
        StringBuilder sb = new StringBuilder();
        for (Card card : deck) {
            sb.append(card.encode()).append(",");
        }
        return Integer.toHexString(sb.toString().hashCode());
    }
}
