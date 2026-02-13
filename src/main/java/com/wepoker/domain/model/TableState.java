package com.wepoker.domain.model;

/**
 * 房间状态机
 */
public enum TableState {
    WAITING,
    DEALING,
    PRE_FLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN,
    CLEANUP,
    CLOSED
}
