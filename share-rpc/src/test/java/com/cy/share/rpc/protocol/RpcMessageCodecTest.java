package com.cy.share.rpc.protocol;

import com.cy.share.rpc.serialize.JsonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RpcMessageCodecTest {

    private EmbeddedChannel buildChannel() {
        return new EmbeddedChannel(
                new RpcMessageEncoder(),
                new LengthFieldBasedFrameDecoder(8 * 1024 * 1024, 12, 4, 0, 0),
                new RpcMessageDecoder()
        );
    }

    @Test
    void encodeDecodeRequest() {
        EmbeddedChannel ch = buildChannel();

        RpcHeader header = new RpcHeader();
        header.setServiceName("com.cy.demo.HelloService");
        header.setMethodName("sayHello");
        header.setParamTypes(new String[]{"java.lang.String"});

        byte[] body = new JsonSerializer().serialize("world");

        RpcMessage msg = new RpcMessage();
        msg.setVersion((byte) 0x01);
        msg.setMessageType(MessageType.REQUEST.getCode());
        msg.setSerializeType(SerializeType.JSON.getCode());
        msg.setRequestId(12345L);
        msg.setHeader(header);
        msg.setBody(body);

        ch.writeOutbound(msg);
        ByteBuf encoded = ch.readOutbound();
        assertNotNull(encoded, "Encoder should produce bytes");

        ch.writeInbound(encoded);
        RpcMessage decoded = ch.readInbound();
        assertNotNull(decoded, "Decoder should produce RpcMessage");
        assertEquals(12345L, decoded.getRequestId());
        assertEquals(MessageType.REQUEST.getCode(), decoded.getMessageType());
        assertEquals("com.cy.demo.HelloService", decoded.getHeader().getServiceName());
        assertEquals("sayHello", decoded.getHeader().getMethodName());
        assertNotNull(decoded.getBody());

        ch.finish();
    }

    @Test
    void encodeDecodeHeartbeat() {
        EmbeddedChannel ch = buildChannel();

        RpcHeader header = new RpcHeader();
        RpcMessage msg = new RpcMessage();
        msg.setVersion((byte) 0x01);
        msg.setMessageType(MessageType.HEARTBEAT.getCode());
        msg.setSerializeType(SerializeType.JSON.getCode());
        msg.setRequestId(999L);
        msg.setHeader(header);
        msg.setBody(null);

        ch.writeOutbound(msg);
        ByteBuf encoded = ch.readOutbound();
        ch.writeInbound(encoded);
        RpcMessage decoded = ch.readInbound();

        assertEquals(MessageType.HEARTBEAT.getCode(), decoded.getMessageType());
        assertEquals(999L, decoded.getRequestId());
        ch.finish();
    }
}
