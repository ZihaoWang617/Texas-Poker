package com.wepoker.domain.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 德州扑克房间/桌位
 */
@Getter
@Setter
@ToString(exclude = {"players", "currentHand"})
public class Table implements Serializable {
    private static final long serialVersionUID = 1L;

    private int tableId;
    private TableConfig config;
    private TableState state;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityTime;

    // 座位管理
    private Map<Integer, Player> players;  // seatNumber -> Player
    private int smallBlindSeat;
    private int bigBlindSeat;
    private int buttonSeat;
    private int nextToActSeat;            // 当前需要行动的座位

    // 当前手牌信息
    private Hand currentHand;
    private Queue<String> dealerQueue;    // 洗牌算法队列（预生成的牌）

    // 底池相关
    private long currentBetThisStreet;    // 该街最高bet额
    private long totalPotSize;            // 所有pot的总和

    // 游戏进度
    private int handCount;                // 已进行的手数
    private int communityCardsDealt;      // 已发的公共牌数

    // 时间控制
    private long currentActionDeadline;   // 当前玩家的行动截止时间戳
    private int timeBankUsedCount;        // 本轮已使用的time bank次数

    // 性能/连接监控
    private Map<String, Long> playerLastHeartbeat;  // 玩家最后心跳时间
    private Map<String, String> sessionIds;         // playerId -> sessionId

    public Table() {
        this.players = new HashMap<>();
        this.dealerQueue = new LinkedList<>();
        this.playerLastHeartbeat = new HashMap<>();
        this.sessionIds = new HashMap<>();
    }

    /**
     * 检查是否可以开始新手牌
     */
    public boolean canStartNewHand() {
        long activePlayers = players.values().stream()
            .filter(p -> p.getStatus() != PlayerStatus.LEFT && p.getStackSize() > 0)
            .count();
        return activePlayers >= 2;  // 至少2个玩家
    }

    /**
     * 获取活跃玩家列表
     */
    public List<Player> getActivePlayers() {
        return players.values().stream()
            .filter(p -> p.getStatus() == PlayerStatus.SITTING || p.getStatus() == PlayerStatus.ACTIVE)
            .sorted(Comparator.comparingInt(Player::getSeatNumber))
            .toList();
    }

    /**
     * 获取下一个需要行动的玩家
     */
    public Player getNextPlayerToAct() {
        if (config == null || nextToActSeat < 0) {
            return null;
        }

        for (int i = 0; i < config.getMaxPlayers(); i++) {
            int seat = (nextToActSeat + i) % config.getMaxPlayers();
            Player p = players.get(seat);
            if (p != null && p.isInHand() && !p.isAllIn() && !p.isHasActed()) {
                return p;
            }
        }
        return null;
    }

    /**
     * 玩家加入房间
     */
    public void addPlayer(Player player) throws IllegalStateException {
        if (config == null) {
            throw new IllegalStateException("Table config not initialized");
        }
        if (players.size() >= config.getMaxPlayers()) {
            throw new IllegalStateException("Table is full");
        }
        if (player.getStackSize() < config.getMinBuyIn()) {
            throw new IllegalStateException("Buy-in amount too low");
        }
        players.put(player.getSeatNumber(), player);
    }

    /**
     * 玩家离席
     */
    public void removePlayer(String playerId) {
        players.values().stream()
            .filter(p -> p.getPlayerId().equals(playerId))
            .findFirst()
            .ifPresent(p -> p.setStatus(PlayerStatus.LEFT));
    }

    /**
     * 移动按钮（dealer）
     */
    public void moveButton() {
        if (config == null) {
            return;
        }
        int nextButton = (buttonSeat + 1) % config.getMaxPlayers();
        // 找到下一个有效座位
        for (int i = 0; i < config.getMaxPlayers(); i++) {
            int seat = (nextButton + i) % config.getMaxPlayers();
            if (players.containsKey(seat)) {
                this.buttonSeat = seat;
                break;
            }
        }
        updateBlindPositions();
    }

    /**
     * 基于Button位置更新小盲注、大盲注位置
     */
    private void updateBlindPositions() {
        if (config == null) {
            return;
        }
        if (config.getMaxPlayers() == 2) {
            // Head-up: button is small blind
            this.smallBlindSeat = buttonSeat;
            this.bigBlindSeat = (buttonSeat + 1) % 2;
        } else {
            // 多人游戏：button左边是small blind，再左边是big blind
            this.smallBlindSeat = (buttonSeat + 1) % config.getMaxPlayers();
            this.bigBlindSeat = (buttonSeat + 2) % config.getMaxPlayers();
        }
    }

    /**
     * 状态转移到下一个街
     */
    public void moveToNextStreet() {
        if (state == null) {
            return;
        }
        switch (state) {
            case PRE_FLOP -> setState(TableState.FLOP);
            case FLOP -> setState(TableState.TURN);
            case TURN -> setState(TableState.RIVER);
            case RIVER -> setState(TableState.SHOWDOWN);
            case SHOWDOWN -> setState(TableState.CLEANUP);
            case CLEANUP -> setState(TableState.WAITING);
            default -> {
            }
        }
    }

    /**
     * 重置房间为待机状态
     */
    public void resetForNewHand() {
        this.state = TableState.WAITING;
        this.currentBetThisStreet = 0;
        this.nextToActSeat = -1;
        this.communityCardsDealt = 0;
        this.timeBankUsedCount = 0;

        players.values().forEach(Player::resetForNewHand);

        if (currentHand != null) {
            this.handCount++;
        }
        this.currentHand = null;
    }

    // Compatibility helpers for service layer fields using long ids and naming aliases.
    public void setTableId(Long tableId) {
        this.tableId = tableId == null ? 0 : tableId.intValue();
    }

    public Long getTableIdAsLong() {
        return (long) this.tableId;
    }

    public TableState getCurrentState() {
        return this.state;
    }

    public long getTotalPot() {
        return this.totalPotSize;
    }

    public void setMaxPlayers(int maxPlayers) {
        if (this.config == null) {
            this.config = new TableConfig();
        }
        this.config.setMaxPlayers(maxPlayers);
    }

    public int allocateSeat() {
        int max = this.config != null ? this.config.getMaxPlayers() : 6;
        for (int seat = 0; seat < max; seat++) {
            if (!players.containsKey(seat)) {
                return seat;
            }
        }
        return -1;
    }

    public void removePlayer(Long playerId) {
        if (playerId == null) {
            return;
        }
        removePlayer(String.valueOf(playerId));
    }

    public Player getPlayer(Long playerId) {
        if (playerId == null) {
            return null;
        }
        String pid = String.valueOf(playerId);
        return players.values().stream()
            .filter(p -> pid.equals(p.getPlayerId()))
            .findFirst()
            .orElse(null);
    }
}
