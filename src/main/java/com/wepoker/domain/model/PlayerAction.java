package com.wepoker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 玩家在当前手牌中的操作记录
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerAction implements Serializable {
    private static final long serialVersionUID = 1L;

    private long timestamp;
    private String action;
    private long amount;
    private String street;
    private long betAmount;
}
