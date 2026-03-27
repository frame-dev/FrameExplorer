package ch.framedev.explorer.ui;

public enum UiDensity {
    COMPACT("Compact", 22, 22, 11),
    COMFORTABLE("Comfortable", 27, 24, 12),
    SPACIOUS("Spacious", 32, 28, 13);

    public final String label;
    public final int tableRowHeight;
    public final int treeRowHeight;
    public final int fontSize;

    UiDensity(String label, int tableRowHeight, int treeRowHeight, int fontSize) {
        this.label = label;
        this.tableRowHeight = tableRowHeight;
        this.treeRowHeight = treeRowHeight;
        this.fontSize = fontSize;
    }

    @Override
    public String toString() {
        return label;
    }
}
