package com.cy.share.rpc.retry;

import com.cy.share.rpc.protocol.RpcRequest;

public interface RetryPolicy {
    boolean shouldRetry(RpcRequest request, Throwable cause, int retryCount);
    long getRetryInterval(int retryCount);
}
