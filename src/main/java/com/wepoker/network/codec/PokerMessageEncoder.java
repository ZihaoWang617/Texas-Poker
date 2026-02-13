package com.wepoker.network.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wepoker.network.protocol.PokerMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty JSON 编码器
 * 
 * 负责将PokerMessage对象转换为ByteBuf
 */
@Slf4j
public class PokerMessageEncoder extends MessageToByteEncoder<PokerMessage> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final byte FRAME_DELIMITER = '\n';
    
    @Override
    protected void encode(ChannelHandlerContext ctx, PokerMessage msg, ByteBuf out) throws Exception {
        try {
            // JSON序列化
            byte[] messageBytes = objectMapper.writeValueAsBytes(msg);
            
            // 写入消息体和分隔符
            out.writeBytes(messageBytes);
            out.writeByte(FRAME_DELIMITER);
            
        } catch (Exception e) {
            log.error("Failed to encode message: {}", msg, e);
            throw e;
        }
    }
}
