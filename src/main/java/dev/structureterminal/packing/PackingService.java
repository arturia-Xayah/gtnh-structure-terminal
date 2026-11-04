package dev.structureterminal.packing;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartItemStack;
import appeng.api.parts.SelectedPart;
import appeng.parts.PartPlacement;
import appeng.tile.AEBaseTile;
import cpw.mods.fml.common.registry.GameRegistry;

/** Uses native complete device NBT, not the deliberately partial Memory Card settings format. */
public final class PackingService {

    private static final Set<String> WHITELIST = new HashSet<>();

    private PackingService() {}

    public static void configure(File file) {
        List<String> defaults = new ArrayList<>();
        defaults.add(
            key(
                AEApi.instance()
                    .definitions()
                    .parts()
                    .iface()
                    .maybeStack(1)
                    .get()));
        defaults.add(
            key(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .iface()
                    .maybeStack(1)
                    .get()));
        defaults.add(
            key(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .drive()
                    .maybeStack(1)
                    .get()));
        Item fcPart = GameRegistry.findItem("ae2fc", "part_fluid_interface");
        if (fcPart != null) defaults.add(key(new ItemStack(fcPart)));
        // Register the FC block by its class rather than assume its legacy registry spelling.
        for (Object entry : Block.blockRegistry) {
            Block block = (Block) entry;
            if (block.getClass()
                .getName()
                .equals("com.glodblock.github.common.block.BlockFluidInterface")
                || block.getClass()
                    .getSimpleName()
                    .equals("BlockFluidInterface")
                    && block.getClass()
                        .getName()
                        .startsWith("com.glodblock.github."))
                defaults.add(key(new ItemStack(block)));
        }
        Configuration config = new Configuration(file);
        config.load();
        WHITELIST.clear();
        WHITELIST.addAll(
            Arrays.asList(
                config.getStringList(
                    "deviceWhitelist",
                    "packingTape",
                    defaults.toArray(new String[0]),
                    "Packable AE devices: registry:item@metadata. Does not enable arbitrary non-AE machines.")));
        if (config.hasChanged()) config.save();
    }

    private static String key(ItemStack stack) {
        return Item.itemRegistry.getNameForObject(stack.getItem()) + "@" + stack.getItemDamage();
    }

