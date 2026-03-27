package ch.framedev.explorer.model;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileTreeNode extends DefaultMutableTreeNode {
    private final Path path;
    private boolean loaded;

    public FileTreeNode(Path path) {
        super(pathLabel(path));
        this.path = path;
        if (path != null && Files.isDirectory(path)) {
            add(new DefaultMutableTreeNode("Loading..."));
        }
    }

    public Path getPathValue() {
        return path;
    }

    public void loadChildren(DefaultTreeModel model) {
        if (loaded || path == null || !Files.isDirectory(path)) {
            return;
        }
        loaded = true;
        removeAllChildren();

        try (Stream<Path> stream = Files.list(path)) {
            List<Path> children = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(
                            p -> p.getFileName() != null ? p.getFileName().toString().toLowerCase(Locale.ROOT) : p.toString().toLowerCase(Locale.ROOT)
                    ))
                    .collect(Collectors.toList());

            for (Path child : children) {
                add(new FileTreeNode(child));
            }
        } catch (IOException ignored) {
        }
        model.nodeStructureChanged(this);
    }

    private static String pathLabel(Path path) {
        if (path == null) {
            return "Computer";
        }
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }
}
