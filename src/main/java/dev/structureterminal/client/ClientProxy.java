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
        dev.structureterminal.client.update.UpdateClient.INSTANCE.init();
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

    private int[] lastTarget;
    private net.minecraft.world.World targetWorld;

    @Override
    public void settings(EntityPlayer player) {
        if (targetWorld == player.worldObj && lastTarget != null
            && dev.structureterminal.bridge.NativeBridge
                .resolve(player.worldObj, lastTarget[0], lastTarget[1], lastTarget[2], lastTarget[3]) != null) {
            open(player, lastTarget[0], lastTarget[1], lastTarget[2], lastTarget[3]);
        } else open(player, 0, 0, 0, -1);
    }

    @Override
    public void use(EntityPlayer player, int x, int y, int z, int side, boolean build) {
        lastTarget = new int[] { x, y, z, side };
        targetWorld = player.worldObj;
        queue.add(() -> {
            if (Minecraft.getMinecraft().thePlayer != player || player.getHeldItem() == null
                || player.getHeldItem()
                    .getItem() != dev.structureterminal.StructureTerminal.item)
                return;
            try {
                dev.structureterminal.TerminalSettings settings = dev.structureterminal.TerminalSettings
                    .fromTool(player.getHeldItem());
                if (build) {
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setInteger("action", 1);
                    tag.setInteger("x", x);
                    tag.setInteger("y", y);
                    tag.setInteger("z", z);
                    tag.setInteger("side", side);
                    tag.setTag("settings", settings.write());
                    dev.structureterminal.net.TerminalNetwork.channel
                        .sendToServer(new dev.structureterminal.net.TerminalNetwork.Request(tag));
                } else {
                    dev.structureterminal.bridge.NativeBridge bridge = dev.structureterminal.bridge.NativeBridge
                        .resolve(player.worldObj, x, y, z, side);
                    if (bridge == null) return;
                    dev.structureterminal.bridge.StructureScanner.Scan scan = dev.structureterminal.bridge.StructureScanner
                        .scan(bridge, player, settings);
                    com.gtnewhorizon.structurelib.StructureLibAPI.startHinting(player.worldObj);
                    try {
                        for (dev.structureterminal.bridge.StructureScanner.Cell cell : scan.cells) cell.element
                            .spawnHint(bridge.context, player.worldObj, cell.x, cell.y, cell.z, settings.trigger());
                    } finally {
                        com.gtnewhorizon.structurelib.StructureLibAPI.endHinting(player.worldObj);
                    }
                }
            } catch (RuntimeException error) {
                player.addChatComponentMessage(
                    new ChatComponentText(
                        error.getMessage() == null ? "Terminal operation failed" : error.getMessage()));
            }
        });
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
        if (targetWorld != Minecraft.getMinecraft().theWorld) {
            targetWorld = null;
            lastTarget = null;
        }
        if (event.phase == TickEvent.Phase.END) for (int i = 0; i < 16; i++) {
            Runnable next = queue.poll();
            if (next == null) break;
            next.run();
        }
    }
}
