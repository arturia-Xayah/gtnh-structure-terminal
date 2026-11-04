package dev.structureterminal.update;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class UpdateTransportTest {

    static void jar(Path file, String version, String pack, boolean fixture) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String name : new String[] { "dev/structureterminal/StructureTerminal.class",
                "dev/aeoptimizer/OrderOptimizer.class", "dev/plasmaforgepower/PlasmaForgePower.class",
                "dev/aestoragevault/VaultMod.class" }) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(new byte[] { 1 });
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("modernization-update.json"));
            zip.write(
                ("{\"schema\":1,\"version\":\"" + version + "\",\"gtnh\":\"" + pack + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (fixture) {
                zip.putNextEntry(new ZipEntry("dev/structureterminal/packtest/Test.class"));
                zip.closeEntry();
            }
        }
    }

    @Test
    public void jarDescriptorMustMatchBothRequestedVersions() throws Exception {
        Path file = Files.createTempFile("updater", ".jar");
        try {
            jar(file, "0.3.3-alpha", "2.9.0", false);
            UpdateTransport.verifyJar(file, "0.3.3-alpha", "2.9.0");
            try {
                UpdateTransport.verifyJar(file, "0.3.4-alpha", "2.9.0");
                fail();
            } catch (IOException expected) {}
            try {
                UpdateTransport.verifyJar(file, "0.3.3-alpha", "2.9.0-RC-2");
                fail();
            } catch (IOException expected) {}
            jar(file, "0.3.3-alpha", "2.9.0", true);
            try {
                UpdateTransport.verifyJar(file, "0.3.3-alpha", "2.9.0");
                fail();
            } catch (IOException expected) {}
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void refusesPartialNonJarOrUnrelatedContent() throws Exception {
        Path file = Files.createTempFile("updater-broken", ".jar");
        try {
            Files.write(file, "not a jar".getBytes(StandardCharsets.UTF_8));
            try {
                UpdateTransport.verifyJar(file, "0.3.3-alpha", "2.9.0");
                fail();
            } catch (IOException expected) {}
        } finally {
            Files.delete(file);
        }
    }

    @Test
    public void cancellationAndSizeLimitsStopTransfer() throws Exception {
        byte[] input = new byte[40000];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            UpdateTransport
                .transfer(new ByteArrayInputStream(input), output, 50000, new AtomicBoolean(true), n -> {}, 1000);
            fail();
        } catch (IOException expected) {}
        assertEquals(0, output.size());
        try {
            UpdateTransport
                .transfer(new ByteArrayInputStream(input), output, 20000, new AtomicBoolean(), n -> {}, 1000);
            fail();
        } catch (IOException expected) {}
        assertTrue(output.size() <= 20000);
    }
}
