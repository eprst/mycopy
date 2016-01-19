package org.kos.mycopy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StrategyFilter {
    private final CopyStrategy copyStrategy;
    private ArrayBlockingQueue<Utils.Pair<StatusListener, ProgressListener>> currentFileListeners;
    private final StatusListener totalStatusListener;
    private final ProgressListener totalProgressListener;
    private final ExecutorService executor;

    private int totalSourceFiles;
    private final AtomicInteger totalFilesScanned = new AtomicInteger();
    private final AtomicInteger totalFilesToCopy = new AtomicInteger();
    private final AtomicLong totalBytesToCopy = new AtomicLong();

    public StrategyFilter(CopyStrategy copyStrategy,
                          ArrayBlockingQueue<Utils.Pair<StatusListener, ProgressListener>> currentFileListeners,
                          StatusListener totalStatusListener,
                          ProgressListener totalProgressListener,
                          ExecutorService executor
    ) {
        this.copyStrategy = copyStrategy;
        this.currentFileListeners = currentFileListeners;
        this.totalStatusListener = totalStatusListener;
        this.totalProgressListener = totalProgressListener;
        this.executor = executor;
    }

    public List<FileToCopy> filter(List<FileToCopy> filesToCopy) {
        totalSourceFiles = filesToCopy.size();
        totalFilesScanned.set(0);
        totalFilesToCopy.set(totalSourceFiles);
        totalBytesToCopy.set(0);

        final List<Future<FileToCopy>> futures = new ArrayList<>();
        for (FileToCopy fileToCopy : filesToCopy) {
            futures.add(executor.submit(() -> shouldCopy(fileToCopy)));

            if (Thread.currentThread().isInterrupted())
                return Collections.emptyList();
        }

        final List<FileToCopy> res = new ArrayList<>();
        for (Future<FileToCopy> future : futures) {
            FileToCopy fileToCopy = Utils.getFuture(future);

            if (fileToCopy != null)
                res.add(fileToCopy);

            if (Thread.currentThread().isInterrupted())
                return Collections.emptyList();
        }

        return res;
    }

    private FileToCopy shouldCopy(final FileToCopy fileToCopy) {
        if (Thread.currentThread().isInterrupted())
            return null;

        Utils.Pair<StatusListener, ProgressListener> currentFileListener = currentFileListeners.poll();
        if (currentFileListener == null) {
            System.out.println("oops");
            System.exit(-1);
        }

        File src = fileToCopy.getSource();
        currentFileListener.a.status("Checking " + src.getAbsolutePath());
        final File dstFile = fileToCopy.getDestination();

        boolean shouldCopy = copyStrategy.shouldCopy(src, dstFile, executor);
        if (shouldCopy) {
            totalBytesToCopy.addAndGet(fileToCopy.getSourceBytes());
        } else {
            totalFilesToCopy.decrementAndGet();
        }

        totalFilesScanned.incrementAndGet();
        if (totalProgressListener != null) {
            int progress = (totalFilesScanned.get() * 100) / totalSourceFiles;
            totalProgressListener.onProgress(progress);
        }

        totalStatusListener.status(String.format("0 of %s (%d files left)",
                Utils.bytesToHumanReadable(totalBytesToCopy.get()), totalFilesToCopy.get()));

        try {
            currentFileListeners.put(currentFileListener);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return shouldCopy ? fileToCopy : null;
    }

}
