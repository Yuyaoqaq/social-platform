package com.cy.share.rpc.util;

import com.cy.share.rpc.protocol.RpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingFutures {
    private static final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> MAP =
            new ConcurrentHashMap<>();

    public static void put(long requestId, CompletableFuture<RpcResponse> future) {
        MAP.put(requestId, future);
    }

    public static void complete(long requestId, RpcResponse response) {
        CompletableFuture<RpcResponse> future = MAP.remove(requestId);
        if (future != null) future.complete(response);
    }

    public static void remove(long requestId) {
        MAP.remove(requestId);
    }

    private PendingFutures() {}
}
