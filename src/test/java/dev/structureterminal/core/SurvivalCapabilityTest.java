package dev.structureterminal.core;

import static org.junit.Assert.*;

import java.lang.reflect.Constructor;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import org.junit.Test;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import dev.structureterminal.bridge.NativeBridge;

public final class SurvivalCapabilityTest {

    private static class Marker implements ISurvivalConstructable {

        @Override
        public void construct(ItemStack stack, boolean hintsOnly) {}

        @Override
        public String[] getStructureDescription(ItemStack stack) {
            return new String[0];
        }
    }

    private static final class Modern extends Marker {

        @Override
        public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment environment) {
            return -1;
        }
    }

    private static final class Legacy extends Marker {

        @Override
        public int survivalConstruct(ItemStack stack, int budget, IItemSource source, EntityPlayerMP player) {
            return -1;
        }
    }

    private static NativeBridge bridge(ISurvivalConstructable controller) throws Exception {
        Constructor<?> constructor = NativeBridge.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (NativeBridge) constructor.newInstance(null, controller, controller, null);
    }

    @Test
    public void markerWithRecursiveDefaultsIsRejectedWithoutInvokingIt() throws Exception {
        assertFalse(bridge(new Marker()).supportsSurvivalConstruction());
    }

    @Test
    public void modernAndLegacyImplementationsAreBothAccepted() throws Exception {
        assertTrue(bridge(new Modern()).supportsSurvivalConstruction());
        assertTrue(bridge(new Legacy()).supportsSurvivalConstruction());
    }
}
