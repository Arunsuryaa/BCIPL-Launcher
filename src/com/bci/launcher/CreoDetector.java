package com.bci.launcher;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;

public final class CreoDetector {
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?i)creo[ _-]*([0-9]{1,2}(?:\\.[0-9]+)?)");
    private static final int MAX_DEPTH = 12;

    public interface ScanListener {
        void onProgress(String message);
        boolean isCancelled();
    }

    public List<CreoInstallation> scanAllAccessibleDrives(ScanListener listener) {
        Map<String, CreoInstallation> found = new LinkedHashMap<>();

        // Check common PTC locations first, then perform a broader drive scan.
        List<Path> roots = new ArrayList<>();
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String systemDrive = System.getenv("SystemDrive");

        addIfDirectory(roots, programFiles == null ? null : Path.of(programFiles, "PTC"));
        addIfDirectory(roots, programFilesX86 == null ? null : Path.of(programFilesX86, "PTC"));
        addIfDirectory(roots, systemDrive == null ? null : Path.of(systemDrive + "\\PTC"));

        for (Path root : File.listRoots().length == 0
                ? List.<Path>of()
                : toPaths(File.listRoots())) {
            if (listener.isCancelled()) break;
            if (!Files.isDirectory(root)) continue;
            listener.onProgress("Scanning drive " + root + " ...");
            scanRoot(root, found, listener);
        }

        List<CreoInstallation> results = new ArrayList<>(found.values());
        results.sort(Comparator.comparing(CreoInstallation::getVersion,
                String.CASE_INSENSITIVE_ORDER).thenComparing(CreoInstallation::getExecutablePath));
        return results;
    }

    private void scanRoot(Path root, Map<String, CreoInstallation> found, ScanListener listener) {
        try {
            Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                    MAX_DEPTH, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (listener.isCancelled()) return FileVisitResult.TERMINATE;
                    if (!dir.equals(root) && shouldSkipDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (listener.isCancelled()) return FileVisitResult.TERMINATE;
                    if (attrs.isRegularFile()
                            && file.getFileName() != null
                            && "parametric.exe".equalsIgnoreCase(file.getFileName().toString())) {
                        addCandidate(file, found);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Access denied, disconnected drives and locked folders are expected.
                    // Skip them without elevating privileges or stopping the entire scan.
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
        } catch (IOException | SecurityException ignored) {
            // Continue with the other drives.
        }
    }

    private boolean shouldSkipDirectory(Path dir) {
        Path namePath = dir.getFileName();
        if (namePath == null) return false;
        String name = namePath.toString().toLowerCase(Locale.ROOT);
        if (name.equals("$recycle.bin") || name.equals("system volume information")
                || name.equals("recycler") || name.equals("config.msi")) return true;

        String full = dir.toString().toLowerCase(Locale.ROOT);
        // Skip very large Windows internals; common application folders remain scannable.
        return full.contains("\\windows\\winsxs")
                || full.contains("\\windows\\softwaredistribution")
                || full.contains("\\windows\\installer")
                || full.contains("\\$recycle.bin")
                || full.contains("\\system volume information");
    }

    private void addCandidate(Path executable, Map<String, CreoInstallation> found) {
        try {
            Path normalized = executable.toRealPath();
            if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) return;
            String path = normalized.toString();
            String lower = path.toLowerCase(Locale.ROOT);

            // Avoid unrelated executables with the same filename.
            if (!lower.contains("\\parametric\\bin\\parametric.exe")
                    && !lower.contains("/parametric/bin/parametric.exe")) return;

            String version = inferVersion(normalized);
            String drive = normalized.getRoot() == null ? "" : normalized.getRoot().toString();
            CreoInstallation item = new CreoInstallation(version, path, drive);
            found.putIfAbsent(path.toLowerCase(Locale.ROOT), item);
        } catch (IOException | SecurityException ignored) {
            // Candidate became unavailable or is inaccessible.
        }
    }

    private String inferVersion(Path executable) {
        for (Path part : executable) {
            Matcher matcher = VERSION_PATTERN.matcher(part.toString());
            if (matcher.find()) return matcher.group(1);
        }
        return "Unknown";
    }

    private static void addIfDirectory(List<Path> roots, Path path) {
        if (path != null && Files.isDirectory(path)) roots.add(path);
    }

    private static List<Path> toPaths(File[] roots) {
        List<Path> result = new ArrayList<>();
        for (File root : roots) result.add(root.toPath());
        return result;
    }
}
