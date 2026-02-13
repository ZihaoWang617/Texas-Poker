package com.wepoker.domain.model;

import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 房间状态机
 * State transitions:
 * WAITING -> DEALING -> PRE_FLOP -> FLOP -> TURN -> RIVER -> SHOWDOWN -> CLEANUP -> WAITING
 */
public enum TableState {
    WAITING,        // 等待玩家满坐
    DEALING,        // 正在发牌
    PRE_FLOP,       // 前翻
    FLOP,           // 翻牌
    TURN,           // 转牌
    RIVER,          // 河牌
    SHOWDOWN,       // 比牌
    CLEANUP,        // 清算
    CLOSED          // 房桌关闭
}

/**
 * Game Config - 桌位的静态配置
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TableConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private int tableId;
    private String tableName;
    private int maxPlayers;              // 2, 6, 9人
    private long smallBlindAmount;       // 最小分值单位
    private long bigBlindAmount;
    private long minBuyIn;               // 最小买入 (50BB)
    private long maxBuyIn;               // 最大买入 (200BB)
    private double rakePercentage;       // 抽水比例 (如0.05 = 5%)
    private long rakeMaxPerHand;         // 每手最多抽水多少
    private int timeToAct;              // 玩家行动时限(秒) - 如15秒
    private int timeBank;               // 延时时间(秒) - 如30秒
    private boolean runItTwiceAllowed;  // 是否允许Run It Twice
    private LocalDateTime createdAt;
}

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
        if (nextToActSeat >= 0) {
            // 从nextToActSeat开始查找，跳过已弃牌的玩家
            for (int i = 0; i < config.getMaxPlayers(); i++) {
                int seat = (nextToActSeat + i) % config.getMaxPlayers();
                Player p = players.get(seat);
                if (p != null && p.isInHand() && !p.isAllIn() && !p.hasActed()) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * 玩家加入房间
     */
    public void addPlayer(Player player) throws IllegalStateException {
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
        switch (state) {
            case PRE_FLOP -> setState(TableState.FLOP);
            case FLOP -> setState(TableState.TURN);
            case TURN -> setState(TableState.RIVER);
            case RIVER -> setState(TableState.SHOWDOWN);
            case SHOWDOWN -> setState(TableState.CLEANUP);
            case CLEANUP -> setState(TableState.WAITING);
            default -> {}
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
}
