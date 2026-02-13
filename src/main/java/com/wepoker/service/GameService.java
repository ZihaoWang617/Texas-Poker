package com.wepoker.service;

import com.wepoker.domain.algorithm.HandEvaluator;
import com.wepoker.domain.model.Card;
import com.wepoker.domain.model.HandRank;
import com.wepoker.domain.model.Pot;
import com.wepoker.domain.model.PotDistribution;
import com.wepoker.domain.model.Player;
import com.wepoker.domain.model.PlayerStatus;
import com.wepoker.domain.model.Hand;
import com.wepoker.domain.model.PlayerAction;
import com.wepoker.domain.model.Rank;
import com.wepoker.domain.model.Suit;
import com.wepoker.domain.model.Table;
import com.wepoker.domain.model.TableConfig;
import com.wepoker.domain.model.TableState;
import com.wepoker.domain.service.GameStateMachine;
import com.wepoker.network.protocol.PokerMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 游戏业务逻辑服务
 */
@Slf4j
@Service
public class GameService {
    private static final long ACTION_TIMEOUT_MS = 30_000L;
    private static final long STREET_TRANSITION_DELAY_MS = 1200L;
    private static final long NEXT_HAND_DELAY_MS = 4000L;
    
    // 游戏中的所有房间
    private final ConcurrentHashMap<Long, Table> tables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, TableState> pendingStreetTransitions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService engine = Executors.newSingleThreadScheduledExecutor();
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired(required = false)
    private GameStateMachine gameStateMachine;

    @PostConstruct
    public void startEngineLoop() {
        engine.scheduleAtFixedRate(this::engineTickSafely, 500, 500, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdownEngineLoop() {
        engine.shutdownNow();
    }
    
    /**
     * 处理玩家加入房间
     */
    public void handleJoinTable(PokerMessage message) {
        Long tableId = message.getTableId();
        Long playerId = message.getPlayerId();
        long buyIn = ((Number) message.getPayloadField("buyIn")).longValue();
        
        log.info("Player {} joining table {}", playerId, tableId);
        
        Table table = tables.computeIfAbsent(tableId, k -> {
            Table newTable = new Table();
            newTable.setTableId(tableId);
            newTable.setMaxPlayers(6);
            return newTable;
        });
        
        // 创建玩家对象
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setStack(buyIn);
        player.setSeat(-1); // 等待分配座位
        
        // 分配座位
        int seat = table.allocateSeat();
        if (seat == -1) {
            log.warn("No available seat at table {}", tableId);
            return;
        }
        
        player.setSeat(seat);
        table.addPlayer(player);
        
        log.info("Player {} sat at table {} seat {}", playerId, tableId, seat);
    }
    
    /**
     * 处理玩家操作
     */
    public void handleAction(PokerMessage message) {
        Long tableId = message.getTableId();
        Long playerId = message.getPlayerId();
        String action = message.getType().toString();
        long amount = message.getPayloadField("amount") != null ? 
                     ((Number) message.getPayloadField("amount")).longValue() : 0;
        
        Table table = tables.get(tableId);
        if (table == null) {
            log.warn("Table {} not found", tableId);
            return;
        }
        
        Player player = table.getPlayer(playerId);
        if (player == null) {
            log.warn("Player {} not found in table {}", playerId, tableId);
            return;
        }
        
        log.info("Player {} action: {} amount: {} at table {}", playerId, action, amount, tableId);
        
        // 转发给状态机处理
        if (gameStateMachine != null) {
            gameStateMachine.processPlayerAction(table, player, action, amount);
        } else {
            log.warn("GameStateMachine is not configured; action processing skipped");
        }
    }
    
    /**
     * 处理玩家离开房间
     */
    public void handleLeaveTable(PokerMessage message) {
        Long tableId = message.getTableId();
        Long playerId = message.getPlayerId();
        
        Table table = tables.get(tableId);
        if (table != null) {
            table.removePlayer(playerId);
            log.info("Player {} left table {}", playerId, tableId);
            
            // 如果房间为空，删除房间
            if (table.getPlayers().isEmpty()) {
                tables.remove(tableId);
                log.info("Table {} removed (empty)", tableId);
            }
        }
    }
    
    /**
     * 获取游戏状态（用于断线重连）
     */
    public PokerMessage getGameState(Long tableId, Long playerId) {
        Table table = tables.get(tableId);
        if (table == null) {
            return null;
        }
        
        Player player = table.getPlayer(playerId);
        if (player == null) {
            return null;
        }
        
        PokerMessage message = PokerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .type(PokerMessage.MessageType.GAME_STATE_UPDATE)
                .timestamp(System.currentTimeMillis())
                .tableId(tableId)
                .playerId(playerId)
                .payload(new java.util.HashMap<>())
                .build();
        
        message.setPayloadField("tableState", table.getCurrentState());
        message.setPayloadField("playerStack", player.getStack());
        message.setPayloadField("totalPot", table.getTotalPot());
        message.setPayloadField("players", table.getPlayers());
        
        return message;
    }
    
    /**
     * 获取房间信息
     */
    public Table getTable(Long tableId) {
        return tables.get(tableId);
    }
    
    /**
     * 获取所有活跃房间
     */
    public java.util.Collection<Table> getAllTables() {
        return tables.values();
    }

    private void engineTickSafely() {
        try {
            engineTick();
        } catch (Exception e) {
            log.warn("engine tick failed", e);
        }
    }

    private synchronized void engineTick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Table> entry : tables.entrySet()) {
            Long tableId = entry.getKey();
            Table table = entry.getValue();

            if (pendingStreetTransitions.containsKey(tableId) && now >= table.getCurrentActionDeadline()) {
                TableState next = pendingStreetTransitions.remove(tableId);
                if (next != null) {
                    applyStreetTransition(table, next);
                }
                continue;
            }

            if (isInActiveStreet(table.getState()) && table.getNextToActSeat() >= 0 && now >= table.getCurrentActionDeadline()) {
                autoFoldCurrentPlayer(table);
                continue;
            }

            if (table.getState() == TableState.SHOWDOWN && now >= table.getCurrentActionDeadline()) {
                if (table.canStartNewHand()) {
                    startGame(tableId);
                } else {
                    table.setState(TableState.WAITING);
                }
            }
        }
    }

