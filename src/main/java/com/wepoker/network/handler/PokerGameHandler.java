package com.wepoker.network.handler;

import com.wepoker.network.protocol.PokerMessage;
import com.wepoker.service.GameService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty 游戏主处理器
 * 
 * 负责：
 * - 连接管理和断线重连
 * - 消息分发
 * - 心跳和超时检测
 */
@Slf4j
@Component
public class PokerGameHandler extends ChannelInboundHandlerAdapter {
    
    // 静态存储所有活跃连接
    private static final ConcurrentHashMap<String, Channel> ACTIVE_CHANNELS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, String> PLAYER_SESSION_MAP = new ConcurrentHashMap<>();
    
    @Autowired
    private GameService gameService;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel active: {}", ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof PokerMessage message)) {
            ctx.fireChannelRead(msg);
            return;
        }
        
        log.debug("Received message: {}", message);
        
        try {
            // 处理不同的消息类型
            switch (message.getType()) {
                // 连接握手
                case HANDSHAKE:
                    handleHandshake(ctx, message);
                    break;
                
                // 断线重连
                case RECONNECT:
                    handleReconnect(ctx, message);
                    break;
                
                // 心跳
                case HEARTBEAT:
                    sendHeartbeatAck(ctx, message);
                    break;
                
                // 游戏操作
                case JOIN_TABLE:
                    gameService.handleJoinTable(message);
                    break;
                
                case BET:
                case RAISE:
                case CALL:
                case CHECK:
                case FOLD:
                case ALL_IN:
                    gameService.handleAction(message);
                    break;
                
                case LEAVE_TABLE:
                    gameService.handleLeaveTable(message);
                    break;
                
                default:
                    log.warn("Unknown message type: {}", message.getType());
            }
            
        } catch (Exception e) {
            log.error("Error processing message: {}", message, e);
            sendError(ctx, message, "PROCESS_ERROR", e.getMessage());
        }
    }
    
    /**
     * 处理握手请求 - 建立会话
     */
    private void handleHandshake(ChannelHandlerContext ctx, PokerMessage request) {
        String sessionId = generateSessionId();
        Long playerId = request.getPlayerId();
        
        // 记录映射
        ACTIVE_CHANNELS.put(sessionId, ctx.channel());
        PLAYER_SESSION_MAP.put(playerId, sessionId);
        
        // 返回握手响应
        PokerMessage response = PokerMessage.builder()
                .messageId(generateMessageId())
                .type(PokerMessage.MessageType.ACK)
                .timestamp(System.currentTimeMillis())
                .sessionId(sessionId)
                .playerId(playerId)
                .payload(new java.util.HashMap<>())
                .build();
        
        response.setPayloadField("status", "CONNECTED");
        ctx.writeAndFlush(response);
        
        log.info("Player {} handshake success, sessionId: {}", playerId, sessionId);
    }
    
    /**
     * 处理断线重连
     */
    private void handleReconnect(ChannelHandlerContext ctx, PokerMessage request) {
        String sessionId = request.getSessionId();
        Long playerId = request.getPlayerId();
        
        // 替换旧连接
        Channel oldChannel = ACTIVE_CHANNELS.put(sessionId, ctx.channel());
        if (oldChannel != null && oldChannel.isActive()) {
            oldChannel.close();
            log.info("Closed old connection for player {}", playerId);
        }
        
        // 恢复游戏状态
        PokerMessage gameState = gameService.getGameState(request.getTableId(), playerId);
        if (gameState != null) {
            ctx.writeAndFlush(gameState);
            log.info("Restored game state for player {} at table {}", playerId, request.getTableId());
        }
    }
    
    /**
     * 处理心跳
     */
    private void sendHeartbeatAck(ChannelHandlerContext ctx, PokerMessage heartbeat) {
        PokerMessage ack = PokerMessage.builder()
                .messageId(generateMessageId())
                .type(PokerMessage.MessageType.ACK)
                .timestamp(System.currentTimeMillis())
                .sessionId(heartbeat.getSessionId())
                .playerId(heartbeat.getPlayerId())
                .build();
        
        ctx.writeAndFlush(ack);
    }
    
    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, PokerMessage request, String errorCode, String errorMsg) {
        PokerMessage error = PokerMessage.builder()
                .messageId(generateMessageId())
                .type(PokerMessage.MessageType.ERROR)
                .timestamp(System.currentTimeMillis())
                .sessionId(request.getSessionId())
                .playerId(request.getPlayerId())
                .errorCode(errorCode)
                .errorMessage(errorMsg)
                .build();
        
        ctx.writeAndFlush(error);
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            log.warn("Idle timeout: {}", ctx.channel().remoteAddress());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("Channel inactive: {}", ctx.channel().remoteAddress());
        
        // 清理会话记录
        ACTIVE_CHANNELS.entrySet().removeIf(e -> e.getValue() == ctx.channel());
        
        ctx.fireChannelInactive();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in channel: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
    
    // 工具方法
    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }
    
    private String generateMessageId() {
        return UUID.randomUUID().toString();
    }
    
    // 静态方法：根据会话ID获取通道
    public static Channel getChannel(String sessionId) {
        return ACTIVE_CHANNELS.get(sessionId);
    }
    
    // 静态方法：根据玩家ID获取会话ID
    public static String getSessionId(Long playerId) {
        return PLAYER_SESSION_MAP.get(playerId);
    }
    
    // 静态方法：向玩家发送消息
    public static void sendMessageToPlayer(Long playerId, PokerMessage message) {
        String sessionId = PLAYER_SESSION_MAP.get(playerId);
        if (sessionId != null) {
            Channel channel = ACTIVE_CHANNELS.get(sessionId);
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(message);
            }
        }
    }
}
