package ch.framedev;

import ch.framedev.explorer.ui.FileExplorerFrame;
import ch.framedev.explorer.util.ExplorerSettings;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ExplorerSettings settings = ExplorerSettings.load();
            setConfiguredLookAndFeel(settings.lookAndFeelClassName);
            new FileExplorerFrame().setVisible(true);
        });
    }

    private static void setConfiguredLookAndFeel(String lookAndFeelClassName) {
        try {
            if (lookAndFeelClassName != null && !lookAndFeelClassName.isBlank()) {
                UIManager.setLookAndFeel(lookAndFeelClassName);
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception ignored) {
        }
    }
}
