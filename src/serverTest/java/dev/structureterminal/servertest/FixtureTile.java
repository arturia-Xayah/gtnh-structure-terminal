package dev.structureterminal.servertest;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

/** A real-world StructureLib fixture; deliberately not a claim of a tested GregTech machine. */
public final class FixtureTile extends TileEntity implements ISurvivalConstructable, IAlignment {

    public boolean running;
    public int setterCalls, checkCalls, progress;
    private ExtendedFacing facing = ExtendedFacing.DEFAULT;
    private static final IStructureDefinition<FixtureTile> DEFINITION = StructureDefinition.<FixtureTile>builder()
        .addShape("main", new String[][] { { "AAA", "A~A", "AAA" }, { "GGG", "G-G", "GGG" }, { "CCC", "CAC", "CCC" } })
        .addElement('A', element(0))
        .addElement('G', StructureUtility.withChannel("test_glass", element(1)))
        .addElement('C', StructureUtility.withChannel("test_coil", element(2)))
        .build();

    private static IStructureElement<FixtureTile> element(final int kind) {
        return new IStructureElement<FixtureTile>() {

            private Block block(ItemStack trigger) {
                return kind == 0 ? Blocks.iron_block
                    : kind == 1 ? (trigger.stackSize == 1 ? Blocks.glass : Blocks.stained_glass) : Blocks.wool;
            }

            private int meta(ItemStack trigger) {
                return kind == 2 ? (trigger.stackSize == 1 ? 14 : 5) : kind == 1 && trigger.stackSize > 1 ? 5 : 0;
            }

            private boolean matches(World world, int x, int y, int z, ItemStack trigger) {
                return world.getBlock(x, y, z) == block(trigger) && world.getBlockMetadata(x, y, z) == meta(trigger);
            }

            @Override
            public boolean check(FixtureTile tile, World world, int x, int y, int z) {
                tile.checkCalls++;
                return matches(world, x, y, z, new ItemStack(Blocks.stone));
            }

            @Override
            public boolean couldBeValid(FixtureTile tile, World world, int x, int y, int z, ItemStack trigger) {
                return matches(world, x, y, z, trigger);
            }

            @Override
            public boolean spawnHint(FixtureTile tile, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, block(trigger), meta(trigger));
                return true;
            }

            @Override
            public boolean placeBlock(FixtureTile tile, World world, int x, int y, int z, ItemStack trigger) {
                return world.setBlock(x, y, z, block(trigger), meta(trigger), 3);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(FixtureTile tile, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return BlocksToPlace.create(block(trigger), meta(trigger));
            }

            @Override
            public PlaceResult survivalPlaceBlock(FixtureTile tile, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                if (matches(world, x, y, z, trigger)) return PlaceResult.SKIP;
                return StructureUtility.survivalPlaceBlock(
                    block(trigger),
                    meta(trigger),
                    world,
                    x,
                    y,
                    z,
                    env.getSource(),
                    env.getActor(),
                    env.getChatter());
            }

            @Override
            public List<String> getDescription(FixtureTile tile) {
                return Collections.singletonList("fixture-" + kind);
            }
        };
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        if (hintsOnly) DEFINITION.hints(this, trigger, "main", worldObj, facing, xCoord, yCoord, zCoord, 1, 1, 0);
        else DEFINITION.build(this, trigger, "main", worldObj, facing, xCoord, yCoord, zCoord, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int budget, ISurvivalBuildEnvironment env) {
        return DEFINITION.survivalBuild(
            this,
            trigger,
            "main",
            worldObj,
            facing,
            xCoord,
            yCoord,
            zCoord,
            1,
            1,
            0,
            budget,
            env,
            false);
    }

    @Override
    public String[] getStructureDescription(ItemStack trigger) {
        return new String[] { "Headless StructureLib test fixture" };
    }

    @Override
    public ExtendedFacing getExtendedFacing() {
        return facing;
    }

    @Override
    public void setExtendedFacing(ExtendedFacing value) {
        if (facing != value) {
            setterCalls++;
            running = false;
            facing = value;
        }
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return IAlignmentLimits.UNLIMITED;
    }

    @Override
    public boolean isNewExtendedFacingValid(ForgeDirection direction, Rotation rotation, Flip flip) {
        return true;
    }

    @Override
    public void updateEntity() {
        if (running) progress++;
    }
}
