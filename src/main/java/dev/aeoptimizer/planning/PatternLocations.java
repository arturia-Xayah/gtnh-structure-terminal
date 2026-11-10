package dev.aeoptimizer.planning;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.crafting.v2.CraftingContext;
import appeng.helpers.DualityInterface;
import appeng.me.cache.CraftingGridCache;
import dev.structureterminal.net.TerminalNetwork;

/** Short, player-bound clickable tokens. No arbitrary coordinates or world loads accepted from clients. */
public final class PatternLocations extends CommandBase {

    private static final Map<UUID, LinkedHashMap<String, Record>> RECORDS = new HashMap<>();
    private static final long TTL = 10 * 60 * 1000L;

    private static final class Record {

        final String product;
        final NBTTagList locations;
        final long expires = System.currentTimeMillis() + TTL;

        Record(String product, NBTTagList locations) {
            this.product = product;
            this.locations = locations;
        }
    }

    public static synchronized String remember(EntityPlayerMP player, String product,
        Collection<ICraftingPatternDetails> patterns, CraftingContext context) {
        long now = System.currentTimeMillis();
        RECORDS.values()
            .forEach(
                records -> records.values()
                    .removeIf(r -> r.expires < now));
        RECORDS.values()
            .removeIf(Map::isEmpty);
        NBTTagList locations = new NBTTagList();
        Set<String> seen = new HashSet<>();
        if (context.craftingGrid instanceof CraftingGridCache) for (ICraftingPatternDetails pattern : patterns) {
            List<ICraftingMedium> mediums = ((CraftingGridCache) context.craftingGrid).getMediums(pattern);
            if (mediums == null) continue;
            for (ICraftingMedium medium : mediums) {
                TileEntity tile = medium instanceof DualityInterface ? ((DualityInterface) medium).getTile()
                    : medium instanceof TileEntity ? (TileEntity) medium : null;
                if (tile == null || tile.getWorldObj() == null) continue;
                int dim = tile.getWorldObj().provider.dimensionId;
                String key = dim + ":" + tile.xCoord + ":" + tile.yCoord + ":" + tile.zCoord;
                if (!seen.add(key)) continue;
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("dim", dim);
                tag.setInteger("x", tile.xCoord);
                tag.setInteger("y", tile.yCoord);
                tag.setInteger("z", tile.zCoord);
                tag.setString("world", tile.getWorldObj().provider.getDimensionName());
                locations.appendTag(tag);
            }
        }
        LinkedHashMap<String, Record> records = RECORDS
            .computeIfAbsent(player.getUniqueID(), k -> new LinkedHashMap<>());
        records.values()
            .removeIf(r -> r.expires < System.currentTimeMillis());
        while (records.size() >= 256) records.remove(
            records.keySet()
                .iterator()
                .next());
        String token = UUID.randomUUID()
            .toString();
        records.put(token, new Record(product, locations));
        return "/aeolocate " + token;
    }

    public static synchronized void clear() {
        RECORDS.clear();
    }

    @Override
    public String getCommandName() {
        return "aeolocate";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/aeolocate <report-token> [page]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP) || args.length < 1 || args.length > 2) return;
        EntityPlayerMP player = (EntityPlayerMP) sender;
        Record record;
        synchronized (PatternLocations.class) {
            record = RECORDS.getOrDefault(player.getUniqueID(), new LinkedHashMap<>())
                .get(args[0]);
        }
        if (record == null || record.expires < System.currentTimeMillis()) {
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("aeoptimizer.locate.expired"));
            return;
        }
        if (record.locations.tagCount() == 0) {
            player.addChatMessage(
                new net.minecraft.util.ChatComponentTranslation("aeoptimizer.locate.unknown", record.product));
            return;
        }
        int page = 0;
        if (args.length == 2) try {
            page = Math.max(0, Integer.parseInt(args[1]) - 1);
        } catch (NumberFormatException ignored) {
            return;
        }
        int pages = (record.locations.tagCount() + 31) / 32;
        page = Math.min(page, pages - 1);
        NBTTagCompound packet = new NBTTagCompound();
        packet.setBoolean("patternHighlight", true);
        packet.setString("product", record.product);
        NBTTagList points = new NBTTagList();
        for (int i = page * 32; i < Math.min(record.locations.tagCount(), (page + 1) * 32); i++) {
            NBTTagCompound pos = record.locations.getCompoundTagAt(i);
            points.appendTag(pos.copy());
            player.addChatMessage(
                new ChatComponentText(
                    record.product + " — "
                        + pos.getString("world")
                        + " ["
                        + pos.getInteger("dim")
                        + "] ("
                        + pos.getInteger("x")
                        + ", "
                        + pos.getInteger("y")
                        + ", "
                        + pos.getInteger("z")
                        + ")"));
        }
        packet.setTag("points", points);
        TerminalNetwork.channel.sendTo(new TerminalNetwork.Reply(packet), player);
        if (pages > 1) {
            ChatComponentText next = new ChatComponentText("[" + (page + 1) + "/" + pages + "] →");
            next.getChatStyle()
                .setUnderlined(true)
                .setColor(net.minecraft.util.EnumChatFormatting.AQUA)
                .setChatClickEvent(
                    new net.minecraft.event.ClickEvent(
                        net.minecraft.event.ClickEvent.Action.RUN_COMMAND,
                        "/aeolocate " + args[0] + " " + ((page + 1) % pages + 1)));
            player.addChatMessage(next);
        }
    }
}
