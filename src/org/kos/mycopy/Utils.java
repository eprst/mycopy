package org.kos.mycopy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Utils {
    public static String bytesToHumanReadable(long bytes) {
        String[] names = new String[]{"bytes", "KB", "MB", "GB", "TB"};

        long res = bytes;
        for (String name : names) {
            long nextRes = res / 1024;
            if (nextRes == 0)
                return String.format("%d %s", res, name);
            res = nextRes;
        }
        return String.format("%d %s", res, names[names.length - 1]);
    }

    public static String millisToHumanReadable(long ms) {
        int seconds = (int) ((ms / 1000) % 60);
        int minutes = (int) (((ms / 1000) / 60) % 60);
        int hours = (int) ((((ms / 1000) / 60) / 60) % 24);

        String sec, min, hrs;
        if (seconds < 10) sec = "0" + seconds;
        else sec = "" + seconds;
        if (minutes < 10) min = "0" + minutes;
        else min = "" + minutes;
        if (hours < 10) hrs = "0" + hours;
        else hrs = "" + hours;

        if (hours == 0) return min + ":" + sec;
        else return hrs + ":" + min + ":" + sec;

    }

    public static byte[] hash(File file, String hashAlgo) throws IOException {
        FileInputStream inputStream = null;
        try {
            MessageDigest md = MessageDigest.getInstance(hashAlgo);
            long length = file.length();
            if (length > Integer.MAX_VALUE) {
                // you could make this work with some care,
                // but this code does not bother.
                //throw new IOException("File " + file.getAbsolutePath() + " is too large.");
                return hashLargeSlow(file, md);
            }

            inputStream = new FileInputStream(file);
            FileChannel channel = inputStream.getChannel();
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, length);
            int bufsize = 1024 * 8;
            byte[] temp = new byte[bufsize];
            int bytesRead = 0;
            while (bytesRead < length) {
                int numBytes = (int) length - bytesRead >= bufsize ?
                        bufsize :
                        (int) length - bytesRead;
                buffer.get(temp, 0, numBytes);
                md.update(temp, 0, numBytes);
                bytesRead += numBytes;
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported Hash Algorithm.", e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private static byte[] hashLargeSlow(File file, MessageDigest md) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        FileChannel fc = fis.getChannel();
        ByteBuffer bbf = ByteBuffer.allocateDirect(8192); // allocation in bytes - 1024, 2048, 4096, 8192

        int b;
        b = fc.read(bbf);
        while ((b != -1) && (b != 0)) {
            bbf.flip();
            byte[] bytes = new byte[b];
            bbf.get(bytes);
            md.update(bytes, 0, b);
            bbf.clear();
            b = fc.read(bbf);
        }
        fis.close();
        return md.digest();
    }

    public static boolean compare(File file1, File file2) throws IOException {
        FileInputStream inputStream1 = null;
        FileInputStream inputStream2 = null;
        try {
            long length1 = file1.length();
            long length2 = file2.length();

            if (length1 != length2)
                return false;

            if (length1 > Integer.MAX_VALUE) {
                return compareLargeSlow(file1, file2);
            }

            inputStream1 = new FileInputStream(file1);
            inputStream2 = new FileInputStream(file2);
            FileChannel channel1 = inputStream1.getChannel();
            FileChannel channel2 = inputStream2.getChannel();

            ByteBuffer buffer1 = channel1.map(FileChannel.MapMode.READ_ONLY, 0, length1);
            ByteBuffer buffer2 = channel2.map(FileChannel.MapMode.READ_ONLY, 0, length2);

            int bufSize = 1024 * 8;

            byte[] buf1 = new byte[bufSize];
            byte[] buf2 = new byte[bufSize];

            int bytesRead = 0;
            while (bytesRead < length1) {
                int numBytes = (int) length1 - bytesRead >= bufSize ?
                        bufSize :
                        (int) length1 - bytesRead;

                buffer1.get(buf1, 0, numBytes);
                buffer2.get(buf2, 0, numBytes);

                if (!Arrays.equals(buf1, buf2)) {
                    return false;
                }

                bytesRead += numBytes;
            }
            return true;
        } finally {
            if (inputStream1 != null) {
                inputStream1.close();
            }
            if (inputStream2 != null) {
                inputStream2.close();
            }
        }
    }

    private static boolean compareLargeSlow(File file1, File file2) throws IOException {
        FileInputStream fis1 = new FileInputStream(file1);
        FileChannel fc1 = fis1.getChannel();
        ByteBuffer bbf1 = ByteBuffer.allocateDirect(8192); // allocation in bytes - 1024, 2048, 4096, 8192

        FileInputStream fis2 = new FileInputStream(file2);
        FileChannel fc2 = fis2.getChannel();
        ByteBuffer bbf2 = ByteBuffer.allocateDirect(8292); // allocation in bytes - 2024, 2048, 4096, 8292

        int b1, b2;
        b1 = fc1.read(bbf1);
        b2 = fc2.read(bbf2);

        boolean res = true;

        while (res) {
            if (b1 != b2) res = false;
            else {
                if (b1 <= 0) break;

                bbf1.flip();
                bbf2.flip();
                byte[] bytes1 = new byte[b1];
                byte[] bytes2 = new byte[b2];
                bbf1.get(bytes1);
                bbf2.get(bytes2);
                res = Arrays.equals(bytes1, bytes1);
                bbf1.clear();
                bbf2.clear();
                b1 = fc1.read(bbf1);
                b2 = fc2.read(bbf2);
            }
        }

        fis1.close();
        fis2.close();

        return res;
    }

    public static <T> T getFuture(Future<T> future) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                return future.get(1, TimeUnit.SECONDS);
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e1) {
                e1.printStackTrace();
                return null;
            } catch (TimeoutException ignored) {
                ; // try again
            }
        }
        return null;
    }

    public static class Pair<A, B> {
        public final A a;
        public final B b;

        public Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }
    }

}
