package com.cy.share.rpc.core.handler;

import com.cy.share.rpc.protocol.MessageType;
import com.cy.share.rpc.protocol.RpcMessage;
import com.cy.share.rpc.protocol.RpcResponse;
import com.cy.share.rpc.util.PendingFutures;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        if (msg.getMessageType() != MessageType.RESPONSE.getCode()) {
            log.warn("Unexpected message type {} on client side", msg.getMessageType());
            return;
        }

        RpcResponse response = new RpcResponse();
        response.setRequestId(msg.getRequestId());
        response.setSuccess(msg.getHeader().isSuccess());
        response.setErrorMessage(msg.getHeader().getErrorMessage());
        response.setReturnType(msg.getHeader().getReturnType());
        response.setBody(msg.getBody());

        PendingFutures.complete(msg.getRequestId(), response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("RpcResponseHandler error", cause);
        ctx.close();
    }
}