    public static ItemStack device(ItemStack pack) {
        if (pack == null || pack.getItem() != PackingItems.PACKAGE || !pack.hasTagCompound()) return null;
        NBTTagCompound tag = pack.getTagCompound();
        if (tag.getInteger("schema") != 1 || !tag.hasKey("contents", 10) || !tag.hasKey("device", 10)) return null;
        ItemStack result = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("device"));
        return result != null && result.stackSize == 1 ? result : null;
    }

    private static ItemStack packageFor(ItemStack device, NBTTagCompound contents, boolean part, Block block,
        int meta) {
        ItemStack result = new ItemStack(PackingItems.PACKAGE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("schema", 1);
        tag.setBoolean("part", part);
        tag.setTag("device", device.writeToNBT(new NBTTagCompound()));
        tag.setTag("contents", contents);
        if (!part) {
            tag.setString("block", Block.blockRegistry.getNameForObject(block));
            tag.setInteger("meta", meta);
        }
        result.setTagCompound(tag);
        return result;
    }

    private static boolean permitted(EntityPlayer player, World world, int x, int y, int z, int side, ItemStack held) {
        return y >= 0 && y < world.getHeight()
            && world.canMineBlock(player, x, y, z)
            && player.canPlayerEdit(x, y, z, side, held);
    }

    private static boolean permitted(EntityPlayer player, IGridNode node) {
        if (node == null) return true;
        ISecurityGrid security = node.getGrid()
            .getCache(ISecurityGrid.class);
        return security.hasPermission(player, SecurityPermissions.BUILD);
    }

    private static boolean hostPermission(EntityPlayer player, TileEntity tile) {
        IPartHost host = PartPlacement.getExistingHost(tile);
        if (host != null) {
            for (ForgeDirection side : ForgeDirection.values()) {
                IPart part = host.getPart(side);
                if (part != null && !permitted(player, part.getGridNode())) return false;
            }
        } else if (tile instanceof IGridHost) {
            for (ForgeDirection side : ForgeDirection.values())
                if (!permitted(player, ((IGridHost) tile).getGridNode(side))) return false;
        }
        return true;
    }

    public static boolean pack(ItemStack tape, EntityPlayer player, World world, int x, int y, int z, int face,
        float hx, float hy, float hz) {
        if (world.isRemote || tape == null
            || tape.stackSize != 1
            || tape.getItem() != PackingItems.TAPE
            || !permitted(player, world, x, y, z, face, tape)) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        IPartHost host = PartPlacement.getExistingHost(tile);
        SelectedPart selected = host == null ? null : host.selectPart(Vec3.createVectorHelper(hx, hy, hz));
        IPart part = selected == null ? null : selected.part;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        ItemStack original;
        if (host != null) {
            if (part == null || selected.side == ForgeDirection.UNKNOWN || !permitted(player, part.getGridNode()))
                return false;
            original = part.getItemStack(PartItemStack.World)
                .copy();
        } else {
            if (!(tile instanceof AEBaseTile) || !hostPermission(player, tile)) return false;
            original = new ItemStack(block, 1, meta);
        }
        original.stackSize = 1;
        if (!WHITELIST.contains(key(original))) return false;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, block, meta, player))) return false;
        NBTTagCompound contents = new NBTTagCompound();
        if (part != null) part.writeToNBT(contents);
        else tile.writeToNBT(contents);
        ItemStack packed = packageFor(original, contents, part != null, block, meta);
        EntityItem drop = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, packed);
        drop.delayBeforeCanPickup = 10;
        // Drop creation can be vetoed by another mod. Do not remove the device until it succeeds.
        if (!world.spawnEntityInWorld(drop)) return false;
        if (world.getTileEntity(x, y, z) != tile || part != null && host.getPart(selected.side) != part) {
            drop.setDead();
            return false;
        }
        if (part != null) {
            host.removePart(selected.side, false);
            if (host.isEmpty()) host.cleanup();
        } else {
            // Removing the TE first prevents the normal block break path from dropping its contents again.
            world.removeTileEntity(x, y, z);
            if (!world.setBlockToAir(x, y, z)) {
                world.setTileEntity(x, y, z, tile);
                drop.setDead();
                return false;
            }
        }
        if (!player.capabilities.isCreativeMode) {
            if (tape.getItemDamage() + 1 >= 64) tape.stackSize--;
            else tape.setItemDamage(tape.getItemDamage() + 1);
        }
        player.inventory.markDirty();
        return true;
    }

    public static boolean unpack(ItemStack pack, EntityPlayer player, World world, int x, int y, int z, int face) {
        ItemStack original = device(pack);
        if (world.isRemote || original == null || pack.stackSize != 1 || face < 0 || face > 5) return false;
        NBTTagCompound tag = pack.getTagCompound();
        ForgeDirection side = ForgeDirection.getOrientation(face);
        boolean restored;
        if (tag.getBoolean("part")) {
            if (!(original.getItem() instanceof IPartItem)) return false;
            IPartHost host = PartPlacement.getExistingHost(world.getTileEntity(x, y, z));
            if (host != null && host.canAddPart(original, side)) {
                restored = placePart(original, tag, player, world, x, y, z, side, host, pack);
            } else if (host == null && world.getBlock(x, y, z)
                .isReplaceable(world, x, y, z)) {
                    restored = placePart(original, tag, player, world, x, y, z, side, null, pack);
                } else {
                    int tx = x + side.offsetX, ty = y + side.offsetY, tz = z + side.offsetZ;
                    restored = placePart(
                        original,
                        tag,
                        player,
                        world,
                        tx,
                        ty,
                        tz,
                        side.getOpposite(),
                        PartPlacement.getExistingHost(world.getTileEntity(tx, ty, tz)),
                        pack);
                }
        } else {
            if (!world.getBlock(x, y, z)
                .isReplaceable(world, x, y, z)) {
                x += side.offsetX;
                y += side.offsetY;
                z += side.offsetZ;
            }
            restored = placeBlock(original, tag, player, world, x, y, z, side, pack);
        }
        if (restored) {
            // Even creative players consume a populated package: otherwise stored items can be duplicated.
            pack.stackSize--;
            player.inventory.markDirty();
        }
        return restored;
    }

    private static boolean placePart(ItemStack original, NBTTagCompound tag, EntityPlayer player, World world, int x,
        int y, int z, ForgeDirection side, IPartHost host, ItemStack pack) {
        if (!permitted(player, world, x, y, z, side.ordinal(), pack)
            || !hostPermission(player, world.getTileEntity(x, y, z))) return false;
        if (host == null && !world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z)) return false;
        if (host != null && !host.canAddPart(original, side)) return false;
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, x, y, z);
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(snapshot, world.getBlock(x, y, z), player)))
            return false;
        boolean created = host == null;
        if (created) {
            Block cable = AEApi.instance()
                .definitions()
                .blocks()
                .multiPart()
                .maybeBlock()
                .get();
            if (!world.setBlock(x, y, z, cable, 0, 3)) return false;
            host = PartPlacement.getExistingHost(world.getTileEntity(x, y, z));
            if (host == null) {
                snapshot.restore(true, false);
                return false;
            }
        }
        ForgeDirection placed = null;
        try (PackedPartContext context = new PackedPartContext(tag.getCompoundTag("contents"), host, side)) {
            placed = host.addPart(original.copy(), side, player);
            if (placed == null) {
                if (created) snapshot.restore(true, false);
                return false;
            }
            // Newly placed cable hosts initialize on the next tick; load before that first addToWorld.
            if (!context.restored()) host.getPart(placed)
                .readFromNBT(
                    (NBTTagCompound) tag.getCompoundTag("contents")
                        .copy());
        } catch (RuntimeException failure) {
            if (host.getPart(side) != null) host.removePart(side, false);
            if (created) snapshot.restore(true, false);
            throw failure;
        }
        host.markForSave();
        host.markForUpdate();
        host.partChanged();
        return true;
    }

    private static boolean placeBlock(ItemStack original, NBTTagCompound tag, EntityPlayer player, World world, int x,
        int y, int z, ForgeDirection side, ItemStack pack) {
        if (!permitted(player, world, x, y, z, side.ordinal(), pack) || !world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z)) return false;
        Block block = Block.getBlockFromName(tag.getString("block"));
        int meta = tag.getInteger("meta");
        if (block == null || Item.getItemFromBlock(block) != original.getItem()
            || meta != original.getItemDamage()
            || !world.canPlaceEntityOnSide(block, x, y, z, false, side.ordinal(), player, original)) return false;
        TileEntity tile = block.createTileEntity(world, meta);
        if (!(tile instanceof AEBaseTile)) return false;
        NBTTagCompound contents = (NBTTagCompound) tag.getCompoundTag("contents")
            .copy();
        contents.setInteger("x", x);
        contents.setInteger("y", y);
        contents.setInteger("z", z);
        tile.readFromNBT(contents);
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, x, y, z);
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(snapshot, world.getBlock(x, y, z), player)))
            return false;
        if (!world.setBlock(x, y, z, block, meta, 3)) return false;
        world.setTileEntity(x, y, z, tile);
        tile.markDirty();
        ((AEBaseTile) tile).markForUpdate();
        return true;
    }
}
