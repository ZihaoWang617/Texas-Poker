package com.wepoker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Set;

/**
 * 底池结构 - 支持多个Pot拆分（All-in场景）
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Pot implements Serializable {
    private static final long serialVersionUID = 1L;

    private int potSequence;
    private long potSize;
    private Set<String> eligiblePlayers;
    private long minRaiseAmount;

    public boolean isPlayerEligible(String playerId) {
        return eligiblePlayers.contains(playerId);
    }

    public void addAmount(long amount) {
        this.potSize += amount;
    }
}
