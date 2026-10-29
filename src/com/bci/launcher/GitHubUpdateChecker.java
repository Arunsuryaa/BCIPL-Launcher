package com.bci.launcher;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the latest public GitHub Release without blocking the Swing UI. */
public final class GitHubUpdateChecker {
    private static final String RELEASE_API =
            "https://api.github.com/repos/Arunsuryaa/BCIPL-Launcher/releases/latest";
    private static final String RELEASE_PAGE =
            "https://github.com/Arunsuryaa/BCIPL-Launcher/releases/latest";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateInfo check(String currentVersion) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASE_API))
                .timeout(Duration.ofSeconds(7))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BCIPL-Launcher")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned HTTP " + response.statusCode());
        }

        String json = response.body();
        String tag = find(json, "\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        if (tag == null || tag.isBlank()) {
            throw new IOException("The latest release has no tag_name.");
        }
        String latestVersion = tag.replaceFirst("(?i)^v", "").trim();
        String downloadUrl = findExeAsset(json);
        if (downloadUrl == null) downloadUrl = RELEASE_PAGE;

        return new UpdateInfo(latestVersion, downloadUrl,
                isNewerVersion(latestVersion, currentVersion));
    }

    private static String findExeAsset(String json) {
        Pattern assetPattern = Pattern.compile(
                "\\{[^{}]*\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.exe)\\\"[^{}]*}",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = assetPattern.matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\/", "/") : null;
    }

    private static String find(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean isNewerVersion(String latest, String current) {
        int[] a = versionParts(latest);
        int[] b = versionParts(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av > bv;
        }
        return false;
    }

    private static int[] versionParts(String version) {
        String clean = version == null ? "0" : version.trim().replaceFirst("(?i)^v", "");
        String[] pieces = clean.split("[.-]");
        int[] result = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            Matcher number = Pattern.compile("^\\d+").matcher(pieces[i]);
            if (number.find()) {
                try { result[i] = Integer.parseInt(number.group()); }
                catch (NumberFormatException ignored) { result[i] = 0; }
            }
        }
        return result;
    }

    public static void openDownload(String url) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("This computer cannot open a browser automatically. Visit " + RELEASE_PAGE);
        }
        Desktop.getDesktop().browse(URI.create(url == null ? RELEASE_PAGE : url));
    }

    public static final class UpdateInfo {
        private final String latestVersion;
        private final String downloadUrl;
        private final boolean updateAvailable;

        UpdateInfo(String latestVersion, String downloadUrl, boolean updateAvailable) {
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.updateAvailable = updateAvailable;
        }
        public String getLatestVersion() { return latestVersion; }
        public String getDownloadUrl() { return downloadUrl; }
        public boolean isUpdateAvailable() { return updateAvailable; }
    }
}
