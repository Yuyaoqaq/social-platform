package com.cy.share.rpc.core;

import com.cy.share.rpc.core.handler.RpcResponseHandler;
import com.cy.share.rpc.protocol.RpcMessageDecoder;
import com.cy.share.rpc.protocol.RpcMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ChannelManager implements AutoCloseable {
    private static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final ConcurrentHashMap<SocketAddress, Channel> channelMap = new ConcurrentHashMap<>();
    private final Bootstrap bootstrap;

    public ChannelManager() {
        RpcResponseHandler responseHandler = new RpcResponseHandler();
        RpcMessageEncoder  encoder        = new RpcMessageEncoder();

        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_BYTES, 12, 4, 0, 0));
                        p.addLast(new RpcMessageDecoder());
                        p.addLast(responseHandler);
                        p.addLast(encoder);
                    }
                });
    }

    public Channel getOrCreate(SocketAddress addr) {
        return channelMap.compute(addr, (key, existing) -> {
            if (existing != null && existing.isActive()) return existing;
            try {
                Channel ch = bootstrap.connect(key).sync().channel();
                log.debug("Connected to {}", key);
                ch.closeFuture().addListener(f -> channelMap.remove(key));
                return ch;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Connect interrupted: " + key, e);
            }
        });
    }

    @Override
    public void close() {
        channelMap.values().forEach(Channel::close);
        group.shutdownGracefully();
    }
}
