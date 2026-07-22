package com.cy.share.rpc.util;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesUniqueIds() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(SnowflakeIdGenerator.nextId());
        }
        assertEquals(1000, ids.size(), "All 1000 IDs should be unique");
    }

    @Test
    void idsArePositive() {
        for (int i = 0; i < 100; i++) {
            assertTrue(SnowflakeIdGenerator.nextId() > 0);
        }
    }
}
