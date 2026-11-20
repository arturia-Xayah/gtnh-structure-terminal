package dev.structureterminal.client.projection;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import dev.structureterminal.projection.SceneView;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Detached read-only block access. Local (0,0,0) is the voxel containing the destination camera. */
final class SceneTerrain implements IBlockAccess {

    final int[] blocks;
    final BiomeGenBase biome;
    private final Map<Integer, TileEntity> tiles = new HashMap<>();

    SceneTerrain(int[] blocks, NBTTagCompound view) {
        this.blocks = blocks;
        BiomeGenBase found = BiomeGenBase.getBiome(view.getInteger("biome"));
        biome = found == null ? BiomeGenBase.plains : found;
        NBTTagList machines = view.getTagList("machines", 10);
        for (int j = 0; j < machines.tagCount(); j++) {
            NBTTagCompound info = machines.getCompoundTagAt(j);
            int i = info.getInteger("i");
            if (i < 0 || i >= blocks.length) continue;
            // Only the public machine type, colour, facing and running state are copied.
            BaseMetaTileEntity tile = new BaseMetaTileEntity();
            tile.xCoord = i % 33 - 16;
            tile.zCoord = i / 33 % 33 - 16;
            tile.yCoord = i / 1089 - 16;
            try {
                tile.setInitialValuesAsNBT((NBTTagCompound) info.copy(), (short) info.getInteger("mID"));
                if (tile.getMetaTileEntity() != null) tiles.put(i, tile);
            } catch (RuntimeException ignored) {/* Use the block's atlas texture when its renderer needs a live tile. */}
        }
    }

    private static boolean inside(int x, int y, int z) {
        return Math.abs(x) <= 16 && Math.abs(y) <= 16 && Math.abs(z) <= 16;
    }

    private int cell(int x, int y, int z) {
        return inside(x, y, z) ? blocks[SceneView.index(x, y, z)] : 0;
    }

    public Block getBlock(int x, int y, int z) {
        Block b = Block.getBlockById(cell(x, y, z) >>> 8);
        return b == null ? Blocks.air : b;
    }

    public int getBlockMetadata(int x, int y, int z) {
        return cell(x, y, z) >> 4 & 15;
    }

    public TileEntity getTileEntity(int x, int y, int z) {
        return inside(x, y, z) ? tiles.get(SceneView.index(x, y, z)) : null;
    }

    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) {
        int light = cell(x, y, z) & 15;
        return light << 20 | Math.max(min, light) << 4;
    }

    public boolean isAirBlock(int x, int y, int z) {
        return (cell(x, y, z) >>> 8) == 0;
    }

    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return 0;
    }

    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return biome;
    }

    public int getHeight() {
        return 256;
    }

    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
        return getBlock(x, y, z).isOpaqueCube();
    }

    void mesh(int pass) {
        RenderBlocks rb = new RenderBlocks(this);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        try {
            for (int i = 0; i < blocks.length; i++) {
                if ((blocks[i] >>> 8) == 0) continue;
                int x = i % 33 - 16, z = i / 33 % 33 - 16, y = i / 1089 - 16;
                Block b = getBlock(x, y, z);
                if ((b.getRenderBlockPass() > 0 ? 1 : 0) != pass) continue;
                if (getBlock(x + 1, y, z).isOpaqueCube() && getBlock(x - 1, y, z).isOpaqueCube()
                    && getBlock(x, y + 1, z).isOpaqueCube()
                    && getBlock(x, y - 1, z).isOpaqueCube()
                    && getBlock(x, y, z + 1).isOpaqueCube()
                    && getBlock(x, y, z - 1).isOpaqueCube()) continue;
                int type = b.getRenderType();
                if (type >= 0 && (type <= 41 || tiles.containsKey(i))) {
                    try {
                        if (rb.renderBlockByRenderType(b, x, y, z)) continue;
                    } catch (RuntimeException ignored) {/* Mod models may require client-world services. */}
                }
                rb.setRenderBounds(0, 0, 0, 1, 1, 1);
                rb.renderStandardBlock(b, x, y, z);
            }
        } finally {
            t.draw();
        }
    }
}
