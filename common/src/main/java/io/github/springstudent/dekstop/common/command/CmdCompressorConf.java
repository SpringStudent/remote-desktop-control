package io.github.springstudent.dekstop.common.command;

import io.github.springstudent.dekstop.common.bean.CompressionMethod;
import io.github.springstudent.dekstop.common.configuration.CompressorEngineConfiguration;
import io.netty.buffer.ByteBuf;
import java.io.IOException;

/**
 * @author ZhouNing
 * @date 2024/12/12 18:05
 **/
public class CmdCompressorConf extends Cmd{

    public static final int WIRE_SIZE = 11;

    private final CompressorEngineConfiguration configuration;

    public CmdCompressorConf(CompressorEngineConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public CmdType getType() {
        return CmdType.CompressorConfig;
    }

    public CompressorEngineConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public int getWireSize() {
        return WIRE_SIZE;
    }

    @Override
    public void encode(ByteBuf out) throws IOException {
        encodeEnum(out, configuration.getMethod());
        out.writeByte(configuration.useCache() ? 1 : 0);
        out.writeInt(configuration.getCacheMaxSize());
        out.writeInt(configuration.getCachePurgeSize());
        out.writeByte(configuration.getCompressionLevel());
    }

    public static CmdCompressorConf decode(ByteBuf in) throws IOException {
        final CompressionMethod method = decodeEnum(in, CompressionMethod.class);
        final boolean useCase = in.readByte() == 1;
        final int maxSize = in.readInt();
        final int purgeSize = in.readInt();
        final int compressionLevel = in.isReadable() ? in.readByte() & 0xFF : 1;
        return new CmdCompressorConf(new CompressorEngineConfiguration(method, useCase, maxSize, purgeSize, compressionLevel));
    }


    public String toString() {
        return String.format("CmdCompressorConf={configuration:%s}", configuration.getMethod());
    }
}
