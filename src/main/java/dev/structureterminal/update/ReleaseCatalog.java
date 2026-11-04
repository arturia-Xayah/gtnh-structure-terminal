package dev.structureterminal.update;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Only release assets belonging to our fixed repository are eligible. No server supplied download URLs. */
public final class ReleaseCatalog {

    public static final String REPOSITORY = "arturia-Xayah/gtnh-structure-terminal";
    public static final String API = "https://api.github.com/repos/" + REPOSITORY + "/releases";
    public static final String WEB = "https://github.com/" + REPOSITORY + "/releases";
    public static final long MAX_JAR = 32L * 1024 * 1024;
    private static final Pattern VERSION = Pattern
        .compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-(alpha|beta|rc)(?:[.-](\\d+))?)?");
    private static final Pattern PACK = Pattern.compile(
        "(?:^|[^0-9.])(2\\.\\d+\\.\\d+(?:-(?:RC|Beta|Alpha)-?\\d+)?)(?:$|[^A-Za-z0-9.-])",
        Pattern.CASE_INSENSITIVE);

    private ReleaseCatalog() {}

    public static boolean validVersion(String value) {
        return value != null && value.length() <= 64
            && VERSION.matcher(value)
                .matches();
    }

    public static int compare(String left, String right) {
        Matcher a = VERSION.matcher(left), b = VERSION.matcher(right);
        if (!a.matches() || !b.matches()) throw new IllegalArgumentException("Unsupported version");
        for (int i = 1; i <= 3; i++) {
            int c = new BigInteger(a.group(i)).compareTo(new BigInteger(b.group(i)));
            if (c != 0) return c;
        }
        int c = Integer.compare(stage(a.group(4)), stage(b.group(4)));
        if (c != 0) return c;
        return new BigInteger(a.group(5) == null ? "0" : a.group(5))
            .compareTo(new BigInteger(b.group(5) == null ? "0" : b.group(5)));
    }

    private static int stage(String s) {
        return s == null ? 3 : "rc".equals(s) ? 2 : "beta".equals(s) ? 1 : 0;
    }

    public static String packVersion(String text) {
        if (text == null || text.length() > 4096) return null;
        Matcher matcher = PACK.matcher(text.replaceAll("§.", ""));
        return matcher.find() ? matcher.group(1) : null;
    }

    public static Release select(String json, String current, String pack, boolean previews, String exact)
        throws IOException {
        if (pack == null || !validVersion(current) || (exact != null && !validVersion(exact))) return null;
        try {
            JsonElement parsed = new JsonParser().parse(json);
            JsonArray list = new JsonArray();
            if (parsed.isJsonArray()) list = parsed.getAsJsonArray();
            else list.add(parsed);
            Release best = null;
            for (JsonElement element : list) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.get("draft")
                    .getAsBoolean()) continue;
                String tag = obj.get("tag_name")
                    .getAsString();
                String suffix = "-GTNH-" + pack;
                if (!tag.startsWith("v") || !tag.endsWith(suffix)) continue;
                String version = tag.substring(1, tag.length() - suffix.length());
                if (!validVersion(version) || compare(version, current) <= 0) continue;
                if (exact != null ? !exact.equals(version)
                    : !previews && (obj.get("prerelease")
                        .getAsBoolean() || version.contains("-")))
                    continue;
                String filename = "gtnh-modernization-upgrade-" + version + suffix + ".jar";
                for (JsonElement asset : obj.getAsJsonArray("assets")) {
                    JsonObject file = asset.getAsJsonObject();
                    if (!filename.equals(
                        file.get("name")
                            .getAsString())
                        || !file.has("digest")
                        || file.get("digest")
                            .isJsonNull())
                        continue;
                    String digest = file.get("digest")
                        .getAsString();
                    String url = file.get("browser_download_url")
                        .getAsString();
                    long size = file.get("size")
                        .getAsLong();
                    if (!digest.matches("sha256:[a-fA-F0-9]{64}") || size <= 0
                        || size > MAX_JAR
                        || !url
                            .equals("https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/" + filename))
                        continue;
                    String notes = obj.has("body") && !obj.get("body")
                        .isJsonNull() ? obj.get("body")
                            .getAsString() : "";
                    Release next = new Release(
                        version,
                        pack,
                        url,
                        digest.substring(7)
                            .toLowerCase(java.util.Locale.ROOT),
                        size,
                        notes.substring(0, Math.min(notes.length(), 12000)));
                    if (best == null || compare(next.version, best.version) > 0) best = next;
                }
            }
            return best;
        } catch (RuntimeException error) {
            throw new IOException("Invalid release metadata", error);
        }
    }

    public static boolean trustedUrl(URI uri) {
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
            || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
        String host = uri.getHost();
        return "api.github.com".equals(host) || "github.com".equals(host)
            || "release-assets.githubusercontent.com".equals(host)
            || "objects.githubusercontent.com".equals(host);
    }

    public static final class Release {

        public final String version, pack, url, sha256, notes;
        public final long size;

        public Release(String version, String pack, String url, String sha256, long size, String notes) {
            this.version = version;
            this.pack = pack;
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
            this.notes = notes;
        }
    }
}
