package dev.structureterminal.server;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.ChannelDescription;
import com.gtnewhorizon.structurelib.structure.ISpecialItemBlock;

import dev.structureterminal.bridge.ItemKeys;
import dev.structureterminal.bridge.StructureScanner.Cell;

/** Recover intact ordinary structure blocks (including glass), without touching tile entities. */
final class BlockMaterials {

    private BlockMaterials() {}

    static List<ItemStack> recover(World world, int x, int y, int z, EntityPlayerMP player) {
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        if (world.getTileEntity(x, y, z) == null && !block.hasTileEntity(meta)) {
            try {
                Item item = Item.getItemFromBlock(block);
                if (item instanceof ItemBlock) {
                    int damage = item instanceof ISpecialItemBlock
                        ? ((ISpecialItemBlock) item).getItemMetaFromBlockMeta(block, meta)
                        : block.getDamageValue(world, x, y, z);
                    ItemStack copy = new ItemStack(item, 1, damage);
                    List<ItemStack> items = new ArrayList<ItemStack>();
                    items.add(copy);
                    return items;
                }
            } catch (RuntimeException ignored) {}
        }
        return block.getDrops(world, x, y, z, meta, 0);
    }

    static boolean known(Cell cell, Block block, List<ItemStack> drops) {
        for (ItemStack candidate : cell.candidates) {
            if (Block.getBlockFromItem(candidate.getItem()) == block) return true;
            for (ItemStack drop : drops) if (ItemKeys.same(candidate, drop)) return true;
        }
        for (ChannelDescription channel : ChannelDescription.getAll()
            .values())
            for (ItemStack candidate : channel.getItems()
                .keySet()) for (ItemStack drop : drops) if (ItemKeys.same(candidate, drop)) return true;
        return false;
    }

    static void give(EntityPlayerMP player, List<ItemStack> items) {
        if (player.capabilities.isCreativeMode) return;
        for (ItemStack original : items) {
            ItemStack stack = original.copy();
            if (!player.inventory.addItemStackToInventory(stack) && stack.stackSize > 0) player.worldObj
                .spawnEntityInWorld(new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, stack));
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }
}
