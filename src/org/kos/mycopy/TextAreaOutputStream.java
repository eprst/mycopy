package org.kos.mycopy;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;

public class TextAreaOutputStream extends OutputStream {
    private final JTextArea textArea;

    public TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(int b) throws IOException {
        // redirects data to the text area
        textArea.append(String.valueOf((char) b));
    }

    @Override
    public void write(byte[] b) throws IOException {
        textArea.append(new String(b));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        textArea.append(new String(b, off, len));
    }
}
