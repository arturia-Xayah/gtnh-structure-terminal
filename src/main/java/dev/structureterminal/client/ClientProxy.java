package dev.structureterminal.client;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.structureterminal.CommonProxy;

public final class ClientProxy extends CommonProxy {

    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<Runnable>();

    @Override
    public void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Override
    public void open(EntityPlayer player, int x, int y, int z, int side) {
        queue.add(
            () -> Minecraft.getMinecraft()
                .displayGuiScreen(new TerminalScreen(x, y, z, side)));
    }

    @Override
    public void receive(NBTTagCompound tag) {
        queue.add(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            String text = StatCollector.translateToLocalFormatted(tag.getString("key"), tag.getString("value"));
            if (mc.currentScreen instanceof TerminalScreen) ((TerminalScreen) mc.currentScreen).status = text;
            else if (mc.thePlayer != null) mc.thePlayer.addChatComponentMessage(new ChatComponentText(text));
        });
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) for (int i = 0; i < 16; i++) {
            Runnable next = queue.poll();
            if (next == null) break;
            next.run();
        }
    }
}
