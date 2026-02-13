package com.wepoker.service;

import com.wepoker.domain.model.Player;
import com.wepoker.domain.model.Table;
import com.wepoker.domain.service.GameStateMachine;
import com.wepoker.network.protocol.PokerMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏业务逻辑服务
 */
@Slf4j
@Service
public class GameService {
    
    // 游戏中的所有房间
    private final ConcurrentHashMap<Long, Table> tables = new ConcurrentHashMap<>();
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private GameStateMachine gameStateMachine;
    
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
        gameStateMachine.processPlayerAction(table, player, action, amount);
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
}
