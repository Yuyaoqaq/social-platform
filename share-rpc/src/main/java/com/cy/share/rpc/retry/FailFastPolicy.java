package com.cy.share.rpc.retry;

import com.cy.share.rpc.protocol.RpcRequest;

/** Never retries — fail immediately on any error */
public class FailFastPolicy implements RetryPolicy {
    @Override
    public boolean shouldRetry(RpcRequest request, Throwable cause, int retryCount) {
        return false;
    }

    @Override
    public long getRetryInterval(int retryCount) {
        return 0;
    }
}
