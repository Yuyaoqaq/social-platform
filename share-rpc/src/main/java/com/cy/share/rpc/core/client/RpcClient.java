package com.cy.share.rpc.core.client;

import com.cy.share.rpc.core.ChannelManager;
import com.cy.share.rpc.lb.LoadBalancer;
import com.cy.share.rpc.lb.RandomLoadBalancer;
import com.cy.share.rpc.proxy.RpcProxyFactory;
import com.cy.share.rpc.registry.Registry;
import com.cy.share.rpc.retry.FailFastPolicy;
import com.cy.share.rpc.retry.RetryPolicy;
import com.cy.share.rpc.serialize.JsonSerializer;
import com.cy.share.rpc.serialize.Serializer;

public class RpcClient implements AutoCloseable {
    private final Registry registry;
    private final LoadBalancer loadBalancer;
    private final ChannelManager channelManager;
    private final Serializer serializer;
    private final long timeout;
    private final int retryTimes;
    private final RetryPolicy retryPolicy;

    public RpcClient(Registry registry) {
        this(registry, new RandomLoadBalancer(), new JsonSerializer(), 3000L, 1, new FailFastPolicy());
    }

    public RpcClient(Registry registry, LoadBalancer lb, Serializer serializer,
                     long timeout, int retryTimes, RetryPolicy retryPolicy) {
        this.registry       = registry;
        this.loadBalancer   = lb;
        this.serializer     = serializer;
        this.timeout        = timeout;
        this.retryTimes     = retryTimes;
        this.retryPolicy    = retryPolicy;
        this.channelManager = new ChannelManager();
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> serviceClass) {
        return RpcProxyFactory.create(
                serviceClass, registry, loadBalancer, channelManager, serializer, timeout, retryTimes, retryPolicy);
    }

    @Override
    public void close() {
        channelManager.close();
    }
}
