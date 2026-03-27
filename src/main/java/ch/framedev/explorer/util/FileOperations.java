package ch.framedev.explorer.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

public final class FileOperations {

    private FileOperations() {
    }

    public static Path resolveUniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }

        String base = target.getFileName().toString();
        String name = base;
        String ext = "";
        int idx = base.lastIndexOf('.');
        if (idx > 0) {
            name = base.substring(0, idx);
            ext = base.substring(idx);
        }

        int counter = 1;
        Path candidate;
        do {
            candidate = target.resolveSibling(name + "_copy" + counter + ext);
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }

    public static void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(dir);
                    Files.createDirectories(target.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(file);
                    Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    public static void deleteRecursively(Path source) throws IOException {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.deleteIfExists(source);
        }
    }

    public static long calculateDirectorySize(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ex) {
            return 0L;
        }
    }
}
