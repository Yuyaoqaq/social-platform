package com.cy.share.rpc.util;

public final class SnowflakeIdGenerator {
    private static final long EPOCH         = 1672531200000L; // 2023-01-01 UTC
    private static final long MACHINE_ID    = 1L;
    private static final long SEQ_BITS      = 12L;
    private static final long MACHINE_BITS  = 10L;
    private static final long MAX_SEQ       = (1L << SEQ_BITS) - 1;
    private static final long MACHINE_SHIFT = SEQ_BITS;
    private static final long TS_SHIFT      = SEQ_BITS + MACHINE_BITS;

    private static long lastTs = -1L;
    private static long seq    = 0L;

    public static synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts == lastTs) {
            seq = (seq + 1) & MAX_SEQ;
            if (seq == 0) {
                while (ts <= lastTs) ts = System.currentTimeMillis();
            }
        } else {
            seq = 0L;
        }
        lastTs = ts;
        return ((ts - EPOCH) << TS_SHIFT) | (MACHINE_ID << MACHINE_SHIFT) | seq;
    }

    private SnowflakeIdGenerator() {}
}
