package com.cy.share.rpc.proxy;

import com.cy.share.rpc.core.ChannelManager;
import com.cy.share.rpc.exception.RpcTimeoutException;
import com.cy.share.rpc.lb.LoadBalancer;
import com.cy.share.rpc.protocol.*;
import com.cy.share.rpc.registry.Registry;
import com.cy.share.rpc.registry.ServiceMeta;
import com.cy.share.rpc.retry.RetryPolicy;
import com.cy.share.rpc.serialize.Serializer;
import com.cy.share.rpc.util.PendingFutures;
import com.cy.share.rpc.util.SnowflakeIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RpcInvocationHandler implements InvocationHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Class<?> interfaceClass;
    private final long timeout;
    private final int retryTimes;
    private final Registry registry;
    private final LoadBalancer loadBalancer;
    private final ChannelManager channelManager;
    private final Serializer serializer;
    private final RetryPolicy retryPolicy;

    public RpcInvocationHandler(Class<?> interfaceClass, long timeout, int retryTimes,
                                 Registry registry, LoadBalancer loadBalancer,
                                 ChannelManager channelManager, Serializer serializer,
                                 RetryPolicy retryPolicy) {
        this.interfaceClass = interfaceClass;
        this.timeout        = timeout;
        this.retryTimes     = retryTimes;
        this.registry       = registry;
        this.loadBalancer   = loadBalancer;
        this.channelManager = channelManager;
        this.serializer     = serializer;
        this.retryPolicy    = retryPolicy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Handle Object methods locally
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        int attempt = 0;
        Throwable lastCause = null;

        while (attempt <= retryTimes) {
            try {
                return doInvoke(method, args);
            } catch (RpcTimeoutException | java.net.ConnectException e) {
                lastCause = e;
                RpcRequest req = buildRequest(method, args);
                if (attempt < retryTimes && retryPolicy.shouldRetry(req, e, attempt)) {
                    long interval = retryPolicy.getRetryInterval(attempt);
                    log.warn("RPC retry {}/{} after {}ms due to: {}", attempt + 1, retryTimes, interval, e.getMessage());
                    Thread.sleep(interval);
                } else {
                    break;
                }
            }
            attempt++;
        }
        throw lastCause;
    }

    private Object doInvoke(Method method, Object[] args) throws Throwable {
        long requestId = SnowflakeIdGenerator.nextId();

        // Discover + select
        List<ServiceMeta> instances = registry.discover(interfaceClass.getName());
        if (instances.isEmpty()) {
            throw new IllegalStateException("No available instances for: " + interfaceClass.getName());
        }
        ServiceMeta selected = loadBalancer.select(instances);
        Channel channel = channelManager.getOrCreate(
                new InetSocketAddress(selected.getHost(), selected.getPort()));

        // Serialize params
        byte[] bodyBytes = (args == null || args.length == 0)
                ? new byte[0]
                : serializer.serialize(args);

        // Build RpcMessage
        RpcHeader header = new RpcHeader();
        header.setServiceName(interfaceClass.getName());
        header.setMethodName(method.getName());
        header.setParamTypes(Arrays.stream(method.getParameterTypes())
                .map(Class::getName).toArray(String[]::new));

        RpcMessage message = new RpcMessage();
        message.setVersion((byte) 0x01);
        message.setMessageType(MessageType.REQUEST.getCode());
        message.setSerializeType(serializer.getType());
        message.setRequestId(requestId);
        message.setHeader(header);
        message.setBody(bodyBytes.length > 0 ? bodyBytes : null);

        // Register future BEFORE sending (avoid race condition)
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        PendingFutures.put(requestId, future);
        channel.writeAndFlush(message);

        try {
            RpcResponse response = future.get(timeout, TimeUnit.MILLISECONDS);
            if (!response.isSuccess()) {
                throw new RuntimeException("RPC error: " + response.getErrorMessage());
            }
            // Deserialize return value
            byte[] body = response.getBody();
            if (body == null || body.length == 0 || method.getReturnType() == void.class) {
                return null;
            }
            return MAPPER.readValue(body, method.getReturnType());
        } catch (TimeoutException e) {
            PendingFutures.remove(requestId);
            throw new RpcTimeoutException(
                    "Timeout calling " + interfaceClass.getSimpleName() + "#" + method.getName());
        }
    }

    private RpcRequest buildRequest(Method method, Object[] args) {
        RpcRequest req = new RpcRequest();
        req.setServiceName(interfaceClass.getName());
        req.setMethodName(method.getName());
        req.setParamTypes(method.getParameterTypes());
        req.setParams(args);
        return req;
    }
}
