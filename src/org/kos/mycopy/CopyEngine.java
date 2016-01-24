package org.kos.mycopy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CopyEngine {
    public static final int TRANSFER_CHUNK_SIZE = 16 * 1024 * 1024;

    private final ProgressListener totalProgressListener;
    private final StatusListener totalStatusListener;
    private final StatusListener auxStatusListener;
    private final Runnable completionListener;
    private final File source;
    private final File destination;
    private final boolean copySourceItself;
    private final CopyStrategy copyStrategy;

    private ArrayBlockingQueue<Utils.Pair<StatusListener, ProgressListener>> currentFileListeners;

    private final Thread worker;
    private volatile boolean running;
    private long totalBytesToCopy;
    private int totalFilesToCopy;
    private AtomicLong totalBytesCopied;
    private AtomicInteger totalFilesCopied;
    private long startedTimestamp;

    private ExecutorService executor;

    public CopyEngine(ProgressListener totalProgressListener,
                      StatusListener totalStatusListener,
                      ProgressListener[] fileProgressListeners,
                      StatusListener[] fileStatusListeners,
                      StatusListener auxStatusListener,
                      Runnable completionListener,
                      File source,
                      File destination,
                      boolean copySourceItself,
                      CopyStrategy copyStrategy,
                      int threads) {
        this.totalProgressListener = totalProgressListener;
        this.totalStatusListener = totalStatusListener;
        this.auxStatusListener = auxStatusListener;
        this.completionListener = completionListener;
        this.source = source;
        this.destination = destination;
        this.copySourceItself = copySourceItself;
        this.copyStrategy = copyStrategy;

        currentFileListeners = new ArrayBlockingQueue<>(threads);
        for (int i = 0; i < threads; i++)
            currentFileListeners.add(new Utils.Pair<>(fileStatusListeners[i], fileProgressListeners[i]));

        this.executor = new ThreadPoolExecutor(1, threads - 1, 30, TimeUnit.SECONDS,  // since we have caller runs policy: caller thread is counted too, hence -1
                new LinkedBlockingDeque<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

        worker = new Thread(CopyEngine.this::run);
        worker.start();
    }

    public void interrupt() {
        executor.shutdown();
        worker.interrupt();
        auxStatusListener.status("Interrupted");
    }

    private void run() {
        try {
            running = true;
            run0();
        } catch (AbortException e) {
            e.printStackTrace();
        } finally {
            running = false;
            completionListener.run();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void run0() {
        auxStatusListener.status("Scanning source files");
        List<FileToCopy> copyList = new FilesCollector(source, destination, copySourceItself, totalStatusListener).collectListOfFiles();

        auxStatusListener.status("Checking which files should be copied");
        copyList = new StrategyFilter(copyStrategy, currentFileListeners, totalStatusListener, totalProgressListener, executor).filter(copyList);

        if (Thread.currentThread().isInterrupted())
            return;

        Optional<Long> totalBytesToCopyOptional = copyList.stream().map(FileToCopy::getSourceBytes).reduce(Long::sum);
        if (!totalBytesToCopyOptional.isPresent()) {
            auxStatusListener.status("Nothing to copy!");
            return;
        }

//        if (true) return;

        totalBytesToCopy = totalBytesToCopyOptional.get();
        totalFilesToCopy = copyList.size();
        totalFilesCopied = new AtomicInteger(0);

        auxStatusListener.status(String.format("%s to copy (%d files)",
                Utils.bytesToHumanReadable(totalBytesToCopy),
                copyList.size()));

        totalBytesCopied = new AtomicLong(0);
        startedTimestamp = System.currentTimeMillis();

        Stack<Future<?>> futures = new Stack<>();

        for (FileToCopy fileToCopy : copyList) {
            futures.push(executor.submit((Runnable) () -> {
                copy(fileToCopy);
                totalFilesCopied.incrementAndGet();
            }));

            if (Thread.currentThread().isInterrupted())
                break;
        }

        while (!futures.isEmpty() && !Thread.currentThread().isInterrupted())
            Utils.getFuture(futures.pop());

        auxStatusListener.status(String.format("Done! %s",
                Utils.millisToHumanReadable(System.currentTimeMillis() - startedTimestamp)));
    }

    private void copy(FileToCopy fileToCopy) {
        File destination = fileToCopy.getDestination();
        prepareDestDir(destination);

        long prevBytesPerSec = -1; // not shared between threads but that's fine
        Utils.Pair<StatusListener, ProgressListener> currentFileStatus = null;
        try {
            currentFileStatus = currentFileListeners.poll();

            FileChannel in = new FileInputStream(fileToCopy.getSource()).getChannel();
            FileChannel out = new FileOutputStream(fileToCopy.getDestination()).getChannel();

            currentFileStatus.a.status(fileToCopy.getSource().getAbsolutePath());

            long bytesToTransfer = fileToCopy.getSourceBytes();
            long transferred = 0;
            while (!Thread.currentThread().isInterrupted() && transferred != bytesToTransfer) {
                final long chunkSize = Math.min(TRANSFER_CHUNK_SIZE, bytesToTransfer - transferred);
                long bytesRead = in.transferTo(transferred, chunkSize, out);

                transferred += bytesRead;
                totalBytesCopied.addAndGet(bytesRead);

                long filePercent = (transferred * 100) / bytesToTransfer;
                currentFileStatus.b.onProgress((int) filePercent);

                long totalPercent = (totalBytesCopied.get() * 100) / totalBytesToCopy;
                totalProgressListener.onProgress((int) totalPercent);

                totalStatusListener.status(String.format("%s of %s (%d files left)",
                        Utils.bytesToHumanReadable(totalBytesCopied.get()),
                        Utils.bytesToHumanReadable(totalBytesToCopy),
                        totalFilesToCopy - totalFilesCopied.get()));

                long secondsPassed = (System.currentTimeMillis() - startedTimestamp) / 1000;
                if (secondsPassed > 0) {
                    long bytesPerSec = totalBytesCopied.get() / secondsPassed;
                    if (bytesPerSec != prevBytesPerSec) {
                        auxStatusListener.status(String.format("%s/sec", Utils.bytesToHumanReadable(bytesPerSec)));
                        prevBytesPerSec = bytesPerSec;
                    }
                }
            }

            out.close();
            in.close();
        } catch (IOException e) {
            // abort(e.getMessage());
            System.out.println(e.getMessage());
        } finally {
            if (currentFileStatus != null)
                currentFileListeners.add(currentFileStatus);
        }
    }

    private void prepareDestDir(File destination) {
        if (destination.exists()) {
            if (!destination.delete())
                abort("Can't remove " + destination.getAbsolutePath());
        }
        File destParent = destination.getParentFile();
        if (!destParent.exists()) {
            if (!destParent.mkdirs())
                if (!destParent.exists()) // double check cause we can have races from other threads
                    abort("Can't create " + destParent + " directory");
        } else if (!destParent.isDirectory())
            abort(destParent.getAbsolutePath() + " is not a directory");
    }

    private void abort(String msg) {
        System.out.println(msg);
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executor.shutdownNow();
        Thread.currentThread().interrupt();
        auxStatusListener.status(msg);
        throw new AbortException(msg);
    }

    private static final class AbortException extends RuntimeException {
        public AbortException(String message) {
            super(message);
        }
    }
}