    /**
     * REST: 玩家加入房间（简化版）
     */
    public synchronized Table joinTable(Long tableId, String playerId, String nickname, long buyIn) {
        if (tableId == null || playerId == null || nickname == null) {
            throw new IllegalArgumentException("tableId/playerId/nickname cannot be null");
        }
        if (buyIn <= 0) {
            throw new IllegalArgumentException("buyIn must be positive");
        }

        Table table = tables.computeIfAbsent(tableId, id -> {
            Table t = new Table();
            t.setTableId(id);
            t.setState(TableState.WAITING);
            TableConfig cfg = new TableConfig();
            cfg.setMaxPlayers(6);
            cfg.setMinBuyIn(5000);
            cfg.setMaxBuyIn(500000);
            t.setConfig(cfg);
            return t;
        });

        // 已在桌上的玩家重复加入直接返回
        Player existing = table.getPlayers().values().stream()
            .filter(p -> playerId.equals(p.getPlayerId()))
            .findFirst()
            .orElse(null);
        if (existing != null) {
            return table;
        }

        int seat = table.allocateSeat();
        if (seat < 0) {
            throw new IllegalStateException("table is full");
        }

        Player player = new Player();
        try {
            player.setPlayerId(Long.parseLong(playerId));
        } catch (NumberFormatException e) {
            player.setPlayerId(Math.abs((long) playerId.hashCode()));
        }
        player.setNickname(nickname);
        player.setSeatNumber(seat);
        player.setStackSize(buyIn);
        player.setStatus(PlayerStatus.SITTING);

        table.addPlayer(player);
        return table;
    }

    /**
     * REST: 开始游戏（简化版）
     */
    public synchronized Table startGame(Long tableId) {
        Table table = tables.get(tableId);
        if (table == null) {
            throw new IllegalArgumentException("table not found");
        }
        if (isInActiveStreet(table.getState())) {
            throw new IllegalStateException("a hand is already running");
        }
        if (!table.canStartNewHand()) {
            throw new IllegalStateException("at least 2 players required");
        }

        if (table.getConfig() == null) {
            TableConfig cfg = new TableConfig();
            cfg.setMaxPlayers(6);
            cfg.setSmallBlindAmount(500);
            cfg.setBigBlindAmount(1000);
            cfg.setMinBuyIn(5000);
            cfg.setMaxBuyIn(500000);
            table.setConfig(cfg);
        } else {
            if (table.getConfig().getSmallBlindAmount() <= 0) {
                table.getConfig().setSmallBlindAmount(500);
            }
            if (table.getConfig().getBigBlindAmount() <= 0) {
                table.getConfig().setBigBlindAmount(1000);
            }
            if (table.getConfig().getMinBuyIn() <= 0) {
                table.getConfig().setMinBuyIn(5000);
            }
            if (table.getConfig().getMaxBuyIn() <= 0) {
                table.getConfig().setMaxBuyIn(500000);
            }
        }

        Hand hand = new Hand();
        hand.setTableId(table.getTableId());
        hand.setHandId(table.getTableId() + "_" + System.currentTimeMillis());
        hand.setCreatedAt(System.currentTimeMillis());
        hand.setCurrentStreet("PRE_FLOP");

        List<Card> deck = generateShuffledDeck();
        List<Player> playersInOrder = getPlayersInSeatOrder(table);
        List<Player> participants = new ArrayList<>();

        for (Player p : playersInOrder) {
            if (p.getStatus() != PlayerStatus.LEFT && p.getStackSize() > 0) {
                p.resetForNewHand();
                p.setStatus(PlayerStatus.ACTIVE);
                participants.add(p);
            }
        }

        if (participants.size() < 2) {
            throw new IllegalStateException("at least 2 players with chips required");
        }

        assignButtonAndBlinds(table, participants);

        // 按座位顺序轮流发两轮底牌
        for (int round = 0; round < 2; round++) {
            for (Player p : participants) {
                Card card = drawFromDeck(deck);
                Card[] current = hand.getPlayerHoleCards().get(p.getPlayerId());
                if (current == null) {
                    current = new Card[2];
                }
                current[round] = card;
                hand.addPlayerHoleCards(p.getPlayerId(), current);
                p.setHoleCards(current);
            }
        }

        table.setCurrentHand(hand);
        table.setState(TableState.PRE_FLOP);
        table.setCommunityCardsDealt(0);
        table.setTotalPotSize(0);
        table.setLastActivityTime(LocalDateTime.now());
        table.getDealerQueue().clear();
        for (Card card : deck) {
            table.getDealerQueue().add(String.valueOf(card.encode()));
        }
        resetStreetBetState(table);
        postBlinds(table);
        int firstToAct = findPreflopFirstToAct(table);
        table.setNextToActSeat(firstToAct);
        scheduleActionDeadline(table);
        pendingStreetTransitions.remove(table.getTableIdAsLong());

        return table;
    }

