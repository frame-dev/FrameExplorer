package ch.framedev.explorer.model;

import javax.swing.table.AbstractTableModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Name", "Size", "Type", "Modified"};
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.##");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 1 -> Long.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Entry entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> entry.name;
            case 1 -> entry.rawSize;
            case 2 -> entry.type;
            case 3 -> entry.modified;
            default -> "";
        };
    }

    public Path getPathAt(int modelRow) {
        if (modelRow < 0 || modelRow >= entries.size()) {
            return null;
        }
        return entries.get(modelRow).path;
    }

    public String getNameAt(int modelRow) {
        if (modelRow < 0 || modelRow >= entries.size()) {
            return "";
        }
        return entries.get(modelRow).name;
    }

    public boolean isDirectoryAt(int modelRow) {
        if (modelRow < 0 || modelRow >= entries.size()) {
            return false;
        }
        return entries.get(modelRow).directory;
    }

    public String getDisplaySizeAt(int modelRow) {
        if (modelRow < 0 || modelRow >= entries.size()) {
            return "";
        }
        Entry entry = entries.get(modelRow);
        return entry.directory ? "<DIR>" : formatSize(entry.rawSize);
    }

    public void setDirectory(Path directory) {
        setDirectory(directory, false);
    }

    public void setDirectory(Path directory, boolean showHidden) {
        entries.clear();

        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> sorted = stream
                    .filter(path -> showHidden || !isHidden(path))
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) {
                            return aDir ? -1 : 1;
                        }
                        String aName = a.getFileName() == null ? a.toString() : a.getFileName().toString();
                        String bName = b.getFileName() == null ? b.toString() : b.getFileName().toString();
                        return aName.compareToIgnoreCase(bName);
                    })
                    .collect(Collectors.toList());

            for (Path path : sorted) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    long rawSize = attrs.isDirectory() ? 0L : attrs.size();
                    boolean directoryEntry = attrs.isDirectory();
                    String type = directoryEntry ? "Directory" : "File";
                    String modified = formatDate(attrs.lastModifiedTime());
                    String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
                    entries.add(new Entry(path, name, rawSize, type, modified, directoryEntry));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        fireTableDataChanged();
    }

    public int getEntryCount() {
        return entries.size();
    }

    public long getTotalFileSize() {
        return entries.stream().filter(entry -> !entry.directory).mapToLong(entry -> entry.rawSize).sum();
    }

    public static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        double value = size;
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int index = -1;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return SIZE_FORMAT.format(value) + " " + units[index];
    }

    public static String formatDate(FileTime time) {
        return DATE_FORMAT.format(new Date(time.toMillis()));
    }

    private static boolean isHidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (Exception ex) {
            return false;
        }
    }

    private record Entry(Path path, String name, long rawSize, String type, String modified, boolean directory) {
    }
}
