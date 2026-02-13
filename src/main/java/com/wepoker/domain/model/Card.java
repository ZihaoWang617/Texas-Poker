package com.wepoker.domain.model;

import lombok.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * 纸牌枚举 - 52张标准牌
 * 使用4位二进制编码：
 * - 花色 (0-3): Spade, Heart, Diamond, Club
 * - 点数 (0-12): 2-14 (A=14)
 */
@Getter
@RequiredArgsConstructor
public enum Suit {
    SPADE(0, "♠"),
    HEART(1, "♥"),
    DIAMOND(2, "♦"),
    CLUB(3, "♣");

    private final int value;
    private final String symbol;
}

@Getter
@RequiredArgsConstructor
public enum Rank {
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "T"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A");

    private final int value;
    private final String symbol;

    public static Rank fromValue(int value) {
        return values()[value - 2];
    }
}

/**
 * 纸牌类 - 表示一张扑克牌
 */
@Getter
@EqualsAndHashCode
@ToString
public class Card implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Suit suit;
    private final Rank rank;

    private Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public static Card of(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    /**
     * 编码为单一整数 (高效率传输)
     * 花色: 2位 (0-3)
     * 点数: 4位 (0-12)
     */
    public int encode() {
        return (suit.getValue() << 4) | (rank.getValue() - 2);
    }

    public static Card decode(int encoded) {
        int suitValue = (encoded >> 4) & 0x3;
        int rankValue = (encoded & 0xF) + 2;
        return of(Suit.values()[suitValue], Rank.fromValue(rankValue));
    }

    public String toShortString() {
        return rank.getSymbol() + suit.getSymbol();
    }
}
