package com.wepoker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Game Config - 桌位的静态配置
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TableConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private int tableId;
    private String tableName;
    private int maxPlayers;
    private long smallBlindAmount;
    private long bigBlindAmount;
    private long minBuyIn;
    private long maxBuyIn;
    private double rakePercentage;
    private long rakeMaxPerHand;
    private int timeToAct;
    private int timeBank;
    private boolean runItTwiceAllowed;
    private LocalDateTime createdAt;
}
