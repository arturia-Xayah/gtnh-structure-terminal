package dev.structureterminal.update;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UpdateInstallerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Properties prepare(Path tx, Path source) throws Exception {
        Files.createDirectories(tx);
        Files.createDirectories(source.getParent());
        UpdateTransportTest.jar(source, "0.3.2-alpha", "2.9.0", false);
        UpdateTransportTest.jar(tx.resolve("download.jar"), "0.3.3-alpha", "2.9.0", false);
        Properties p = new Properties();
        p.setProperty(
            "source",
            source.toRealPath()
                .toString());
        p.setProperty("oldSha256", UpdateInstaller.sha256(source));
        p.setProperty("newSha256", UpdateInstaller.sha256(tx.resolve("download.jar")));
        p.setProperty("version", "0.3.3-alpha");
        UpdateInstaller.save(tx.resolve("plan.properties"), p);
        return p;
    }

    @Test
    public void atomicReplacementKeepsExactlyOneJarAndVerifiedBackup() throws Exception {
        Path root = folder.getRoot()
            .toPath(), tx = root.resolve("tx"), source = root.resolve("mods/original name.jar");
        Properties p = prepare(tx, source);
        UpdateInstaller.install(tx, p);
        assertEquals(p.getProperty("newSha256"), UpdateInstaller.sha256(source));
        assertEquals(p.getProperty("oldSha256"), UpdateInstaller.sha256(tx.resolve("backup.jar")));
        try (java.util.stream.Stream<Path> files = Files.list(source.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    public void refusesModifiedInstalledJarAndTamperedDownload() throws Exception {
        Path root = folder.getRoot()
            .toPath(), tx = root.resolve("tx"), source = root.resolve("mods/a.jar");
        Properties p = prepare(tx, source);
        Files.write(tx.resolve("download.jar"), new byte[] { 1, 2, 3 });
        try {
            UpdateInstaller.install(tx, p);
            fail();
        } catch (IOException expected) {}
        assertEquals(p.getProperty("oldSha256"), UpdateInstaller.sha256(source));
        UpdateTransportTest.jar(tx.resolve("download.jar"), "0.3.3-alpha", "2.9.0", false);
        Files.write(source, new byte[] { 4, 5, 6 });
        try {
            UpdateInstaller.install(tx, p);
            fail();
        } catch (IOException expected) {}
        assertArrayEquals(new byte[] { 4, 5, 6 }, Files.readAllBytes(source));
    }

    @Test
    public void refusesDuplicateModInVersionSubdirectory() throws Exception {
        Path root = folder.getRoot()
            .toPath(), tx = root.resolve("tx"), source = root.resolve("mods/a.jar");
        Properties p = prepare(tx, source);
        Files.createDirectories(root.resolve("mods/1.7.10"));
        Files.copy(source, root.resolve("mods/1.7.10/duplicate.jar"));
        try {
            UpdateInstaller.install(tx, p);
            fail();
        } catch (IOException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("Duplicate"));
        }
        assertEquals(p.getProperty("oldSha256"), UpdateInstaller.sha256(source));
    }

    @Test
    public void refusesInstallOutsideMods() throws Exception {
        Path source = folder.getRoot()
            .toPath()
            .resolve("elsewhere.jar");
        Files.write(source, new byte[] { 1 });
        try {
            UpdateInstaller.installedJar(source);
            fail();
        } catch (IOException expected) {}
    }

    @Test
    public void separateHelperWaitsForParentLockThenInstallsWithoutMinecraft() throws Exception {
        Path root = folder.getRoot()
            .toPath(), tx = root.resolve("transaction with spaces"), source = root.resolve("mods/test.jar");
        Properties p = prepare(tx, source);
        String executable = System.getProperty("os.name")
            .toLowerCase(java.util.Locale.ROOT)
            .contains("win") ? "java.exe" : "java";
        String java = Paths.get(System.getProperty("java.home"), "bin", executable)
            .toString();
        String classes = Paths.get(
            UpdateInstaller.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
            .toString();
        Process helper;
        try (
            FileChannel channel = FileChannel
                .open(tx.resolve("session.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.lock()) {
            helper = new ProcessBuilder(java, "-cp", classes, UpdateInstaller.class.getName(), tx.toString())
                .redirectErrorStream(true)
                .redirectOutput(
                    tx.resolve("helper.log")
                        .toFile())
                .start();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!Files.exists(tx.resolve("ready")) && helper.isAlive() && System.nanoTime() < until)
                Thread.sleep(25);
            assertTrue(Files.exists(tx.resolve("ready")));
            assertTrue(helper.isAlive());
            assertEquals(p.getProperty("oldSha256"), UpdateInstaller.sha256(source));
            assertFalse(Files.exists(tx.resolve("backup.jar")));
        }
        try {
            assertTrue(helper.waitFor(8, TimeUnit.SECONDS));
            assertEquals(0, helper.exitValue());
            assertEquals(
                "installed",
                UpdateInstaller.load(tx.resolve("result.properties"))
                    .getProperty("status"));
            assertEquals(p.getProperty("newSha256"), UpdateInstaller.sha256(source));
            assertEquals(p.getProperty("oldSha256"), UpdateInstaller.sha256(tx.resolve("backup.jar")));
        } finally {
            helper.destroyForcibly();
        }
    }
}
