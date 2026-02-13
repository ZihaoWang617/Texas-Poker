package com.wepoker.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

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

    public String getToShortString() {
        return toShortString();
    }
}
