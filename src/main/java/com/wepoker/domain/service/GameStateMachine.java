package com.wepoker.domain.service;

import com.wepoker.domain.model.*;
import lombok.extern.slf4j.Slf4j;

/**
 * GameStateMachine - 管理牌局的严格状态转移
 * 
 * 状态转移流图：
 * WAITING -> DEALING -> PRE_FLOP -> FLOP -> TURN -> RIVER -> SHOWDOWN -> CLEANUP -> WAITING
 * 
 * 每个状态转移都验证前置条件，确保游戏流程的合法性
 */
@Slf4j
public class GameStateMachine {

    private final Table table;
    private final DealerService dealerService;

    public GameStateMachine(Table table, DealerService dealerService) {
        this.table = table;
        this.dealerService = dealerService;
    }

    /**
     * Compatibility entrypoint for existing service flow.
     */
    public void processPlayerAction(Table table, Player player, String action, long amount) {
        log.debug("processPlayerAction called: table={}, player={}, action={}, amount={}",
            table != null ? table.getTableId() : null,
            player != null ? player.getPlayerId() : null,
            action, amount);
    }

    /**
     * 尝试转移到下一个状态
     * @return 状态转移是否成功
     */
    public boolean transitionToNextState() {
        TableState currentState = table.getState();
        TableState nextState = getNextState(currentState);

        if (nextState == null) {
            log.warn("Cannot transition from state: {}", currentState);
            return false;
        }

        // 验证转移的前置条件
        if (!validateTransition(currentState, nextState)) {
            log.warn("Transition validation failed: {} -> {}", currentState, nextState);
            return false;
        }

        // 执行状态转移
        executeStateTransition(nextState);

        log.info("Table {} transitioned: {} -> {}", table.getTableId(), currentState, nextState);
        return true;
    }

    /**
     * 获取给定状态的下一个状态
     */
    private TableState getNextState(TableState current) {
        return switch (current) {
            case WAITING -> TableState.DEALING;
            case DEALING -> TableState.PRE_FLOP;
            case PRE_FLOP -> TableState.FLOP;
            case FLOP -> TableState.TURN;
            case TURN -> TableState.RIVER;
            case RIVER -> TableState.SHOWDOWN;
            case SHOWDOWN -> TableState.CLEANUP;
            case CLEANUP -> TableState.WAITING;
            default -> null;
        };
    }

    /**
     * 验证状态转移的前置条件
     */
    private boolean validateTransition(TableState from, TableState to) {
        return switch (from) {
            case WAITING -> {
                // 至少要有2个有效玩家
                long activePlayers = table.getActivePlayers().stream()
                    .filter(p -> p.getStackSize() > 0)
                    .count();
                yield activePlayers >= 2;
            }
            case DEALING -> {
                // 所有玩家都已发底牌
                yield table.getCurrentHand() != null &&
                    table.getCurrentHand().getPlayerHoleCards().size() == table.getActivePlayers().size();
            }
            case PRE_FLOP -> {
                // 至少有一个玩家没有fold
                long activePlayers = table.getActivePlayers().stream()
                    .filter(Player::isInHand)
                    .count();
                yield activePlayers >= 2;
            }
            case FLOP -> {
                // 翻牌已发
                yield table.getCurrentHand() != null &&
                    table.getCurrentHand().getCommunityCardCount() >= 3;
            }
            case TURN -> {
                // 转牌已发
                yield table.getCurrentHand() != null &&
                    table.getCurrentHand().getCommunityCardCount() >= 4;
            }
            case RIVER -> {
                // 河牌已发
                yield table.getCurrentHand() != null &&
                    table.getCurrentHand().getCommunityCardCount() >= 5;
            }
            case SHOWDOWN -> {
                // 河牌街所有玩家已行动，或仅剩一个玩家
                long inHandPlayers = table.getActivePlayers().stream()
                    .filter(Player::isInHand)
                    .count();
                yield inHandPlayers <= 1 || allPlayersHaveActed();
            }
            case CLEANUP -> {
                // 总是可以清算
                yield true;
            }
            default -> false;
        };
    }

    /**
     * 检查所有玩家是否已行动（或全下）
     */
    private boolean allPlayersHaveActed() {
        return table.getActivePlayers().stream()
            .filter(Player::isInHand)
            .allMatch(p -> p.hasActed() || p.isAllIn());
    }

    /**
     * 执行状态转移的具体逻辑
     */
    private void executeStateTransition(TableState newState) {
        switch (newState) {
            case DEALING:
                handleDealing();
                break;
            case PRE_FLOP:
                handlePreFlop();
                break;
            case FLOP:
                handleFlop();
                break;
            case TURN:
                handleTurn();
                break;
            case RIVER:
                handleRiver();
                break;
            case SHOWDOWN:
                handleShowdown();
                break;
            case CLEANUP:
                handleCleanup();
                break;
            case WAITING:
                handleWaiting();
                break;
            default:
                break;
        }

        table.setState(newState);
        table.setLastActivityTime(java.time.LocalDateTime.now());
    }

