package dev.structureterminal.server;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import com.gtnewhorizon.structurelib.ChannelDescription;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import dev.structureterminal.TerminalSettings;
import dev.structureterminal.bridge.NativeBridge;
import dev.structureterminal.bridge.StructureScanner;
import dev.structureterminal.bridge.StructureScanner.Cell;
import dev.structureterminal.bridge.StructureScanner.Scan;

/**
 * Reports observed runtime capability; it intentionally does not equate an interface marker with a passed machine test.
 */
public final class AuditCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "structureterminal";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/structureterminal audit | inspect <x> <y> <z>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            File dir = new File(
                Loader.instance()
                    .getConfigDir(),
                "structureterminal-reports");
            Files.createDirectories(dir.toPath());
            if (args.length == 1 && "audit".equals(args[0])) audit(dir);
            else if (args.length == 4 && "inspect".equals(args[0]) && sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                NativeBridge bridge = NativeBridge.resolve(
                    player.worldObj,
                    parseInt(sender, args[1]),
                    parseInt(sender, args[2]),
                    parseInt(sender, args[3]),
                    1);
                if (bridge == null) throw new IllegalArgumentException("Unsupported controller");
                inspect(dir, bridge, player);
            } else {
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
                return;
            }
            sender.addChatMessage(new ChatComponentText("Structure Terminal report: " + dir.getAbsolutePath()));
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            sender.addChatMessage(new ChatComponentText("Structure Terminal: " + e.getMessage()));
        }
    }

    private void audit(File dir) throws ReflectiveOperationException, IOException {
        Class<?> api = Class.forName("gregtech.api.GregTechAPI");
        Object entries = api.getField("METATILEENTITIES")
            .get(null);
        List<String> lines = new ArrayList<String>();
        lines.add("meta_id,class,constructable,survival_marker,survival_override,definition_override,status");
        for (int i = 0; i < Array.getLength(entries); i++) {
            Object machine = Array.get(entries, i);
            if (machine == null) continue;
            boolean constructable = machine instanceof IConstructable,
                survival = machine instanceof ISurvivalConstructable;
            boolean override = false, definition = false;
            if (constructable) definition = machine.getClass()
                .getMethod("getStructureDefinition")
                .getDeclaringClass() != IConstructable.class;
            if (survival) {
                override = machine.getClass()
                    .getMethod("survivalConstruct", ItemStack.class, int.class, ISurvivalBuildEnvironment.class)
                    .getDeclaringClass() != ISurvivalConstructable.class;
                if (!override) override = machine.getClass()
                    .getMethod(
                        "survivalConstruct",
                        ItemStack.class,
                        int.class,
                        com.gtnewhorizon.structurelib.structure.IItemSource.class,
                        EntityPlayerMP.class)
                    .getDeclaringClass() != ISurvivalConstructable.class;
            }
            lines.add(
                i + ","
                    + machine.getClass()
                        .getName()
                    + ","
                    + constructable
                    + ","
                    + survival
                    + ","
                    + override
                    + ","
                    + definition
                    + ",NOT_GAMEPLAY_TESTED");
        }
        Files.write(new File(dir, "gregtech-registry.csv").toPath(), lines, StandardCharsets.UTF_8);
        List<String> channels = new ArrayList<String>();
        channels.add("channel,value,item,damage,name");
        for (Map.Entry<String, ChannelDescription> entry : ChannelDescription.getAll()
            .entrySet())
            for (Map.Entry<ItemStack, Integer> option : entry.getValue()
                .getItems()
                .entrySet()) {
                    ItemStack item = option.getKey();
                    channels.add(
                        csv(entry.getKey()) + ","
                            + option.getValue()
                            + ","
                            + csv(net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()))
                            + ","
                            + item.getItemDamage()
                            + ","
                            + csv(item.getDisplayName()));
                }
        Files.write(new File(dir, "channel-variants.csv").toPath(), channels, StandardCharsets.UTF_8);
        List<String> versions = new ArrayList<String>();
        for (ModContainer mod : Loader.instance()
            .getModList()) versions.add(mod.getModId() + "=" + mod.getVersion());
        Files.write(new File(dir, "loaded-mods.txt").toPath(), versions, StandardCharsets.UTF_8);
    }

    private void inspect(File dir, NativeBridge bridge, EntityPlayerMP player) throws IOException {
        Scan scan = StructureScanner.scan(bridge, player, TerminalSettings.fromTool(player.getHeldItem()));
        List<String> lines = new ArrayList<String>();
        lines.add("x,y,z,group,candidates,candidate_error,air,description");
        for (Cell cell : scan.cells) lines.add(
            cell.x + ","
                + cell.y
                + ","
                + cell.z
                + ","
                + cell.group
                + ","
                + cell.candidates.size()
                + ","
                + cell.candidateError
                + ","
                + cell.air
                + ","
                + csv(cell.description));
        Files.write(new File(dir, "selected-structure.csv").toPath(), lines, StandardCharsets.UTF_8);
    }

    private String csv(String s) {
        return "\"" + s.replace("\"", "\"\"")
            .replace("\r", " ")
            .replace("\n", " ") + "\"";
    }
}
