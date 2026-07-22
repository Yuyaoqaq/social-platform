package com.cy.share.rpc.core.handler;

import com.cy.share.rpc.protocol.*;
import com.cy.share.rpc.serialize.JsonSerializer;
import com.cy.share.rpc.serialize.SerializerFactory;
import com.cy.share.rpc.serialize.Serializer;
import com.cy.share.rpc.util.ServiceMap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

@Slf4j
@ChannelHandler.Sharable
public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        // Echo heartbeat
        if (msg.getMessageType() == MessageType.HEARTBEAT.getCode()) {
            ctx.writeAndFlush(msg);
            return;
        }

        RpcHeader reqHeader = msg.getHeader();
        Serializer serializer = SerializerFactory.getSerializer(msg.getSerializeType());
        RpcMessage responseMsg;

        try {
            // Resolve param types from String[] names
            String[] typeNames = reqHeader.getParamTypes();
            Class<?>[] paramTypes = new Class<?>[typeNames == null ? 0 : typeNames.length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = resolveClass(typeNames[i]);
            }

            // Deserialize params (type-aware)
            Object[] params = JsonSerializer.deserializeParams(msg.getBody(), paramTypes);

            // Invoke service
            Object bean = ServiceMap.get(reqHeader.getServiceName());
            if (bean == null) {
                throw new IllegalStateException("Service not found: " + reqHeader.getServiceName());
            }
            Method method = bean.getClass().getMethod(reqHeader.getMethodName(), paramTypes);
            Object result = method.invoke(bean, params);

            // Build success response
            byte[] responseBody = (result == null || method.getReturnType() == void.class)
                    ? new byte[0]
                    : serializer.serialize(result);

            RpcHeader respHeader = new RpcHeader();
            respHeader.setSuccess(true);
            respHeader.setReturnType(method.getReturnType().getName());

            responseMsg = buildResponse(msg, respHeader, responseBody);
        } catch (Exception e) {
            log.error("RPC invoke error", e);
            RpcHeader errHeader = new RpcHeader();
            errHeader.setSuccess(false);
            errHeader.setErrorMessage(e.getMessage());
            responseMsg = buildResponse(msg, errHeader, new byte[0]);
        }

        ctx.writeAndFlush(responseMsg);
    }

    private RpcMessage buildResponse(RpcMessage req, RpcHeader header, byte[] body) {
        RpcMessage resp = new RpcMessage();
        resp.setVersion(req.getVersion());
        resp.setMessageType(MessageType.RESPONSE.getCode());
        resp.setSerializeType(req.getSerializeType());
        resp.setRequestId(req.getRequestId());
        resp.setHeader(header);
        resp.setBody(body.length > 0 ? body : null);
        return resp;
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return switch (name) {
            case "int"     -> int.class;
            case "long"    -> long.class;
            case "boolean" -> boolean.class;
            case "double"  -> double.class;
            case "float"   -> float.class;
            default        -> Class.forName(name);
        };
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("RpcRequestHandler error", cause);
        ctx.close();
    }
}
