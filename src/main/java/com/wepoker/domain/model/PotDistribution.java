package com.wepoker.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

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
    private int rank;
    private String reason;
}
