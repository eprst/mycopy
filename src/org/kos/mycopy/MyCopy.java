package org.kos.mycopy;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MyCopy {
    public static void main(String[] args) {
        UI ui = new UI();
        JFrame frame = new JFrame();
        frame.setContentPane(ui.getPanel());
        frame.setTitle("MyCopy");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ui.onClose();
            }
        });
        frame.setVisible(true);
    }
}
