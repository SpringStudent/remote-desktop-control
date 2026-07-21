package io.github.springstudent.dekstop.client.squeeze;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import io.github.springstudent.dekstop.common.bean.Constants;
import io.github.springstudent.dekstop.common.bean.MemByteBuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Zstd compressor with configurable compression level.
 * <p>
 * Level 1  = fastest, ~80% compression ratio  — ideal for real-time screen capture over LAN
 * Level 3  = default, good balance
 * Level 19 = maximum compression, very slow   — only useful for low-bandwidth WAN
 *
 * @author ZhouNing
 * @date 2025/2/19 10:02
 **/
public class ZstdZipper implements Zipper {

    private final int compressionLevel;

    public ZstdZipper() {
        this(Constants.DEFAULT_ZSTD_COMPRESSION_LEVEL);
    }

    public ZstdZipper(int compressionLevel) {
        if (compressionLevel < 1 || compressionLevel > 22) {
            throw new IllegalArgumentException("Zstd compression level must be 1-22, got: " + compressionLevel);
        }
        this.compressionLevel = compressionLevel;
    }

    @Override
    public MemByteBuffer zip(MemByteBuffer unzipped) throws IOException {
        try (MemByteBuffer zipped = new MemByteBuffer()) {
            try (ZstdOutputStream zstdOutputStream = new ZstdOutputStream(zipped, compressionLevel)) {
                zstdOutputStream.write(unzipped.getInternal(), 0, unzipped.size());
                zstdOutputStream.flush();
                return zipped;
            }
        }
    }

    @Override
    public MemByteBuffer unzip(MemByteBuffer zipped) throws IOException {
        try (final MemByteBuffer unzipped = new MemByteBuffer()) {
            try (ZstdInputStream zstdInputStream = new ZstdInputStream(new ByteArrayInputStream(zipped.getInternal(), 0, zipped.size()))) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = zstdInputStream.read(buffer)) > 0) {
                    unzipped.write(buffer, 0, bytesRead);
                }
            }
            return unzipped;
        }
    }
}
