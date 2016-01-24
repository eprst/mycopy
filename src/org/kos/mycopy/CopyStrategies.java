package org.kos.mycopy;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class CopyStrategies {
    public static CopyStrategy MOD_TIME_STRATEGY =
            (src, dst, e) -> !dst.exists() || dst.lastModified() != src.lastModified();

    public static CopyStrategy SIZE_STRATEGY = (src, dst, e) -> !dst.exists() || dst.length() != src.length();

    public static CopyStrategy HEAD_TAIL_STRATEGY = (src, dst, e) -> {
        if (dst.exists() && dst.length() == src.length()) {
            int bufSize = Math.min(4096, (int) src.length());

            if (bufSize <= 0)
                return true;

            byte[] srcBuf = readBytes(src, 0, bufSize);
            byte[] dstBuf = readBytes(dst, 0, bufSize);

            if (srcBuf == null || dstBuf == null || !Arrays.equals(srcBuf, dstBuf))
                return true;

            if (src.length() <= bufSize)
                return false;

            srcBuf = readBytes(src, src.length() - bufSize - 1, bufSize);
            dstBuf = readBytes(dst, src.length() - bufSize - 1, bufSize);

            return srcBuf == null || dstBuf == null || !Arrays.equals(srcBuf, dstBuf);
        } else return true;
    };

    public static CopyStrategy HASH_STRATEGY = (src, dst, e) -> {
        if (src.exists() && dst.exists()) {
            Future<byte[]> f1 = e.submit(() -> Utils.hash(src, "SHA-256"));
            Future<byte[]> f2 = e.submit(() -> Utils.hash(dst, "SHA-256"));

            try {
                return !Arrays.equals(f1.get(), f2.get());
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException e1) {
                e1.printStackTrace();
                return false;
            }
        } else return src.exists() != dst.exists();
    };

    public static CopyStrategy CONTENT_STRATEGY = (src, dst, e) -> {
        if (src.exists() && dst.exists()) {
            try {
                return !Utils.compare(src, dst);
            } catch (IOException e1) {
                e1.printStackTrace();
                return true;
            }
        } else return src.exists() != dst.exists();
    };

    public static CopyStrategy ALWAYS_COPY_STRATEGY = (src, dst, e) -> true;

    public static CopyStrategy combineStrategies(CopyStrategy... strategies) {
        return strategies.length == 0 ? ALWAYS_COPY_STRATEGY : (src, dst, e) -> {
            for (CopyStrategy strategy : strategies)
                if (strategy.shouldCopy(src, dst, e))
                    return true;
            return false;
        };
    }

    private static byte[] readBytes(File f, long offset, int bytes) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(bytes);
            RandomAccessFile rf = new RandomAccessFile(f, "r");
            FileChannel fc = rf.getChannel();
            fc.position(offset);
            fc.read(buf);
            byte[] res = buf.array();
            fc.close();
            rf.close();
            return res;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
