package com.bci.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CreoLauncher {
    public Process launch(CreoInstallation installation) throws IOException {
        Path executable = Path.of(installation.getExecutablePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable) || !Files.isReadable(executable)) {
            throw new IOException("The Creo executable is missing or cannot be read:\n" + executable);
        }
        String lower = executable.toString().toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith("\\parametric\\bin\\parametric.exe")
                && !lower.endsWith("/parametric/bin/parametric.exe")) {
            throw new IOException("The selected file does not look like a Creo Parametric executable.");
        }
        return new ProcessBuilder(executable.toString())
                .directory(executable.getParent().getParent().toFile())
                .start();
    }
}
