package ch.framedev.explorer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ExplorerSettings {
    private static final Path SETTINGS_DIR = Path.of(System.getProperty("user.home"), ".frame-explorer");
    private static final Path SETTINGS_FILE = SETTINGS_DIR.resolve("settings.properties");

    public String density = "COMFORTABLE";
    public String scale = "S100";
    public String theme = "OCEAN";
    public String lookAndFeelClassName = "";
    public boolean showHiddenFiles;
    public String lastDirectory = "";

    public int windowX = -1;
    public int windowY = -1;
    public int windowWidth = 1280;
    public int windowHeight = 820;
    public boolean maximized;

    public double horizontalSplitRatio = 0.26d;
    public double verticalSplitRatio = 0.80d;
    public double leftSplitRatio = 0.30d;

    public final List<String> favorites = new ArrayList<>();

    public static ExplorerSettings load() {
        ExplorerSettings settings = new ExplorerSettings();
        if (!Files.exists(SETTINGS_FILE)) {
            return settings;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
            props.load(in);
        } catch (IOException ignored) {
            return settings;
        }

        settings.density = props.getProperty("ui.density", settings.density);
        settings.scale = props.getProperty("ui.scale", settings.scale);
        settings.theme = props.getProperty("ui.theme", settings.theme);
        settings.lookAndFeelClassName = props.getProperty("ui.lookAndFeel", settings.lookAndFeelClassName);
        settings.showHiddenFiles = parseBoolean(props.getProperty("showHiddenFiles"), false);
        settings.lastDirectory = props.getProperty("lastDirectory", "");

        settings.windowX = parseInt(props.getProperty("window.x"), settings.windowX);
        settings.windowY = parseInt(props.getProperty("window.y"), settings.windowY);
        settings.windowWidth = parseInt(props.getProperty("window.width"), settings.windowWidth);
        settings.windowHeight = parseInt(props.getProperty("window.height"), settings.windowHeight);
        settings.maximized = parseBoolean(props.getProperty("window.maximized"), false);

        settings.horizontalSplitRatio = parseDouble(props.getProperty("split.horizontalRatio"), settings.horizontalSplitRatio);
        settings.verticalSplitRatio = parseDouble(props.getProperty("split.verticalRatio"), settings.verticalSplitRatio);
        settings.leftSplitRatio = parseDouble(props.getProperty("split.leftRatio"), settings.leftSplitRatio);

        int favoritesCount = parseInt(props.getProperty("favorites.count"), 0);
        for (int i = 0; i < favoritesCount; i++) {
            String value = props.getProperty("favorite." + i, "").trim();
            if (!value.isEmpty()) {
                settings.favorites.add(value);
            }
        }
        return settings;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("ui.density", density);
        props.setProperty("ui.scale", scale);
        props.setProperty("ui.theme", theme);
        props.setProperty("ui.lookAndFeel", lookAndFeelClassName == null ? "" : lookAndFeelClassName);
        props.setProperty("showHiddenFiles", Boolean.toString(showHiddenFiles));
        props.setProperty("lastDirectory", lastDirectory == null ? "" : lastDirectory);

        props.setProperty("window.x", Integer.toString(windowX));
        props.setProperty("window.y", Integer.toString(windowY));
        props.setProperty("window.width", Integer.toString(windowWidth));
        props.setProperty("window.height", Integer.toString(windowHeight));
        props.setProperty("window.maximized", Boolean.toString(maximized));

        props.setProperty("split.horizontalRatio", Double.toString(horizontalSplitRatio));
        props.setProperty("split.verticalRatio", Double.toString(verticalSplitRatio));
        props.setProperty("split.leftRatio", Double.toString(leftSplitRatio));

        props.setProperty("favorites.count", Integer.toString(favorites.size()));
        for (int i = 0; i < favorites.size(); i++) {
            props.setProperty("favorite." + i, favorites.get(i));
        }

        try {
            Files.createDirectories(SETTINGS_DIR);
            try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) {
                props.store(out, "Frame Explorer Settings");
            }
        } catch (IOException ignored) {
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }
}
