package com.bci.launcher;

import java.io.File;
import java.util.Objects;

public final class CreoInstallation {
    private final String version;
    private final String executablePath;
    private final String drive;

    public CreoInstallation(String version, String executablePath, String drive) {
        this.version = version == null || version.isBlank() ? "Unknown" : version;
        this.executablePath = Objects.requireNonNull(executablePath, "executablePath");
        this.drive = drive == null ? "" : drive;
    }

    public String getVersion() { return version; }
    public String getExecutablePath() { return executablePath; }
    public String getDrive() { return drive; }

    public boolean isAvailable() {
        File executable = new File(executablePath);
        return executable.isFile() && executable.canRead();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CreoInstallation)) return false;
        CreoInstallation other = (CreoInstallation) obj;
        return executablePath.equalsIgnoreCase(other.executablePath);
    }

    @Override
    public int hashCode() {
        return executablePath.toLowerCase(java.util.Locale.ROOT).hashCode();
    }
}
