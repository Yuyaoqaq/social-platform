package com.cy.share.rpc.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "share.rpc")
public class RpcProperties {
    private boolean enabled = true;

    private Provider provider = new Provider();
    private Consumer consumer = new Consumer();
    private RegistryConfig registry = new RegistryConfig();
    private SerializerConfig serializer = new SerializerConfig();

    @Data
    public static class Provider {
        private int    port = 20880;
        private String host = "127.0.0.1";
    }

    @Data
    public static class Consumer {
        private long timeout       = 3000;
        private int  retryTimes    = 1;
        private int  channelMaxIdle = 60;
    }

    @Data
    public static class RegistryConfig {
        private String type = "nacos"; // local | nacos
        private Nacos  nacos = new Nacos();

        @Data
        public static class Nacos {
            private String serverAddr = "127.0.0.1:8848";
        }
    }

    @Data
    public static class SerializerConfig {
        private String type = "json"; // json | kryo
    }
}
