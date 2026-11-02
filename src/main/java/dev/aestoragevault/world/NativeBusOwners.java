package dev.aestoragevault.world;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Native machines retain priority. Weak references do not keep unloaded worlds alive. */
public final class NativeBusOwners {

    private static final Map<MetaTileEntity, List<WeakReference<MTEMultiBlockBase>>> OWNERS = new WeakHashMap<>();

    private NativeBusOwners() {}

    public static synchronized void checked(MTEMultiBlockBase machine) {
        if (!machine.mMachine) return;
        List<MetaTileEntity> buses = new ArrayList<>();
        buses.addAll(machine.mInputBusses);
        buses.addAll(machine.mOutputBusses);
        for (MetaTileEntity bus : buses) {
            List<WeakReference<MTEMultiBlockBase>> owners = OWNERS.computeIfAbsent(bus, key -> new ArrayList<>());
            boolean present = false;
            for (WeakReference<MTEMultiBlockBase> ref : owners) if (ref.get() == machine) present = true;
            if (!present) owners.add(new WeakReference<>(machine));
        }
    }

    public static synchronized boolean inUse(MetaTileEntity bus) {
        List<WeakReference<MTEMultiBlockBase>> owners = OWNERS.get(bus);
        if (owners == null) return false;
        owners.removeIf(ref -> !valid(ref.get(), bus));
        return !owners.isEmpty();
    }

    private static boolean valid(MTEMultiBlockBase machine, MetaTileEntity bus) {
        if (machine == null || !machine.mMachine
            || !(machine.mInputBusses.contains(bus) || machine.mOutputBusses.contains(bus))) return false;
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        return base != null && base.getWorld() != null
            && base.getWorld()
                .blockExists(base.getXCoord(), base.getYCoord(), base.getZCoord())
            && base.getWorld()
                .getTileEntity(base.getXCoord(), base.getYCoord(), base.getZCoord()) == base;
    }
}
