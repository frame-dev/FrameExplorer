package ch.framedev;

import ch.framedev.explorer.ui.FileExplorerFrame;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setSystemLookAndFeel();
            new FileExplorerFrame().setVisible(true);
        });
    }

    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}