    /**
     * REST: 玩家行动（check/call/fold/bet/raise/all_in）
     */
    public synchronized Table playerAction(Long tableId, String playerId, String action, long amount) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId cannot be empty");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action cannot be empty");
        }

        Table table = tables.get(tableId);
        if (table == null) {
            throw new IllegalArgumentException("table not found");
        }
        if (!isInActiveStreet(table.getState())) {
            throw new IllegalStateException("table is not in active hand");
        }

        Player actor = table.getPlayers().values().stream()
            .filter(p -> playerId.equals(p.getPlayerId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("player not found in table"));

        if (actor.getSeatNumber() != table.getNextToActSeat()) {
            throw new IllegalStateException("not your turn");
        }
        if (actor.getStatus() != PlayerStatus.ACTIVE) {
            throw new IllegalStateException("player is not active");
        }

        String normalized = action.trim().toUpperCase();
        String actionForRecord = normalized;
        long toCall = Math.max(0, table.getCurrentBetThisStreet() - actor.getCurrentBet());
        long invested = 0;

        switch (normalized) {
            case "FOLD" -> {
                actor.setStatus(PlayerStatus.FOLDED);
                actor.setHasActed(true);
            }
            case "CHECK" -> {
                if (toCall > 0) {
                    throw new IllegalStateException("cannot check when facing bet");
                }
                actor.setHasActed(true);
            }
            case "CALL" -> {
                if (toCall <= 0) {
                    throw new IllegalStateException("nothing to call");
                }
                invested = invest(table, actor, toCall);
                actor.setHasActed(true);
            }
            case "BET" -> {
                if (table.getCurrentBetThisStreet() > 0) {
                    throw new IllegalStateException("cannot bet after a bet exists, use raise");
                }
                if (amount <= 0) {
                    throw new IllegalArgumentException("bet amount must be positive");
                }
                long before = actor.getCurrentBet();
                invested = invest(table, actor, amount);
                if (actor.getCurrentBet() <= before) {
                    throw new IllegalStateException("invalid bet");
                }
                table.setCurrentBetThisStreet(actor.getCurrentBet());
                actor.setHasActed(true);
                markOthersNeedToAct(table, actor.getPlayerId());
            }
            case "RAISE" -> {
                if (amount <= 0) {
                    throw new IllegalArgumentException("raise amount must be positive");
                }
                // 若本轮无人下注，前端点了 RAISE 也按 BET 处理，避免报错卡死。
                if (table.getCurrentBetThisStreet() <= 0) {
                    long before = actor.getCurrentBet();
                    invested = invest(table, actor, amount);
                    if (actor.getCurrentBet() <= before) {
                        throw new IllegalStateException("invalid bet");
                    }
                    table.setCurrentBetThisStreet(actor.getCurrentBet());
                    actor.setHasActed(true);
                    markOthersNeedToAct(table, actor.getPlayerId());
                    actionForRecord = "BET";
                } else {
                    long required = toCall + amount;
                    long before = actor.getCurrentBet();
                    invested = invest(table, actor, required);
                    long minTotalToRaise = table.getCurrentBetThisStreet() * 2;
                    if (actor.getStatus() != PlayerStatus.ALL_IN && actor.getCurrentBet() < minTotalToRaise) {
                        throw new IllegalStateException("raise too small");
                    }
                    if (actor.getCurrentBet() > table.getCurrentBetThisStreet()) {
                        table.setCurrentBetThisStreet(actor.getCurrentBet());
                        markOthersNeedToAct(table, actor.getPlayerId());
                    }
                    actor.setHasActed(true);
                }
            }
            case "ALL_IN" -> {
                if (actor.getStackSize() <= 0) {
                    throw new IllegalStateException("no chips for all-in");
                }
                long before = actor.getCurrentBet();
                invested = invest(table, actor, actor.getStackSize());
                if (actor.getCurrentBet() > table.getCurrentBetThisStreet()) {
                    table.setCurrentBetThisStreet(actor.getCurrentBet());
                    markOthersNeedToAct(table, actor.getPlayerId());
                } else if (actor.getCurrentBet() <= before) {
                    throw new IllegalStateException("invalid all-in");
                }
                actor.setHasActed(true);
            }
            default -> throw new IllegalArgumentException("unknown action: " + action);
        }

        actor.setLastAction(new PlayerAction(
            System.currentTimeMillis(),
            actionForRecord,
            amount,
            table.getState().name(),
            invested
        ));
        progressHand(table, actor.getSeatNumber());
        table.setLastActivityTime(LocalDateTime.now());
        return table;
    }

    public synchronized Table rebuy(Long tableId, String playerId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("rebuy amount must be positive");
        }
        Table table = tables.get(tableId);
        if (table == null) {
            throw new IllegalArgumentException("table not found");
        }
        if (table.getState() != TableState.WAITING && table.getState() != TableState.SHOWDOWN) {
            throw new IllegalStateException("rebuy only allowed between hands");
        }
        Player player = table.getPlayers().values().stream()
            .filter(p -> playerId.equals(p.getPlayerId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("player not found in table"));

        long min = table.getConfig() != null ? table.getConfig().getMinBuyIn() : 5000;
        long max = table.getConfig() != null ? table.getConfig().getMaxBuyIn() : 500000;
        if (amount < min || amount > max) {
            throw new IllegalArgumentException("rebuy amount out of range");
        }
        if (player.getStackSize() > 0) {
            throw new IllegalStateException("rebuy only allowed when stack is zero");
        }
        player.addStack(amount);
        player.setStatus(PlayerStatus.SITTING);
        return table;
    }

    /**
     * 面向前端的状态视图（隐藏其他玩家底牌）
     */
    public synchronized Map<String, Object> getTableView(Long tableId, String viewerPlayerId) {
        Table table = tables.get(tableId);
        if (table == null) {
            return null;
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tableId", table.getTableIdAsLong());
        root.put("state", table.getState());
        root.put("currentBetThisStreet", table.getCurrentBetThisStreet());
        root.put("totalPotSize", table.getTotalPotSize());
        root.put("nextToActSeat", table.getNextToActSeat());
        root.put("actionDeadline", table.getCurrentActionDeadline());
        root.put("buttonSeat", table.getButtonSeat());
        root.put("smallBlindSeat", table.getSmallBlindSeat());
        root.put("bigBlindSeat", table.getBigBlindSeat());
        root.put("smallBlindAmount", table.getConfig() != null ? table.getConfig().getSmallBlindAmount() : 500);
        root.put("bigBlindAmount", table.getConfig() != null ? table.getConfig().getBigBlindAmount() : 1000);
        root.put("potBreakdown", buildPotBreakdownView(table));

        Map<Integer, Object> players = new LinkedHashMap<>();
        getPlayersInSeatOrder(table).forEach(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("playerId", p.getPlayerId());
            item.put("nickname", p.getNickname());
            item.put("seatNumber", p.getSeatNumber());
            item.put("stackSize", p.getStackSize());
            item.put("status", p.getStatus());
            item.put("currentBet", p.getCurrentBet());
            item.put("isDealer", p.isButtonAndDealer());
            item.put("isSmallBlind", p.isSmallBlind());
            item.put("isBigBlind", p.isBigBlind());
            item.put("lastAction", p.getLastAction());
            players.put(p.getSeatNumber(), item);
        });
        root.put("players", players);

        if (table.getCurrentHand() != null) {
            Hand hand = table.getCurrentHand();
            Map<String, Object> handView = new LinkedHashMap<>();
            handView.put("handId", hand.getHandId());
            handView.put("currentStreet", hand.getCurrentStreet());
            handView.put("communityCards", hand.getCommunityCards());

            Map<String, Object> holeCards = new LinkedHashMap<>();
            if (viewerPlayerId != null && hand.getPlayerHoleCards().containsKey(viewerPlayerId)) {
                holeCards.put(viewerPlayerId, hand.getPlayerHoleCards().get(viewerPlayerId));
            }
            if (table.getState() == TableState.SHOWDOWN || (isInActiveStreet(table.getState()) && countActionablePlayers(table) <= 1)) {
                hand.getPlayerHoleCards().forEach((pid, cards) -> {
                    Player p = table.getPlayers().values().stream()
                        .filter(pl -> pid.equals(pl.getPlayerId()))
                        .findFirst()
                        .orElse(null);
                    if (p != null && p.getStatus() != PlayerStatus.FOLDED) {
                        holeCards.put(pid, cards);
                    }
                });
            }
            handView.put("playerHoleCards", holeCards);
            handView.put("pots", hand.getPots());
            handView.put("distributions", hand.getDistributions());
            root.put("currentHand", handView);
        } else {
            root.put("currentHand", null);
        }

        return root;
    }

    private void progressHand(Table table, int actedSeat) {
        List<Player> inHand = getPlayersInHand(table);
        if (inHand.size() <= 1) {
            settleWithoutShowdown(table, inHand.isEmpty() ? null : inHand.get(0));
            return;
        }

        // 只有在“本轮已不需要任何玩家再做决定”时，才自动跑后续公共牌。
        if (shouldSkipBettingRound(table)) {
            scheduleStreetTransition(table, nextStreet(table.getState()));
            return;
        }

        if (isStreetCompleted(table)) {
            scheduleStreetTransition(table, nextStreet(table.getState()));
            return;
        }

        int next = findNextActorSeat(table, actedSeat);
        if (next < 0) {
            scheduleStreetTransition(table, nextStreet(table.getState()));
            return;
        }
        table.setNextToActSeat(next);
        scheduleActionDeadline(table);
    }

    private boolean isStreetCompleted(Table table) {
        long target = table.getCurrentBetThisStreet();
        for (Player p : getPlayersInHand(table)) {
            if (p.getStatus() != PlayerStatus.ACTIVE) {
                continue;
            }
            if (!p.hasActed()) {
                return false;
            }
            if (p.getCurrentBet() < target) {
                return false;
            }
        }
        return true;
    }

    private void applyStreetTransition(Table table, TableState targetState) {
        if (targetState == null) {
            showdown(table);
            return;
        }

        switch (targetState) {
            case FLOP -> {
                dealFlop(table);
                table.setState(TableState.FLOP);
                table.getCurrentHand().setCurrentStreet("FLOP");
                resetStreetBetState(table);
                int next = findFirstActorSeat(table);
                table.setNextToActSeat(next);
                if (next < 0 || shouldSkipBettingRound(table)) {
                    scheduleStreetTransition(table, nextStreet(table.getState()));
                } else {
                    scheduleActionDeadline(table);
                }
            }
            case TURN -> {
                dealTurn(table);
                table.setState(TableState.TURN);
                table.getCurrentHand().setCurrentStreet("TURN");
                resetStreetBetState(table);
                int next = findFirstActorSeat(table);
                table.setNextToActSeat(next);
                if (next < 0 || shouldSkipBettingRound(table)) {
                    scheduleStreetTransition(table, nextStreet(table.getState()));
                } else {
                    scheduleActionDeadline(table);
                }
            }
            case RIVER -> {
                dealRiver(table);
                table.setState(TableState.RIVER);
                table.getCurrentHand().setCurrentStreet("RIVER");
                resetStreetBetState(table);
                int next = findFirstActorSeat(table);
                table.setNextToActSeat(next);
                if (next < 0 || shouldSkipBettingRound(table)) {
                    scheduleStreetTransition(table, nextStreet(table.getState()));
                } else {
                    scheduleActionDeadline(table);
                }
            }
            case SHOWDOWN -> showdown(table);
            default -> {
            }
        }
    }

    private void showdown(Table table) {
        Hand hand = table.getCurrentHand();
        if (hand == null) {
            throw new IllegalStateException("hand not initialized");
        }

        ensureFiveCommunityCards(table);
        List<Player> contenders = getPlayersInHand(table);
        if (contenders.isEmpty()) {
            table.setState(TableState.WAITING);
            return;
        }

        Map<String, HandRank> rankMap = new LinkedHashMap<>();

        for (Player player : contenders) {
            Card[] hole = hand.getPlayerHoleCards().get(player.getPlayerId());
            if (hole == null || hole.length != 2) {
                continue;
            }
            Card[] seven = new Card[7];
            seven[0] = hole[0];
            seven[1] = hole[1];
            System.arraycopy(hand.getCommunityCards(), 0, seven, 2, 5);
            HandRank rank = HandEvaluator.evaluateSevenCards(seven);
            rankMap.put(player.getPlayerId(), rank);
        }

        if (rankMap.isEmpty()) {
            table.setState(TableState.SHOWDOWN);
            hand.setCurrentStreet("SHOWDOWN");
            return;
        }

        hand.setPlayerRanks(rankMap);
        distributePotsWithSidePot(table, hand, contenders, rankMap);
        table.setState(TableState.SHOWDOWN);
        hand.setCurrentStreet("SHOWDOWN");
        table.setNextToActSeat(-1);
        table.setCurrentBetThisStreet(0);
        table.setCurrentActionDeadline(System.currentTimeMillis() + NEXT_HAND_DELAY_MS);
    }

    private void distributePotsWithSidePot(Table table, Hand hand, List<Player> contenders, Map<String, HandRank> rankMap) {
        List<PotSlice> slices = buildPotSlices(table);
        if (slices.isEmpty()) {
            hand.setDistributions(Collections.emptyList());
            hand.setPots(Collections.emptyList());
            table.setTotalPotSize(0);
            return;
        }

        List<Pot> potModels = new ArrayList<>();
        Map<String, Long> winByPlayer = new LinkedHashMap<>();
        int potSeq = 1;

        for (PotSlice slice : slices) {
            potModels.add(new Pot(
                potSeq++,
                slice.amount,
                new HashSet<>(slice.eligiblePlayerIds),
                0
            ));

            List<Player> eligible = contenders.stream()
                .filter(p -> slice.eligiblePlayerIds.contains(p.getPlayerId()))
                .toList();
            if (eligible.isEmpty()) {
                continue;
            }

            int bestRank = Integer.MAX_VALUE;
            List<Player> winners = new ArrayList<>();
            for (Player p : eligible) {
                HandRank rank = rankMap.get(p.getPlayerId());
                if (rank == null) {
                    continue;
                }
                if (rank.getRankValue() < bestRank) {
                    bestRank = rank.getRankValue();
                    winners.clear();
                    winners.add(p);
                } else if (rank.getRankValue() == bestRank) {
                    winners.add(p);
                }
            }
            if (winners.isEmpty()) {
                continue;
            }

            winners.sort(Comparator.comparingInt(Player::getSeatNumber));
            long share = slice.amount / winners.size();
            long remainder = slice.amount % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                Player w = winners.get(i);
                long won = share + (i < remainder ? 1 : 0);
                winByPlayer.merge(w.getPlayerId(), won, Long::sum);
            }
        }

        List<PotDistribution> distributions = new ArrayList<>();
        for (Player p : getPlayersInSeatOrder(table)) {
            long won = winByPlayer.getOrDefault(p.getPlayerId(), 0L);
            if (won <= 0) {
                continue;
            }
            p.addStack(won);
            String reason = rankMap.get(p.getPlayerId()) != null
                ? rankMap.get(p.getPlayerId()).getSimpleDescription()
                : "winner";
            distributions.add(new PotDistribution(p.getPlayerId(), won, 1, reason));
        }

        hand.setPots(potModels);
        hand.setDistributions(distributions);
        table.setTotalPotSize(0);
    }

    private void settleWithoutShowdown(Table table, Player winner) {
        Hand hand = table.getCurrentHand();
        if (winner != null && table.getTotalPotSize() > 0) {
            winner.addStack(table.getTotalPotSize());
            if (hand != null) {
                List<PotDistribution> distributions = new ArrayList<>();
                distributions.add(new PotDistribution(winner.getPlayerId(), table.getTotalPotSize(), 1, "all others folded"));
                hand.setDistributions(distributions);
                hand.setCurrentStreet("SHOWDOWN");
            }
            table.setTotalPotSize(0);
        }
        table.setState(TableState.SHOWDOWN);
        table.setCurrentBetThisStreet(0);
        table.setNextToActSeat(-1);
        table.setCurrentActionDeadline(System.currentTimeMillis() + NEXT_HAND_DELAY_MS);
    }

    private void ensureFiveCommunityCards(Table table) {
        while (table.getCommunityCardsDealt() < 5) {
            if (table.getCommunityCardsDealt() < 3) {
                dealFlop(table);
            } else if (table.getCommunityCardsDealt() == 3) {
                dealTurn(table);
            } else {
                dealRiver(table);
            }
        }
    }

    private void dealFlop(Table table) {
        burnCard(table);
        Card[] flop = new Card[] { drawNextCard(table), drawNextCard(table), drawNextCard(table) };
        Hand hand = table.getCurrentHand();
        hand.setCommunityCards(flop);
        table.setCommunityCardsDealt(3);
    }

    private void dealTurn(Table table) {
        burnCard(table);
        Hand hand = table.getCurrentHand();
        Card[] cards = hand.getCommunityCards().clone();
        cards[3] = drawNextCard(table);
        hand.setCommunityCards(cards);
        table.setCommunityCardsDealt(4);
    }

    private void dealRiver(Table table) {
        burnCard(table);
        Hand hand = table.getCurrentHand();
        Card[] cards = hand.getCommunityCards().clone();
        cards[4] = drawNextCard(table);
        hand.setCommunityCards(cards);
        table.setCommunityCardsDealt(5);
    }

    private void burnCard(Table table) {
        Queue<String> q = table.getDealerQueue();
        if (q.poll() == null) {
            throw new IllegalStateException("deck exhausted on burn");
        }
    }

    private Card drawNextCard(Table table) {
        String encoded = table.getDealerQueue().poll();
        if (encoded == null) {
            throw new IllegalStateException("deck exhausted");
        }
        return Card.decode(Integer.parseInt(encoded));
    }

    private void resetStreetBetState(Table table) {
        table.setCurrentBetThisStreet(0);
        for (Player p : table.getPlayers().values()) {
            p.setCurrentBet(0);
            p.setBetThisStreet(0);
            if (p.getStatus() == PlayerStatus.ACTIVE) {
                p.setHasActed(false);
            }
        }
    }

    private int findFirstActorSeat(Table table) {
        List<Player> actionables = getPlayersInSeatOrder(table).stream()
            .filter(this::isActionable)
            .toList();
        if (actionables.isEmpty()) {
            return -1;
        }
        return findNextOccupiedSeat(actionables, table.getButtonSeat());
    }

    private int findNextActorSeat(Table table, int currentSeat) {
        int maxPlayers = table.getConfig() != null ? table.getConfig().getMaxPlayers() : 6;
        for (int i = 1; i <= maxPlayers; i++) {
            int seat = (currentSeat + i) % maxPlayers;
            Player p = table.getPlayers().get(seat);
            if (p != null && isActionable(p)) {
                return seat;
            }
        }
        return -1;
    }

    private boolean isActionable(Player p) {
        return p.getStatus() == PlayerStatus.ACTIVE && p.getStackSize() > 0;
    }

    private int countActionablePlayers(Table table) {
        int count = 0;
        for (Player p : table.getPlayers().values()) {
            if (isActionable(p)) {
                count++;
            }
        }
        return count;
    }

    private boolean shouldSkipBettingRound(Table table) {
        List<Player> actionables = getPlayersInSeatOrder(table).stream()
            .filter(this::isActionable)
            .toList();

        if (actionables.isEmpty()) {
            return true;
        }
        if (actionables.size() >= 2) {
            return false;
        }

        // 仅剩1个可行动玩家时：只有他已经跟到当前最大注额，才允许跳过下注轮。
        Player lone = actionables.get(0);
        long maxBet = table.getCurrentBetThisStreet();
        return lone.getCurrentBet() >= maxBet;
    }

    private List<PotSlice> buildPotSlices(Table table) {
        List<Player> contributors = table.getPlayers().values().stream()
            .filter(p -> p.getTotalBetInPot() > 0)
            .toList();
        if (contributors.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> levels = contributors.stream()
            .map(Player::getTotalBetInPot)
            .distinct()
            .sorted()
            .toList();

        List<PotSlice> slices = new ArrayList<>();
        long previous = 0;
        for (Long level : levels) {
            long increment = level - previous;
            if (increment <= 0) {
                previous = level;
                continue;
            }

            List<Player> levelContributors = contributors.stream()
                .filter(p -> p.getTotalBetInPot() >= level)
                .toList();
            long amount = increment * levelContributors.size();
            if (amount <= 0) {
                previous = level;
                continue;
            }

            List<String> eligible = levelContributors.stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.LEFT)
                .map(Player::getPlayerId)
                .toList();
            slices.add(new PotSlice(amount, eligible));
            previous = level;
        }
        return slices;
    }

    private List<Map<String, Object>> buildPotBreakdownView(Table table) {
        List<PotSlice> slices = buildPotSlices(table);
        if (slices.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> view = new ArrayList<>();
        for (int i = 0; i < slices.size(); i++) {
            PotSlice s = slices.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", i == 0 ? "主池" : "边池" + i);
            item.put("amount", s.amount);
            item.put("eligibleCount", s.eligiblePlayerIds.size());
            view.add(item);
        }
        return view;
    }

    private static class PotSlice {
        private final long amount;
        private final List<String> eligiblePlayerIds;

        private PotSlice(long amount, List<String> eligiblePlayerIds) {
            this.amount = amount;
            this.eligiblePlayerIds = eligiblePlayerIds;
        }
    }

    private List<Player> getPlayersInHand(Table table) {
        return getPlayersInSeatOrder(table).stream()
            .filter(p -> p.getStatus() == PlayerStatus.ACTIVE || p.getStatus() == PlayerStatus.ALL_IN)
            .toList();
    }

    private List<Player> getPlayersInSeatOrder(Table table) {
        return table.getPlayers().values().stream()
            .sorted(Comparator.comparingInt(Player::getSeatNumber))
            .toList();
    }

    private boolean isInActiveStreet(TableState state) {
        return state == TableState.PRE_FLOP || state == TableState.FLOP || state == TableState.TURN || state == TableState.RIVER;
    }

    private void scheduleActionDeadline(Table table) {
        if (!isInActiveStreet(table.getState()) || table.getNextToActSeat() < 0) {
            table.setCurrentActionDeadline(0);
            return;
        }
        table.setCurrentActionDeadline(System.currentTimeMillis() + ACTION_TIMEOUT_MS);
    }

    private void scheduleStreetTransition(Table table, TableState target) {
        if (target == null) {
            showdown(table);
            return;
        }
        pendingStreetTransitions.put(table.getTableIdAsLong(), target);
        table.setNextToActSeat(-1);
        table.setCurrentActionDeadline(System.currentTimeMillis() + STREET_TRANSITION_DELAY_MS);
    }

    private TableState nextStreet(TableState current) {
        return switch (current) {
            case PRE_FLOP -> TableState.FLOP;
            case FLOP -> TableState.TURN;
            case TURN -> TableState.RIVER;
            case RIVER -> TableState.SHOWDOWN;
            default -> null;
        };
    }

    private void autoFoldCurrentPlayer(Table table) {
        Player actor = table.getPlayers().get(table.getNextToActSeat());
        if (actor == null || actor.getStatus() != PlayerStatus.ACTIVE) {
            int next = findFirstActorSeat(table);
            table.setNextToActSeat(next);
            scheduleActionDeadline(table);
            return;
        }
        actor.setStatus(PlayerStatus.FOLDED);
        actor.setHasActed(true);
        actor.setLastAction(new PlayerAction(
            System.currentTimeMillis(),
            "FOLD",
            0,
            table.getState().name(),
            0
        ));
        progressHand(table, actor.getSeatNumber());
    }

    private void assignButtonAndBlinds(Table table, List<Player> participants) {
        participants.forEach(p -> {
            p.setButtonAndDealer(false);
            p.setSmallBlind(false);
            p.setBigBlind(false);
        });

        int previousButton = table.getButtonSeat();
        int buttonSeat = findNextOccupiedSeat(participants, previousButton);
        table.setButtonSeat(buttonSeat);
        Player button = findPlayerBySeat(participants, buttonSeat);
        if (button != null) {
            button.setButtonAndDealer(true);
        }

        if (participants.size() == 2) {
            table.setSmallBlindSeat(buttonSeat);
            int bbSeat = findNextOccupiedSeat(participants, buttonSeat);
            table.setBigBlindSeat(bbSeat);
        } else {
            int sbSeat = findNextOccupiedSeat(participants, buttonSeat);
            int bbSeat = findNextOccupiedSeat(participants, sbSeat);
            table.setSmallBlindSeat(sbSeat);
            table.setBigBlindSeat(bbSeat);
        }

        Player sb = findPlayerBySeat(participants, table.getSmallBlindSeat());
        Player bb = findPlayerBySeat(participants, table.getBigBlindSeat());
        if (sb != null) {
            sb.setSmallBlind(true);
        }
        if (bb != null) {
            bb.setBigBlind(true);
        }
    }

    private void postBlinds(Table table) {
        Player sb = table.getPlayers().get(table.getSmallBlindSeat());
        Player bb = table.getPlayers().get(table.getBigBlindSeat());

        long sbAmount = table.getConfig().getSmallBlindAmount();
        long bbAmount = table.getConfig().getBigBlindAmount();
        long postedSb = 0;
        long postedBb = 0;
        if (sb != null && sb.getStatus() == PlayerStatus.ACTIVE) {
            postedSb = invest(table, sb, sbAmount);
        }
        if (bb != null && bb.getStatus() == PlayerStatus.ACTIVE) {
            postedBb = invest(table, bb, bbAmount);
        }
        table.setCurrentBetThisStreet(Math.max(postedBb, postedSb));
    }

    private int findPreflopFirstToAct(Table table) {
        List<Player> participants = getPlayersInSeatOrder(table).stream()
            .filter(p -> p.getStatus() == PlayerStatus.ACTIVE || p.getStatus() == PlayerStatus.ALL_IN)
            .toList();
        if (participants.size() == 2) {
            return table.getSmallBlindSeat();
        }
        return findNextOccupiedSeat(participants, table.getBigBlindSeat());
    }

    private int findNextOccupiedSeat(List<Player> players, int fromSeat) {
        if (players.isEmpty()) {
            return -1;
        }
        int maxPlayers = 6;
        for (Player p : players) {
            maxPlayers = Math.max(maxPlayers, p.getSeatNumber() + 1);
        }
        for (int i = 1; i <= maxPlayers; i++) {
            int seat = Math.floorMod(fromSeat + i, maxPlayers);
            boolean exists = players.stream().anyMatch(p -> p.getSeatNumber() == seat);
            if (exists) {
                return seat;
            }
        }
        return players.get(0).getSeatNumber();
    }

    private Player findPlayerBySeat(List<Player> players, int seat) {
        return players.stream().filter(p -> p.getSeatNumber() == seat).findFirst().orElse(null);
    }

    private long invest(Table table, Player player, long requested) {
        long amount = Math.max(0, Math.min(requested, player.getStackSize()));
        if (amount <= 0) {
            throw new IllegalStateException("insufficient stack");
        }
        player.deductStack(amount);
        player.setCurrentBet(player.getCurrentBet() + amount);
        player.setBetThisStreet(player.getBetThisStreet() + amount);
        if (player.getStackSize() == 0) {
            player.setStatus(PlayerStatus.ALL_IN);
        }
        table.setTotalPotSize(table.getTotalPotSize() + amount);
        return amount;
    }

    private void markOthersNeedToAct(Table table, String actorPlayerId) {
        for (Player p : table.getPlayers().values()) {
            if (p.getStatus() == PlayerStatus.ACTIVE && !p.getPlayerId().equals(actorPlayerId)) {
                p.setHasActed(false);
            }
        }
    }

    private List<Card> generateShuffledDeck() {
        List<Card> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(Card.of(suit, rank));
            }
        }
        SecureRandom random = new SecureRandom();
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card tmp = cards.get(i);
            cards.set(i, cards.get(j));
            cards.set(j, tmp);
        }
        return cards;
    }

    private Card drawFromDeck(List<Card> deck) {
        if (deck.isEmpty()) {
            throw new IllegalStateException("deck exhausted");
        }
        return deck.remove(0);
    }
}
