package org.kos.mycopy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class UI {
    private JTextField sourceTextField;
    private JTextField destinationTextField;
    private JButton sourceButton;
    private JButton destinationButton;
    private JCheckBox checkModificationTimeCheckBox;
    private JProgressBar totalProgressBar;
    private JLabel statusLabel;
    private JCheckBox checkSizeCheckBox;
    private JCheckBox checkHeadAndTailCheckBox;
    private JButton goButton;
    private JRadioButton copyFromSourceRadioButton;
    private JRadioButton copySourceItselfRadioButton;
    private JPanel panel;
    private JCheckBox checkHashCheckBox;
    private JSpinner threadsToUseSpinner;
    private JCheckBox checkContentCheckBox;
    private JPanel currentFilesPanel;
    private JScrollPane logScrollPane;
    private JTextArea logTextArea;

    private List<JProgressBar> currentFileProgressBars = new ArrayList<>();

    private CopyEngine copyEngine;

    private Preferences prefs = Preferences.userNodeForPackage(MyCopy.class);

    public UI() {
        loadPrefs();

        updateCurrentFileProgressBars();

        sourceButton.addActionListener(e -> chooseFile(sourceTextField, "source"));
        destinationButton.addActionListener(e -> chooseFile(destinationTextField, "destination"));

        totalProgressBar.setMinimum(0);
        totalProgressBar.setMaximum(100);
        totalProgressBar.setStringPainted(true);

        goButton.addActionListener(e -> {
            if (copyEngine != null && copyEngine.isRunning())
                copyEngine.interrupt();
            else {
                savePrefs();
                logTextArea.setText("");
                List<CopyStrategy> selectedStrategies = new ArrayList<>(5);
                if (checkModificationTimeCheckBox.isSelected())
                    selectedStrategies.add(CopyStrategies.MOD_TIME_STRATEGY);
                if (checkSizeCheckBox.isSelected())
                    selectedStrategies.add(CopyStrategies.SIZE_STRATEGY);
                if (checkHeadAndTailCheckBox.isSelected())
                    selectedStrategies.add(CopyStrategies.HEAD_TAIL_STRATEGY);
                if (checkHashCheckBox.isSelected())
                    selectedStrategies.add(CopyStrategies.HASH_STRATEGY);
                if (checkContentCheckBox.isSelected())
                    selectedStrategies.add(CopyStrategies.CONTENT_STRATEGY);

                CopyStrategy copyStrategy = CopyStrategies.combineStrategies(
                        selectedStrategies.toArray(new CopyStrategy[selectedStrategies.size()]));

                final int numThreads = numberOfThreadsToUse();
                StatusListener[] sl = new StatusListener[numThreads];
                ProgressListener[] pl = new ProgressListener[numThreads];

                for (int i = 0; i < numThreads; i++) {
                    final JProgressBar pb = currentFileProgressBars.get(i);
                    sl[i] = pb::setString;
                    pl[i] = pb::setValue;
                }

                goButton.setText("Stop!");
                setOptionsEnabled(false);
                copyEngine = new CopyEngine(
                        totalProgressBar::setValue,
                        totalProgressBar::setString,
                        pl,
                        sl,
                        msg -> {
                            statusLabel.setText(msg);
//                            System.out.println(msg);  // to send status messages to the log
                        },
                        this::onCompleted,
                        new File(sourceTextField.getText()),
                        new File(destinationTextField.getText()),
                        copySourceItselfRadioButton.isSelected(),
                        copyStrategy,
                        numberOfThreadsToUse()
                );
            }
        });

        sourceTextField.addActionListener(e -> updateGoStatus());
        destinationTextField.addActionListener(e -> updateGoStatus());

        final PrintStream logPrintStream = new PrintStream(new TextAreaOutputStream(logTextArea));
        System.setOut(logPrintStream);
        System.setErr(logPrintStream);

        enableLogAutoScroll();

        updateGoStatus();

//        goButton.addMouseWheelListener(e -> System.out.println("e = " + e)); // to generate some messages
    }

    private void enableLogAutoScroll() {
        DefaultCaret caret = (DefaultCaret) logTextArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        Document document = logTextArea.getDocument();
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                maybeScrollToBottom();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                maybeScrollToBottom();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                maybeScrollToBottom();
            }

            private void maybeScrollToBottom() {
                JScrollBar scrollBar = logScrollPane.getVerticalScrollBar();
                boolean scrollBarAtBottom = isScrollBarFullyExtended(scrollBar);
                boolean scrollLock = Toolkit.getDefaultToolkit()
                        .getLockingKeyState(KeyEvent.VK_SCROLL_LOCK);
                if (scrollBarAtBottom && !scrollLock) {
                    // Push the call to "scrollToBottom" back TWO PLACES on the
                    // AWT-EDT queue so that it runs *after* Swing has had an
                    // opportunity to "react" to the appending of new text:
                    // this ensures that we "scrollToBottom" only after a new
                    // bottom has been recalculated during the natural
                    // revalidation of the GUI that occurs after having
                    // appending new text to the JTextArea.
                    EventQueue.invokeLater(() -> EventQueue.invokeLater(() -> scrollToBottom(logTextArea)));
                }
            }

            private boolean isScrollBarFullyExtended(JScrollBar vScrollBar) {
                BoundedRangeModel model = vScrollBar.getModel();
                return (model.getExtent() + model.getValue()) == model.getMaximum();
            }

            private void scrollToBottom(JComponent component) {
                Rectangle visibleRect = component.getVisibleRect();
                visibleRect.y = component.getHeight() - visibleRect.height;
                component.scrollRectToVisible(visibleRect);
            }
        });
    }

    private void loadPrefs() {
        sourceTextField.setText(prefs.get("source", ""));
        destinationTextField.setText(prefs.get("dest", ""));
        checkModificationTimeCheckBox.setSelected(prefs.getBoolean("checkModTime", false));
        checkHeadAndTailCheckBox.setSelected(prefs.getBoolean("checkHeadTail", false));
        checkSizeCheckBox.setSelected(prefs.getBoolean("checkSize", true));
        copySourceItselfRadioButton.setSelected(prefs.getBoolean("copySourceItself", false));
        copyFromSourceRadioButton.setSelected(!prefs.getBoolean("copySourceItself", true));
        checkHashCheckBox.setSelected(prefs.getBoolean("checkHash", false));
        checkContentCheckBox.setSelected(prefs.getBoolean("checkContent", false));
        threadsToUseSpinner.setValue(prefs.getInt("threads", getDefaultNumberOfThreads(Runtime.getRuntime().availableProcessors())));
    }

    private void savePrefs() {
        prefs.put("source", sourceTextField.getText());
        prefs.put("dest", destinationTextField.getText());
        prefs.putBoolean("checkModTime", checkModificationTimeCheckBox.isSelected());
        prefs.putBoolean("checkHeadTail", checkHeadAndTailCheckBox.isSelected());
        prefs.putBoolean("checkSize", checkSizeCheckBox.isSelected());
        prefs.putBoolean("copySourceItself", !copyFromSourceRadioButton.isSelected());
        prefs.putBoolean("checkHash", checkHashCheckBox.isSelected());
        prefs.putBoolean("checkContent", checkContentCheckBox.isSelected());
        prefs.putInt("threads", numberOfThreadsToUse());
    }

    public void onClose() {
        if (copyEngine != null && copyEngine.isRunning())
            copyEngine.interrupt();
        savePrefs();
    }

    public JPanel getPanel() {
        return panel;
    }

    private void chooseFile(JTextField curSourceTextField, String title) {
        JFileChooser ch;
        String curSource = curSourceTextField.getText();
        ch = curSource == null || curSource.isEmpty() ? new JFileChooser() : new JFileChooser(curSource);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int retVal = ch.showDialog(panel, "Choose " + title);
        if (retVal == JFileChooser.APPROVE_OPTION) {
            File f = ch.getSelectedFile();
            curSourceTextField.setText(f.getAbsolutePath());
            updateGoStatus();
        }
    }

    private int numberOfThreadsToUse() {
        return (int) threadsToUseSpinner.getValue();
    }

    private void updateGoStatus() {
        // a bit of indian code
        String s = sourceTextField.getText();
        if (s == null || s.isEmpty())
            goButton.setEnabled(false);
        else {
            if (!(new File(s).exists()))
                goButton.setEnabled(false);
            else {
                s = destinationTextField.getText();
                if (s == null || s.isEmpty())
                    goButton.setEnabled(false);
                else
                    goButton.setEnabled(new File(s).exists());
            }
        }
    }

    private void setOptionsEnabled(boolean enabled) {
        JComponent[] components = new JComponent[]{
                sourceTextField,
                destinationTextField,
                sourceButton,
                destinationButton,
                checkModificationTimeCheckBox,
                checkHeadAndTailCheckBox,
                checkHashCheckBox,
                checkContentCheckBox,
                threadsToUseSpinner,
                checkSizeCheckBox,
                copyFromSourceRadioButton,
                copySourceItselfRadioButton
        };

        for (JComponent component : components) {
            component.setEnabled(enabled);
        }
    }

    private void onCompleted() {
        goButton.setText("Go!");
        setOptionsEnabled(true);
    }

    private void createUIComponents() {
        int cores = Runtime.getRuntime().availableProcessors();
        int defaultThreads = getDefaultNumberOfThreads(cores);

        threadsToUseSpinner = new JSpinner(new SpinnerNumberModel(defaultThreads, 1, cores, 1));
        currentFilesPanel = new JPanel();
        currentFilesPanel.setLayout(new BoxLayout(currentFilesPanel, BoxLayout.Y_AXIS));

        threadsToUseSpinner.addChangeListener(e -> updateCurrentFileProgressBars());
    }

    private void updateCurrentFileProgressBars() {
        int numThreads = numberOfThreadsToUse();

        if (numThreads != currentFileProgressBars.size()) {
            while (currentFileProgressBars.size() < numThreads) {
                JProgressBar progressBar = new JProgressBar(0, 100);
                progressBar.setStringPainted(true);
                currentFileProgressBars.add(progressBar);
            }

            while (currentFileProgressBars.size() > numThreads) {
                currentFileProgressBars.remove(currentFileProgressBars.size() - 1);
            }

            currentFilesPanel.removeAll();
            for (JProgressBar fileProgressBar : currentFileProgressBars) {
                currentFilesPanel.add(fileProgressBar);
            }
            currentFilesPanel.updateUI();
        }
    }

    private int getDefaultNumberOfThreads(int cores) {
        return Math.min(2, cores);
    }
}
