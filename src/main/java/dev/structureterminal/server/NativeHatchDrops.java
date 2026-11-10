package dev.structureterminal.server;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Redirect native break-hook item drops to the terminal return route, without inventing fluid/container retention. */
final class NativeHatchDrops implements AutoCloseable {

    private static final ThreadLocal<NativeHatchDrops> ACTIVE = new ThreadLocal<>();
    private static boolean registered;
    final List<ItemStack> items = new ArrayList<>();
    private final World world;
    private final int x, y, z;

    private NativeHatchDrops(World world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static NativeHatchDrops capture(World world, int x, int y, int z) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested terminal hatch removal");
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new Listener());
            registered = true;
        }
        NativeHatchDrops scope = new NativeHatchDrops(world, x, y, z);
        ACTIVE.set(scope);
        return scope;
    }

    public void close() {
        ACTIVE.remove();
    }

    public static final class Listener {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void dropped(EntityJoinWorldEvent event) {
            NativeHatchDrops scope = ACTIVE.get();
            if (scope == null || event.world != scope.world || !(event.entity instanceof EntityItem)) return;
            EntityItem item = (EntityItem) event.entity;
            if (Math.abs(item.posX - scope.x - .5) > 1.5 || Math.abs(item.posY - scope.y - .5) > 1.5
                || Math.abs(item.posZ - scope.z - .5) > 1.5) return;
            ItemStack stack = item.getEntityItem();
            if (stack != null && stack.stackSize > 0) scope.items.add(stack.copy());
            event.setCanceled(true);
        }
    }
}
