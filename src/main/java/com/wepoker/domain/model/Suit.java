package com.wepoker.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 花色
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
