package com.cy.share.rpc.protocol;

import com.cy.share.rpc.serialize.JsonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

@ChannelHandler.Sharable
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {
    private static final short MAGIC   = (short) 0xCAFE;
    private static final byte  VERSION = 0x01;
    private static final JsonSerializer JSON_SER = new JsonSerializer();

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) throws Exception {
        // Fixed header (16B)
        out.writeShort(MAGIC);
        out.writeByte(VERSION);
        byte flags = (byte) ((msg.getMessageType() << 4) | (msg.getSerializeType() & 0x0F));
        out.writeByte(flags);
        out.writeLong(msg.getRequestId());

        // Serialize header to JSON bytes
        byte[] headerBytes = JSON_SER.serialize(msg.getHeader());
        byte[] bodyBytes   = msg.getBody() != null ? msg.getBody() : new byte[0];

        // TotalLen = 2 (HeaderLen field itself) + headerLen + bodyLen
        int totalLen = 2 + headerBytes.length + bodyBytes.length;
        out.writeInt(totalLen);

        // Variable section
        out.writeShort((short) headerBytes.length);
        out.writeBytes(headerBytes);
        out.writeBytes(bodyBytes);
    }
}
