package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class VaultGeometryTest {

    @Test
    public void allSixFaceCentersAndTwentyFiveRemainingShellCells() {
        for (int face = 0; face < 6; face++) {
            int count = 0;
            for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) if (VaultGeometry.shell(x, y, z)
                    && !(x == VaultGeometry.normal(face, 0) && y == VaultGeometry.normal(face, 1)
                        && z == VaultGeometry.normal(face, 2)))
                    count++;
            assertEquals(25, count);
        }
        assertFalse(VaultGeometry.shell(0, 0, 0));
        assertFalse(VaultGeometry.shell(2, 0, 0));
    }

    @Test
    public void sixFacesUseAllNineConnectedTilesExactlyOnce() {
        for (int side = 0; side < 6; side++) {
            Set<Integer> seen = new HashSet<>();
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) if (x * VaultGeometry.normal(side, 0) + y * VaultGeometry.normal(side, 1)
                    + z * VaultGeometry.normal(side, 2) == 1) assertTrue(seen.add(VaultGeometry.tile(x, y, z, side)));
            assertEquals(9, seen.size());
            for (int i = 0; i < 9; i++) assertTrue(seen.contains(i));
        }
    }
}
