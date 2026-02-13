package com.wepoker.network.protocol;

import lombok.*;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 扑克游戏统一消息协议 (JSON-based)
 * 
 * 支持断线重连，所有消息都有唯一的消息ID和时间戳
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PokerMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    // 消息类型
    public enum MessageType {
        // 玩家操作
        JOIN_TABLE,
        LEAVE_TABLE,
        POST_BLIND,
        BET,
        RAISE,
        CALL,
        CHECK,
        FOLD,
        ALL_IN,
        REQUEST_ACTION,
        
        // 游戏状态
        GAME_STATE_UPDATE,
        BOARD_UPDATE,
        POT_UPDATE,
        RESULT,
        TIME_WARNING,
        
        // 连接管理
        HEARTBEAT,
        HANDSHAKE,
        RECONNECT,
        DISCONNECT,
        
        // 错误处理
        ERROR,
        ACK
    }

    private String messageId;                    // 唯一消息ID
    private MessageType type;                    // 消息类型
    private long timestamp;                      // 时间戳（毫秒）
    private long tableId;                        // 房间ID
    private long playerId;                       // 玩家ID
    private String sessionId;                    // 会话ID（用于断线重连）
    private Map<String, Object> payload;        // 消息体
    private int sequenceNumber;                  // 序列号（确保顺序性）
    private String errorCode;                    // 错误代码
    private String errorMessage;                 // 错误信息

    /**
     * 便捷方法：设置payload数据
     */
    public void setPayloadField(String key, Object value) {
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.put(key, value);
    }

    /**
     * 便捷方法：获取payload数据
     */
    public Object getPayloadField(String key) {
        if (payload == null) {
            return null;
        }
        return payload.get(key);
    }

    @Override
    public String toString() {
        return "PokerMessage{" +
                "messageId='" + messageId + '\'' +
                ", type=" + type +
                ", playerId=" + playerId +
                ", tableId=" + tableId +
                ", timestamp=" + timestamp +
                '}';
    }
}
