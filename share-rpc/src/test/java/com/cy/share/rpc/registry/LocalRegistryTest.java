package com.cy.share.rpc.registry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalRegistryTest {

    @Test
    void registerAndDiscover() {
        LocalRegistry registry = new LocalRegistry();
        ServiceMeta meta = new ServiceMeta("com.cy.HelloService", "127.0.0.1", 20880, 1);
        registry.register(meta);

        List<ServiceMeta> found = registry.discover("com.cy.HelloService");
        assertEquals(1, found.size());
        assertEquals("127.0.0.1", found.get(0).getHost());
        assertEquals(20880, found.get(0).getPort());
    }

    @Test
    void discoverEmptyForUnknownService() {
        LocalRegistry registry = new LocalRegistry();
        List<ServiceMeta> found = registry.discover("com.cy.Unknown");
        assertTrue(found.isEmpty());
    }

    @Test
    void unregister() {
        LocalRegistry registry = new LocalRegistry();
        ServiceMeta meta = new ServiceMeta("com.cy.HelloService", "127.0.0.1", 20880, 1);
        registry.register(meta);
        registry.unregister(meta);
        assertTrue(registry.discover("com.cy.HelloService").isEmpty());
    }
}
