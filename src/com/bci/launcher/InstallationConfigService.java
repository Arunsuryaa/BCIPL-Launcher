package com.bci.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InstallationConfigService {
    private final Path configPath;

    public InstallationConfigService() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            localAppData = System.getProperty("user.home");
        }
        configPath = Paths.get(localAppData, "BCIPL", "Launcher", "config.json");
    }

    public Path getConfigPath() { return configPath; }

    public void save(List<CreoInstallation> installations) throws IOException {
        Files.createDirectories(configPath.getParent());
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"schemaVersion\": 1,\n  \"scanCompleted\": true,\n  \"installations\": [\n");
        for (int i = 0; i < installations.size(); i++) {
            CreoInstallation item = installations.get(i);
            json.append("    {\n")
                .append("      \"version\": \"").append(escape(item.getVersion())).append("\",\n")
                .append("      \"drive\": \"").append(escape(item.getDrive())).append("\",\n")
                .append("      \"executablePath\": \"").append(escape(item.getExecutablePath())).append("\"\n")
                .append("    }");
            if (i < installations.size() - 1) json.append(',');
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Files.writeString(configPath, json.toString(), StandardCharsets.UTF_8);
    }

    public List<CreoInstallation> load() throws IOException {
        List<CreoInstallation> result = new ArrayList<>();
        if (!Files.isRegularFile(configPath)) return result;
        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        Pattern objectPattern = Pattern.compile("\\{([^{}]*)\\}");
        Matcher objectMatcher = objectPattern.matcher(json);
        while (objectMatcher.find()) {
            String object = objectMatcher.group(1);
            String path = readString(object, "executablePath");
            if (path == null || path.isBlank()) continue;
            String version = readString(object, "version");
            String drive = readString(object, "drive");
            CreoInstallation installation = new CreoInstallation(unescape(version), unescape(path), unescape(drive));
            if (installation.isAvailable() && !result.contains(installation)) result.add(installation);
        }
        return result;
    }

    private String readString(String jsonObject, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(jsonObject);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String unescape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    default: out.append(c); break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }
}
