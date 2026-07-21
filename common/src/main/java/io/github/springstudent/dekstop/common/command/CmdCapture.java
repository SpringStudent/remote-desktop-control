package io.github.springstudent.dekstop.common.command;

import io.github.springstudent.dekstop.common.bean.CompressionMethod;
import io.github.springstudent.dekstop.common.bean.MemByteBuffer;
import io.github.springstudent.dekstop.common.configuration.CompressorEngineConfiguration;
import io.github.springstudent.dekstop.common.utils.UnitUtilities;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * @author ZhouNing
 * @date 2024/12/12 16:50
 **/
public class CmdCapture extends Cmd {
    private final int id;

    private final CompressionMethod compressionMethod;

    private final CompressorEngineConfiguration compressionConfiguration;

    private final MemByteBuffer payload;

    public CmdCapture(int id, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration,
                      MemByteBuffer payload) {
        this.id = id;
        this.compressionMethod = compressionMethod;
        this.compressionConfiguration = compressionConfiguration;
        this.payload = payload;
    }

    public int getId() {
        return id;
    }

    public CompressionMethod getCompressionMethod() {
        return compressionMethod;
    }

    public CompressorEngineConfiguration getCompressionConfiguration() {
        return compressionConfiguration;
    }

    public MemByteBuffer getPayload() {
        return payload;
    }

    @Override
    public CmdType getType() {
        return CmdType.Capture;
    }

    @Override
    public int getWireSize() {
        if (compressionConfiguration == null) {
            return 10 + payload.size();
        } else {
            return 10 + CmdCompressorConf.WIRE_SIZE + payload.size();
        }
    }

    public String toString() {
        return String.format("CmdCapture={id:%d,size:%s}", id, UnitUtilities.toBitSize(8d * payload.size()));
    }


    @Override
    public void encode(ByteBuf out) throws IOException {
        out.writeInt(id);
        encodeEnum(out, compressionMethod);
        out.writeByte(compressionConfiguration != null ? 1 : 0);
        if (compressionConfiguration != null) {
            new CmdCompressorConf(compressionConfiguration).encode(out);
        }
        out.writeInt(payload.size());
        out.writeBytes(payload.getInternal(), 0, payload.size());
    }

    public static CmdCapture decode(ByteBuf in) throws IOException {
        final int id = in.readInt();
        final CompressionMethod compressionMethod = decodeEnum(in, CompressionMethod.class);
        final CompressorEngineConfiguration compressionConfiguration;
        if (in.readByte() == 1) {
            compressionConfiguration = CmdCompressorConf.decode(in).getConfiguration();
        } else {
            compressionConfiguration = null;
        }
        final int len = in.readInt();
        final byte[] data = new byte[len];
        in.readBytes(data);
        return new CmdCapture(id, compressionMethod, compressionConfiguration, new MemByteBuffer(data));
    }
}
