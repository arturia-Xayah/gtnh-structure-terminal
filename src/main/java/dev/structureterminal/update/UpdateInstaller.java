package dev.structureterminal.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.zip.ZipFile;

/** Separate Java process; deliberately has no Minecraft, Forge, Gson or logging dependencies. */
public final class UpdateInstaller {

    private UpdateInstaller() {}

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] bytes = new byte[16384];
                int n;
                while ((n = in.read(bytes)) != -1) digest.update(bytes, 0, n);
            }
            StringBuilder result = new StringBuilder();
            for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static Properties load(Path file) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        return p;
    }

    public static void save(Path file, Properties values) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            values.store(out, "GTNH Modernization updater");
        }
    }

    public static Path installedJar(Path source) throws IOException {
        Path real = source.toRealPath();
        Path parent = real.getParent();
        if (!Files.isRegularFile(real) || !real.getFileName()
            .toString()
            .endsWith(".jar")
            || !(parent.getFileName()
                .toString()
                .equals("mods")
                || (parent.getFileName()
                    .toString()
                    .equals("1.7.10")
                    && parent.getParent()
                        .getFileName()
                        .toString()
                        .equals("mods"))))
            throw new IOException("Installation JAR must be inside mods or mods/1.7.10");
        return real;
    }

    public static void rejectDuplicates(Path source) throws IOException {
        Path parent = source.getParent();
        Path mods = parent.getFileName()
            .toString()
            .equals("mods") ? parent : parent.getParent();
        try (java.util.stream.Stream<Path> paths = Files.walk(mods, 2)) {
            java.util.Iterator<Path> it = paths.iterator();
            while (it.hasNext()) {
                Path file = it.next();
                if (!Files.isRegularFile(file) || !file.toString()
                    .endsWith(".jar") || Files.isSameFile(file, source)) continue;
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    if (zip.getEntry("dev/structureterminal/StructureTerminal.class") != null)
                        throw new IOException("Duplicate modernization JAR: " + file.getFileName());
                } catch (java.util.zip.ZipException ignored) { /* Forge owns diagnostics for unrelated broken JARs. */ }
            }
        }
    }

    /** All validation happens before the atomic swap. Failure keeps the original installation intact. */
    public static void install(Path transaction, Properties plan) throws IOException {
        Path source = installedJar(Paths.get(plan.getProperty("source")));
        Path staged = transaction.resolve("download.jar");
        String expectedOld = plan.getProperty("oldSha256"), expectedNew = plan.getProperty("newSha256");
        if (!sha256(source).equals(expectedOld)) throw new IOException("Installed JAR changed after download");
        if (!sha256(staged).equals(expectedNew)) throw new IOException("Staged JAR checksum mismatch");
        rejectDuplicates(source);
        Path backup = transaction.resolve("backup.jar");
        Files.copy(source, backup); // Never overwrite a previous recovery copy.
        if (!sha256(backup).equals(expectedOld)) throw new IOException("Backup verification failed");
        Path next = Files.createTempFile(source.getParent(), ".modernization-", ".pending");
        try {
            Files.copy(staged, next, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(next, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (!sha256(next).equals(expectedNew) || !sha256(source).equals(expectedOld))
                throw new IOException("JAR changed during installation");
            // Keep the installed filename: one atomic replacement, no interval with zero or two loaded mod JARs.
            Files.move(next, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(next);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected transaction directory");
        Path tx = Paths.get(args[0])
            .toRealPath();
        Properties result = new Properties();
        int exit = 1;
        try {
            Properties plan = load(tx.resolve("plan.properties"));
            installedJar(Paths.get(plan.getProperty("source")));
            try (FileChannel channel = FileChannel.open(tx.resolve("session.lock"), StandardOpenOption.WRITE)) {
                Files.write(tx.resolve("ready"), new byte[] { 1 });
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(15);
                FileLock lock = null;
                while (lock == null && System.nanoTime() < deadline) {
                    try {
                        lock = channel.tryLock();
                    } catch (OverlappingFileLockException ignored) {}
                    if (lock == null) Thread.sleep(250);
                }
                if (lock == null) throw new IOException("Game did not exit within 15 minutes; original JAR retained");
                try {
                    install(tx, plan);
                } finally {
                    lock.release();
                }
            }
            result.setProperty("status", "installed");
            result.setProperty("version", plan.getProperty("version"));
            exit = 0;
        } catch (Exception error) {
            result.setProperty("status", "failed");
            result.setProperty("detail", error.toString());
            error.printStackTrace();
        }
        save(tx.resolve("result.properties"), result);
        System.exit(exit);
    }
}
