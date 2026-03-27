package ch.framedev.explorer.ui;

import ch.framedev.explorer.model.FileTableModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.nio.file.Path;

public class ExplorerTableCellRenderer extends DefaultTableCellRenderer {
    private final FileTableModel tableModel;
    private final FileSystemView fileSystemView;
    private final Color rowPrimary;
    private final Color rowAlt;
    private final Color textColor;

    public ExplorerTableCellRenderer(
            FileTableModel tableModel,
            FileSystemView fileSystemView,
            Color rowPrimary,
            Color rowAlt,
            Color textColor
    ) {
        this.tableModel = tableModel;
        this.fileSystemView = fileSystemView;
        this.rowPrimary = rowPrimary;
        this.rowAlt = rowAlt;
        this.textColor = textColor;
        setBorder(new EmptyBorder(0, 6, 0, 6));
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
    ) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        int modelRow = table.convertRowIndexToModel(row);
        Path path = tableModel.getPathAt(modelRow);

        setIcon(null);
        setHorizontalAlignment(LEFT);

        if (column == 0 && path != null) {
            setText(tableModel.getNameAt(modelRow));
            setIcon(fileSystemView.getSystemIcon(path.toFile()));
            setFont(tableModel.isDirectoryAt(modelRow) ? getFont().deriveFont(Font.BOLD) : getFont().deriveFont(Font.PLAIN));
        } else if (column == 1) {
            setText(tableModel.getDisplaySizeAt(modelRow));
            setHorizontalAlignment(RIGHT);
            setFont(getFont().deriveFont(Font.PLAIN));
        } else {
            setFont(getFont().deriveFont(Font.PLAIN));
        }

        if (!isSelected) {
            setForeground(textColor);
            setBackground(row % 2 == 0 ? rowPrimary : rowAlt);
        }

        setToolTipText(path != null ? path.toString() : null);
        return this;
    }
}
