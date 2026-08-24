package com.cy.share.rpc.protocol;

import com.cy.share.rpc.serialize.JsonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class RpcMessageDecoder extends MessageToMessageDecoder<ByteBuf> {
    private static final short MAGIC   = (short) 0xCAFE;
    private static final JsonSerializer JSON_SER = new JsonSerializer();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Fixed header (16B)
        short magic = in.readShort();
        if (magic != MAGIC) {
            throw new IllegalStateException("Invalid magic: 0x" + Integer.toHexString(magic & 0xFFFF));
        }
        byte version      = in.readByte();
        byte flags        = in.readByte();
        long requestId    = in.readLong();
        int  totalLen     = in.readInt();

        byte messageType   = (byte) ((flags >> 4) & 0x0F);
        byte serializeType = (byte) (flags & 0x0F);

        // Variable section
        short headerLen = in.readShort();
        byte[] headerBytes = new byte[headerLen];
        in.readBytes(headerBytes);

        int bodyLen = totalLen - 2 - headerLen;
        byte[] bodyBytes = null;
        if (bodyLen > 0) {
            bodyBytes = new byte[bodyLen];
            in.readBytes(bodyBytes);
        }

        RpcHeader header = JSON_SER.deserialize(headerBytes, RpcHeader.class);

        RpcMessage msg = new RpcMessage();
        msg.setVersion(version);
        msg.setMessageType(messageType);
        msg.setSerializeType(serializeType);
        msg.setRequestId(requestId);
        msg.setHeader(header);
        msg.setBody(bodyBytes);

        out.add(msg);
    }
}
