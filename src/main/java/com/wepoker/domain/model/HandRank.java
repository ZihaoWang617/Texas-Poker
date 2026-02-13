package com.wepoker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * 玩家在牌局中的手牌评分
 */
@Getter
@AllArgsConstructor
@ToString
public class HandRank implements Comparable<HandRank>, Serializable {
    private static final long serialVersionUID = 1L;

    private int rankValue;
    private int handType;
    private String description;
    private List<Card> bestFive;

    @Override
    public int compareTo(HandRank other) {
        return Integer.compare(this.rankValue, other.rankValue);
    }

    public String getSimpleDescription() {
        return description;
    }
}
