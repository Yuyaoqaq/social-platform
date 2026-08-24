package com.cy.share.rpc.proxy;

import com.cy.share.rpc.core.ChannelManager;
import com.cy.share.rpc.lb.LoadBalancer;
import com.cy.share.rpc.registry.Registry;
import com.cy.share.rpc.retry.RetryPolicy;
import com.cy.share.rpc.serialize.Serializer;

import java.lang.reflect.Proxy;

public final class RpcProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> serviceClass, Registry registry, LoadBalancer lb,
                                ChannelManager channelManager, Serializer serializer,
                                long timeout, int retryTimes, RetryPolicy retryPolicy) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new RpcInvocationHandler(serviceClass, timeout, retryTimes,
                        registry, lb, channelManager, serializer, retryPolicy));
    }

    private RpcProxyFactory() {}
}
