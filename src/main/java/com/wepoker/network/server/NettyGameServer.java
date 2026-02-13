package com.wepoker.network.server;

import com.wepoker.network.codec.PokerMessageDecoder;
import com.wepoker.network.codec.PokerMessageEncoder;
import com.wepoker.network.handler.PokerGameHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Netty 游戏服务器
 * 
 * 长连接服务器，支持高并发连接
 * 使用虚拟线程池（如果是Java 21）处理业务逻辑
 */
@Slf4j
@Component
public class NettyGameServer {
    
    @Value("${wepoker.netty.host:0.0.0.0}")
    private String host;
    
    @Value("${wepoker.netty.port:9000}")
    private int port;
    
    @Value("${wepoker.netty.bossThreads:1}")
    private int bossThreads;
    
    @Value("${wepoker.netty.workerThreads:8}")
    private int workerThreads;
    
    @Autowired
    private PokerGameHandler gameHandler;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    @PostConstruct
    public void startup() {
        Thread thread = new Thread(this::start, "NettyGameServer");
        thread.setDaemon(false);
        thread.start();
    }
    
    public void start() {
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 超时检测：30秒无读、60秒无写、90秒无活动则触发超时事件
                            pipeline.addLast(new IdleStateHandler(30, 60, 90, TimeUnit.SECONDS));
                            
                            // JSON编码/解码
                            pipeline.addLast(new PokerMessageDecoder());
                            pipeline.addLast(new PokerMessageEncoder());
                            
                            // 游戏逻辑处理
                            pipeline.addLast(gameHandler);
                        }
                    });
            
            // TCP 相关配置
            bootstrap.option(io.netty.channel.ChannelOption.SO_BACKLOG, 1024)
                    .option(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                    .childOption(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                    .childOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true);
            
            // 绑定端口
            serverChannel = bootstrap.bind(host, port).sync().channel();
            log.info("Netty Game Server started on {}:{}", host, port);
            
            // 等待服务器关闭
            serverChannel.closeFuture().sync();
            
        } catch (Exception e) {
            log.error("Netty server startup failed", e);
            throw new RuntimeException(e);
        } finally {
            shutdown();
        }
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Netty Game Server...");
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(5, 15, TimeUnit.SECONDS);
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(5, 15, TimeUnit.SECONDS);
        }
        
        log.info("Netty Game Server shutdown complete");
    }
}
