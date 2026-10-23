package dev.aestoragevault.ui.dashboard;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;

public class VaultMeshTest {

    @Test
    public void geometryRemainsFiniteBoundedAndInsideTheWindowAcrossStates() {
        VaultMesh mesh = new VaultMesh();
        for (VaultLayout layout : new VaultLayout[] { VaultLayout.full(), VaultLayout.fit(320, 240) }) {
            for (boolean formed : new boolean[] { false, true }) for (long time = 0; time < 6000; time += 125) {
                VaultAnimation.Frame f = new VaultAnimation()
                    .sample(time, BigInteger.valueOf(1000), BigInteger.valueOf(time % 1001), 218, formed);
                mesh.build(layout, f, formed, true);
                assertTrue(mesh.size() > 0 && mesh.size() <= VaultMesh.MAX_VERTICES && mesh.size() % 3 == 0);
                for (int i = 0; i < mesh.size(); i++) {
                    assertTrue(Float.isFinite(mesh.x(i)) && Float.isFinite(mesh.y(i)));
                    assertTrue(mesh.x(i) >= 0 && mesh.x(i) <= layout.width);
                    assertTrue(mesh.y(i) >= 0 && mesh.y(i) <= layout.height);
                }
            }
        }
    }

    @Test
    public void disabledMotionIsIdenticalAcrossTimeWhileEnabledDecorationsActuallyMove() {
        VaultMesh mesh = new VaultMesh();
        VaultAnimation a = new VaultAnimation();
        VaultLayout l = VaultLayout.full();
        a.setEnabled(false);
        mesh.build(l, a.sample(0, BigInteger.valueOf(100), BigInteger.valueOf(50), 1, true), true, true);
        long staticHash = hash(mesh);
        mesh.build(l, a.sample(1500, BigInteger.valueOf(100), BigInteger.valueOf(50), 1, true), true, true);
        assertEquals(staticHash, hash(mesh));
        a.setEnabled(true);
        mesh.build(l, a.sample(1500, BigInteger.valueOf(100), BigInteger.valueOf(50), 1, true), true, true);
        long movingHash = hash(mesh);
        mesh.build(l, a.sample(3000, BigInteger.valueOf(100), BigInteger.valueOf(50), 1, true), true, true);
        assertNotEquals(movingHash, hash(mesh));
    }

    private long hash(VaultMesh mesh) {
        long value = 1;
        for (int i = 0; i < mesh.size(); i++)
            value = ((value * 31 + Float.floatToIntBits(mesh.x(i))) * 31 + Float.floatToIntBits(mesh.y(i))) * 31
                + mesh.color(i);
        return value;
    }
}
