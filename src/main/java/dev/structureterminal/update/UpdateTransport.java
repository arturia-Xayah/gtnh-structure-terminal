package dev.structureterminal.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;
import java.util.zip.ZipFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class UpdateTransport {

    private UpdateTransport() {}

    private static HttpURLConnection open(String url) throws IOException {
        URI uri = URI.create(url);
        for (int redirects = 0; redirects < 5; redirects++) {
            if (!ReleaseCatalog.trustedUrl(uri)) throw new IOException("Untrusted update host");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL()
                .openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "GTNH-Modernization-Updater");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException error) {
                connection.disconnect();
                throw error;
            }
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Missing redirect");
                uri = uri.resolve(location);
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new IOException("Update HTTP " + status);
            }
            return connection;
        }
        throw new IOException("Too many redirects");
    }

    public static String metadata(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            transfer(in, out, 2 * 1024 * 1024, new AtomicBoolean(), n -> {}, 15000);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    public static void download(ReleaseCatalog.Release release, Path path, AtomicBoolean cancel, LongConsumer progress)
        throws IOException {
        HttpURLConnection c = open(release.url);
        boolean success = false;
        try {
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(path)) {
                long size = transfer(in, out, release.size, cancel, progress, 120000);
                if (size != release.size) throw new IOException("Incomplete download");
            }
            if (!UpdateInstaller.sha256(path)
                .equals(release.sha256)) throw new IOException("SHA-256 mismatch");
            verifyJar(path, release.version, release.pack);
            success = true;
        } finally {
            c.disconnect();
            if (!success) Files.deleteIfExists(path);
        }
    }

    static long transfer(InputStream in, OutputStream out, long limit, AtomicBoolean cancel, LongConsumer progress,
        long timeout) throws IOException {
        long count = 0, start = System.nanoTime();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (cancel.get() || Thread.currentThread()
                .isInterrupted()) throw new IOException("Cancelled");
            if ((System.nanoTime() - start) / 1000000 > timeout) throw new IOException("Update timed out");
            count += n;
            if (count > limit) throw new IOException("Update exceeds size limit");
            out.write(buffer, 0, n);
            progress.accept(count);
        }
        if (cancel.get()) throw new IOException("Cancelled");
        return count;
    }

    public static void verifyJar(Path path, String version, String pack) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            String[] required = { "dev/structureterminal/StructureTerminal.class",
                "dev/aeoptimizer/OrderOptimizer.class", "dev/plasmaforgepower/PlasmaForgePower.class",
                "dev/aestoragevault/VaultMod.class", "modernization-update.json" };
            for (String entry : required)
                if (zip.getEntry(entry) == null) throw new IOException("Not a modernization installation JAR");
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement()
                    .getName();
                if (name.contains("/packtest/") || name.contains("/servertest/"))
                    throw new IOException("Test JAR rejected");
            }
            try (InputStream in = zip.getInputStream(zip.getEntry("modernization-update.json"));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                transfer(in, out, 16384, new AtomicBoolean(), n -> {}, 1000);
                JsonObject descriptor = new JsonParser().parse(new String(out.toByteArray(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
                if (descriptor.get("schema")
                    .getAsInt() != 1
                    || !version.equals(
                        descriptor.get("version")
                            .getAsString())
                    || !pack.equals(
                        descriptor.get("gtnh")
                            .getAsString()))
                    throw new IOException("Incompatible update JAR");
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid update JAR", error);
        }
    }
}
