package org.kos.mycopy;


import java.io.File;

public class FileToCopy {
    private final File source;
    private final File destination;
    private final long sourceBytes;

    public FileToCopy(File source, File destination) {
        this.source = source;
        this.destination = destination;
        sourceBytes = source.length();
//        System.out.println(source.getAbsoluteFile() + " -> " + destination.getAbsolutePath());
    }

    public File getSource() {
        return source;
    }

    public File getDestination() {
        return destination;
    }

    public long getSourceBytes() {
        return sourceBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileToCopy that = (FileToCopy) o;

        return destination.equals(that.destination) && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + destination.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "FileToCopy{" + source + " -> " + destination + " (" + sourceBytes + ')';
    }
}
