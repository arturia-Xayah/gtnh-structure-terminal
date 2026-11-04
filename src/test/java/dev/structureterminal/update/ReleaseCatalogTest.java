package dev.structureterminal.update;

import static org.junit.Assert.*;

import java.net.URI;

import org.junit.Test;

public class ReleaseCatalogTest {

    static String release(String version, String pack, boolean preview) {
        String tag = "v" + version + "-GTNH-" + pack;
        String file = "gtnh-modernization-upgrade-" + version + "-GTNH-" + pack + ".jar";
        return "{\"draft\":false,\"prerelease\":" + preview
            + ",\"tag_name\":\""
            + tag
            + "\",\"body\":\"notes\",\"assets\":[{\"name\":\""
            + file
            + "\",\"size\":512,\"digest\":\"sha256:"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            + "\",\"browser_download_url\":\"https://github.com/"
            + ReleaseCatalog.REPOSITORY
            + "/releases/download/"
            + tag
            + "/"
            + file
            + "\"}]}";
    }

    @Test
    public void semanticOrderingIncludesStagesAndNumericalParts() {
        String[] ordered = { "0.3.2-alpha", "0.3.2-alpha.2", "0.3.2-alpha.10", "0.3.2-beta", "0.3.2-rc.1", "0.3.2",
            "0.3.10", "0.10.0", "1.0.0" };
        for (int i = 1; i < ordered.length; i++) assertTrue(ReleaseCatalog.compare(ordered[i - 1], ordered[i]) < 0);
        assertEquals(0, ReleaseCatalog.compare("0.3.2-alpha", "0.3.2-alpha.0"));
        assertFalse(ReleaseCatalog.validVersion("../../file"));
        assertFalse(ReleaseCatalog.validVersion("1.0.0+unknown"));
    }

    @Test
    public void detectsRealMenuVersionAndRcWithoutTruncation() {
        assertEquals("2.9.0", ReleaseCatalog.packVersion("§bGT New Horizons 2.9.0\n"));
        assertEquals("2.9.0-RC-2", ReleaseCatalog.packVersion("GTNH 2.9.0-RC-2"));
        assertNull(ReleaseCatalog.packVersion("GTNH 2.9.0-unsupported"));
        assertNull(ReleaseCatalog.packVersion("unknown"));
    }

    @Test
    public void choosesNewestCompatibleVersionNotListOrder() throws Exception {
        String releases = "[" + release("0.3.3-alpha", "2.9.0", true)
            + ","
            + release("0.4.0", "2.10.0", false)
            + ","
            + release("0.3.10-alpha", "2.9.0", true)
            + "]";
        assertEquals("0.3.10-alpha", ReleaseCatalog.select(releases, "0.3.2-alpha", "2.9.0", true, null).version);
        assertNull(ReleaseCatalog.select(releases, "0.3.2-alpha", "2.9.0", false, null));
        assertNull(ReleaseCatalog.select(releases, "0.3.2-alpha", null, true, null));
    }

    @Test
    public void serverExactVersionOverridesPreviewChannelButNeverSubstitutesLatest() throws Exception {
        String releases = "[" + release("0.3.3-alpha", "2.9.0", true) + "," + release("0.4.0", "2.9.0", false) + "]";
        assertEquals(
            "0.3.3-alpha",
            ReleaseCatalog.select(releases, "0.3.2-alpha", "2.9.0", false, "0.3.3-alpha").version);
        assertNull(ReleaseCatalog.select(releases, "0.3.2-alpha", "2.9.0", true, "0.3.4-alpha"));
        assertNull(ReleaseCatalog.select(releases, "0.4.0", "2.9.0", true, "0.3.3-alpha"));
    }

    @Test
    public void rejectsDraftsWrongSourcesUnverifiedAndOversizedFiles() throws Exception {
        String original = release("0.3.3-alpha", "2.9.0", true);
        String[] invalid = { original.replace("\"draft\":false", "\"draft\":true"), original.replace("sha256:", "md5:"),
            original.replace("\"size\":512", "\"size\":33554433"), original.replace("github.com/", "evil.example/"),
            original.replace("\"digest\":\"sha256:", "\"missingDigest\":\"sha256:") };
        for (String json : invalid) assertNull(ReleaseCatalog.select(json, "0.3.2-alpha", "2.9.0", true, null));
    }

    @Test
    public void redirectsAreRestrictedToTlsGithubAssetHosts() {
        assertTrue(ReleaseCatalog.trustedUrl(URI.create("https://release-assets.githubusercontent.com/a?token=abc")));
        for (String url : new String[] { "http://github.com/a", "https://github.com.evil.example/a",
            "https://user@github.com/a", "file:///tmp/a", "https://github.com:8443/a" })
            assertFalse(ReleaseCatalog.trustedUrl(URI.create(url)));
    }
}
