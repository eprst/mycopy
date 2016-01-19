package org.kos.mycopy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilesCollector {
    private final File source;
    private final File destination;
    private final boolean copySourceItself;
    private final StatusListener statusListener;

    int totalFilesScanned;
    long totalBytesToCopy;

    public FilesCollector(File source,
                          File destination,
                          boolean copySourceItself,
                          StatusListener statusListener) {
        this.source = source;
        this.destination = destination;
        this.copySourceItself = copySourceItself;
        this.statusListener = statusListener;
    }

    public List<FileToCopy> collectListOfFiles() {
        totalFilesScanned = 0;
        totalBytesToCopy = 0;

        List<FileToCopy> res = new ArrayList<>();
        if (copySourceItself)
            scan(source, destination, res);
        else {
            File[] srcContents = source.listFiles();
            if (srcContents != null)
                for (File s : srcContents)
                    scan(s, destination, res);
        }
        return res;
    }

    private void scan(File src, File dst, List<FileToCopy> acc) {
        if (Thread.currentThread().isInterrupted())
            return;

        File dstFile = new File(dst, src.getName());
        if (!src.isDirectory()) {
            acc.add(new FileToCopy(src, dstFile));
            totalBytesToCopy += src.length();
            totalFilesScanned++;
        } else {
            File[] srcContents = src.listFiles();
            if (srcContents != null) {
                statusListener.status("Scanning " + src.getAbsolutePath());
                for (File srcFile : srcContents) {
                    scan(srcFile, dstFile, acc);
                }
            }
        }

//        statusListener.status(String.format("0 of %s (%d files left)",
//                Utils.bytesToHumanReadable(totalBytesToCopy), acc.size()));
    }
}
