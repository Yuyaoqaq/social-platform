package com.cy.share.rpc.core.server;

import com.cy.share.rpc.core.handler.RpcRequestHandler;
import com.cy.share.rpc.protocol.RpcMessageDecoder;
import com.cy.share.rpc.protocol.RpcMessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class RpcServer implements AutoCloseable {
    private static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;

    private final int port;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    public RpcServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        boss   = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        RpcRequestHandler handler = new RpcRequestHandler();

        ServerBootstrap b = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
                        p.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_BYTES, 12, 4, 0, 0));
                        p.addLast(new RpcMessageDecoder());
                        p.addLast(handler);
                        p.addLast(new RpcMessageEncoder());
                    }
                });

        ChannelFuture f = b.bind(port).sync();
        serverChannel = f.channel();
        log.info("RpcServer started on port {}", port);
    }

    public void waitForShutdown() throws InterruptedException {
        serverChannel.closeFuture().sync();
    }

    @Override
    public void close() {
        if (boss   != null) boss.shutdownGracefully();
        if (worker != null) worker.shutdownGracefully();
        log.info("RpcServer stopped");
    }
}