    /**
     * DEALING - 发底牌
     */
    private void handleDealing() {
        Hand hand = new Hand();
        hand.setTableId(table.getTableId());
        hand.setHandId(table.getTableId() + "_" + System.currentTimeMillis());
        hand.setCreatedAt(System.currentTimeMillis());
        hand.setCurrentStreet("PRE_FLOP");

        // 发底牌给所有玩家
        for (Player player : table.getActivePlayers()) {
            if (player.getStackSize() > 0) {
                Card[] holeCards = dealerService.dealHoleCards(player, 2);
                hand.addPlayerHoleCards(player.getPlayerId(), holeCards);
                player.setHoleCards(holeCards);
                player.setStatus(PlayerStatus.ACTIVE);
            }
        }

        table.setCurrentHand(hand);
        log.info("Dealing complete. Hand: {}", hand.getHandId());
    }

    /**
     * PRE_FLOP - 前翻街准备
     */
    private void handlePreFlop() {
        Hand hand = table.getCurrentHand();
        hand.setCurrentStreet("PRE_FLOP");

        // 重置玩家的行动标记
        for (Player player : table.getActivePlayers()) {
            if (player.isInHand()) {
                player.setHasActed(false);
            }
        }

        // 设置第一个需要行动的玩家（Big Blind左边的玩家，或Head-up时为Big Blind）
        if (table.getConfig().getMaxPlayers() == 2) {
            // Head-up: Small Blind行动第一
            table.setNextToActSeat(table.getSmallBlindSeat());
        } else {
            // 多人: UTG (Under The Gun) = Big Blind的左边玩家 + 1
            table.setNextToActSeat((table.getBigBlindSeat() + 1) % table.getConfig().getMaxPlayers());
        }

        log.debug("Pre-flop street started");
    }

    /**
     * FLOP - 翻牌
     */
    private void handleFlop() {
        Hand hand = table.getCurrentHand();
        Card[] flop = dealerService.dealFlop();
        hand.setCommunityCards(flop);
        hand.setCurrentStreet("FLOP");

        resetStreetAction();
        log.debug("Flop dealt: {}", formatCards(flop));
    }

    /**
     * TURN - 转牌
     */
    private void handleTurn() {
        Hand hand = table.getCurrentHand();
        Card turn = dealerService.dealTurn();
        Card[] updatedCards = hand.getCommunityCards();
        if (updatedCards.length < 4) {
            updatedCards = new Card[4];
            System.arraycopy(hand.getCommunityCards(), 0, updatedCards, 0, hand.getCommunityCardCount());
        }
        updatedCards[3] = turn;
        hand.setCommunityCards(updatedCards);
        hand.setCurrentStreet("TURN");

        resetStreetAction();
        log.debug("Turn dealt: {}", turn.toShortString());
    }

    /**
     * RIVER - 河牌
     */
    private void handleRiver() {
        Hand hand = table.getCurrentHand();
        Card river = dealerService.dealRiver();
        Card[] updatedCards = new Card[5];
        System.arraycopy(hand.getCommunityCards(), 0, updatedCards, 0, hand.getCommunityCardCount());
        updatedCards[4] = river;
        hand.setCommunityCards(updatedCards);
        hand.setCurrentStreet("RIVER");

        resetStreetAction();
        log.debug("River dealt: {}", river.toShortString());
    }

    /**
     * SHOWDOWN - 比牌/结算
     */
    private void handleShowdown() {
        Hand hand = table.getCurrentHand();
        hand.setCurrentStreet("SHOWDOWN");
        log.info("Showdown stage reached for hand: {}", hand.getHandId());
    }

    /**
     * CLEANUP - 清算底池，确定赢家
     */
    private void handleCleanup() {
        Hand hand = table.getCurrentHand();
        log.info("Cleanup stage for hand: {}", hand.getHandId());
        
        // TODO: 结算逻辑将在Service层实现
        // - 评估每个玩家的手牌
        // - 计算pot分配
        // - 更新玩家筹码和账户余额
    }

    /**
     * WAITING - 等待下一手牌
     */
    private void handleWaiting() {
        table.resetForNewHand();
        log.info("Table {} ready for next hand", table.getTableId());
    }

    /**
     * 重置当前街的玩家行动标记
     */
    private void resetStreetAction() {
        for (Player player : table.getActivePlayers()) {
            if (player.isInHand() && !player.isAllIn()) {
                player.setHasActed(false);
            }
        }
        table.setCurrentBetThisStreet(0);
    }

    /**
     * 格式化牌列表为字符串
     */
    private String formatCards(Card[] cards) {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            if (card != null) {
                sb.append(card.toShortString()).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
