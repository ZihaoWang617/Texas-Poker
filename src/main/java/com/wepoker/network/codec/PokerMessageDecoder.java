package com.wepoker.network.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wepoker.network.protocol.PokerMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Netty JSON 解码器
 * 
 * 负责将ByteBuf转换为PokerMessage对象
 * 支持半包和粘包处理
 */
@Slf4j
public class PokerMessageDecoder extends ByteToMessageDecoder {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_MESSAGE_SIZE = 1024 * 64; // 64KB最大消息体
    private static final byte FRAME_DELIMITER = '\n';
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        
        // 寻找分隔符
        int delIndex = in.indexOf(in.readerIndex(), in.writerIndex(), FRAME_DELIMITER);
        
        if (delIndex == -1) {
            // 还没有完整的帧
            if (in.readableBytes() > MAX_MESSAGE_SIZE) {
                // 单个消息超过最大限制，丢弃
                in.skipBytes(in.readableBytes());
                log.warn("Message exceeds max size limit, discarding");
            }
            return;
        }
        
        // 计算消息长度（不包括分隔符）
        int length = delIndex - in.readerIndex();
        
        // 检查消息大小
        if (length > MAX_MESSAGE_SIZE) {
            in.skipBytes(length + 1);
            log.warn("Message size {} exceeds limit {}", length, MAX_MESSAGE_SIZE);
            return;
        }
        
        // 提取消息字节
        byte[] messageBytes = new byte[length];
        in.readBytes(messageBytes);
        in.skipBytes(1); // 跳过分隔符
        
        try {
            // JSON反序列化
            PokerMessage message = objectMapper.readValue(messageBytes, PokerMessage.class);
            out.add(message);
        } catch (Exception e) {
            log.error("Failed to decode message: {}", new String(messageBytes), e);
            // 继续处理下一个消息，不中断解码
        }
    }
}
