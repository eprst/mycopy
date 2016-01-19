package org.kos.mycopy;

import java.io.File;
import java.util.concurrent.ExecutorService;

public interface CopyStrategy {
    boolean shouldCopy(File src, File dst, ExecutorService executor);
}
