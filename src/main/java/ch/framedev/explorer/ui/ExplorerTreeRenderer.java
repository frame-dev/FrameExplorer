package ch.framedev.explorer.ui;

import ch.framedev.explorer.model.FileTreeNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class ExplorerTreeRenderer extends DefaultTreeCellRenderer {
    private final FileSystemView fileSystemView;

    public ExplorerTreeRenderer(FileSystemView fileSystemView) {
        this.fileSystemView = fileSystemView;
        setBorderSelectionColor(null);
    }

    public void setThemeColors(Color background, Color selectionBackground, Color textColor, Color selectionTextColor) {
        setBackgroundNonSelectionColor(background);
        setBackgroundSelectionColor(selectionBackground);
        setTextNonSelectionColor(textColor);
        setTextSelectionColor(selectionTextColor);
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
    ) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof FileTreeNode fileTreeNode && fileTreeNode.getPathValue() != null) {
            setIcon(fileSystemView.getSystemIcon(fileTreeNode.getPathValue().toFile()));
        }
        setBorder(new EmptyBorder(2, 3, 2, 3));
        return this;
    }
}
