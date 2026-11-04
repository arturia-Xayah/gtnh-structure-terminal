package dev.aestoragevault.world;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import dev.aestoragevault.VaultMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/** Public StructureLib registration consumed by BlockRenderer6343's native NEI preview. */
public final class VaultConstructable implements IMultiblockInfoContainer<TileController> {

    private IStructureDefinition<TileController> definition;

    public static void register() {
        IMultiblockInfoContainer.registerTileClass(TileController.class, new VaultConstructable());
    }

    private synchronized IStructureDefinition<TileController> definition() {
        if (definition == null) definition = StructureDefinition.<TileController>builder()
            .addShape(
                "main",
                new String[][] { { "CCC", "CKC", "IAC" }, { "CCC", "C-C", "CCC" }, { "CCC", "CCC", "CCC" } })
            .addShape(
                "export",
                new String[][] { { "CCC", "CKC", "IEC" }, { "CCC", "C-C", "CCC" }, { "CCC", "CCC", "CCC" } })
            .addShape(
                "return",
                new String[][] { { "CCC", "CKC", "IAO" }, { "CCC", "C-C", "CCC" }, { "CCC", "CCC", "CCC" } })
            .addElement('K', controllerElement())
            .addElement('C', shell(ofBlock(GregTechAPI.sBlockCasings1, 6), 0))
            .addElement('A', shell(ofBlock(VaultMod.ACCESS, 0), 1))
            .addElement('E', shell(ofBlock(VaultMod.OUTPUT, 0), 1))
            .addElement('I', shell(bus(false), 2))
            .addElement('O', shell(bus(true), 3))
            .build();
        return definition;
    }

    private static IStructureElement<TileController> controllerElement() {
        final IStructureElement<TileController> block = ofBlockAnyMeta(VaultMod.CONTROLLER, 2);
        return new IStructureElement<TileController>() {

            public boolean check(TileController t, World w, int x, int y, int z) {
                return block.check(t, w, x, y, z);
            }

            public boolean spawnHint(TileController t, World w, int x, int y, int z, ItemStack trigger) {
                return block.spawnHint(t, w, x, y, z, trigger);
            }

            public boolean placeBlock(TileController t, World w, int x, int y, int z, ItemStack trigger) {
                // NEI starts with a tile but no block; real construction must preserve the
                // existing controller, its orientation, UUID and capacity ledger.
                return check(t, w, x, y, z) || block.placeBlock(t, w, x, y, z, trigger);
            }

            public BlocksToPlace getBlocksToPlace(TileController t, World w, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return block.getBlocksToPlace(t, w, x, y, z, trigger, env);
            }
        };
    }

    private String piece(ItemStack trigger) {
        return trigger.stackSize == 2 ? "export" : trigger.stackSize >= 3 ? "return" : "main";
    }

    private ExtendedFacing facing(TileController tile, ExtendedFacing fallback) {
        World world = tile.getWorldObj();
        return world.getBlock(tile.xCoord, tile.yCoord, tile.zCoord) == VaultMod.CONTROLLER ? ExtendedFacing.of(
            net.minecraftforge.common.util.ForgeDirection
                .getOrientation(world.getBlockMetadata(tile.xCoord, tile.yCoord, tile.zCoord)))
            : fallback;
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly, TileController tile, ExtendedFacing side) {
        definition().buildOrHints(
            tile,
            trigger,
            piece(trigger),
            tile.getWorldObj(),
            facing(tile, side),
            tile.xCoord,
            tile.yCoord,
            tile.zCoord,
            1,
            1,
            0,
            hintsOnly);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int budget, ISurvivalBuildEnvironment env, TileController tile,
        ExtendedFacing side) {
        int result = definition().survivalBuild(
            tile,
            trigger,
            piece(trigger),
            tile.getWorldObj(),
            facing(tile, side),
            tile.xCoord,
            tile.yCoord,
            tile.zCoord,
            1,
            1,
            0,
            budget,
            env,
            false);
        tile.checkStructure();
        return result;
    }

    @Override
    public String[] getDescription(ItemStack trigger) {
        return new String[] { StatCollector.translateToLocal("aestoragevault.structure.description"),
            StatCollector.translateToLocal("aestoragevault.structure.variants") };
    }

    private static boolean component(World w, int x, int y, int z) {
        if (VaultMod.isLuVCasing(w, x, y, z)) return true;
        TileEntity tile = w.getTileEntity(x, y, z);
        if (tile instanceof TileAccess) return true;
        if (!(tile instanceof IGregTechTileEntity)) return false;
        Object meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
        return meta != null
            && (meta.getClass() == MTEHatchInputBus.class || meta.getClass() == MTEHatchOutputBus.class);
    }

    /** Existing components can be anywhere on the shell; the displayed arrangement is only an example. */
    private static IStructureElement<TileController> shell(IStructureElement<TileController> fallback, int role) {
        return new IStructureElement<TileController>() {

            public boolean check(TileController t, World w, int x, int y, int z) {
                return component(w, x, y, z);
            }

            public boolean spawnHint(TileController t, World w, int x, int y, int z, ItemStack trigger) {
                return fallback.spawnHint(t, w, x, y, z, trigger);
            }

            public boolean placeBlock(TileController t, World w, int x, int y, int z, ItemStack trigger) {
                return fallback.placeBlock(t, w, x, y, z, trigger);
            }

            public BlocksToPlace getBlocksToPlace(TileController t, World w, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return fallback.getBlocksToPlace(t, w, x, y, z, trigger, env);
            }

            public PlaceResult survivalPlaceBlock(TileController t, World w, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                if (component(w, x, y, z)) return PlaceResult.SKIP;
                if (role != 0 && hasRole(t, role)) return VaultConstructable.<TileController>casing()
                    .survivalPlaceBlock(t, w, x, y, z, trigger, env);
                return fallback.survivalPlaceBlock(t, w, x, y, z, trigger, env);
            }
        };
    }

    private static <T> IStructureElement<T> casing() {
        return ofBlock(GregTechAPI.sBlockCasings1, 6);
    }

    private static boolean hasRole(TileController t, int role) {
        World w = t.getWorldObj();
        int front = w.getBlockMetadata(t.xCoord, t.yCoord, t.zCoord);
        int cx = t.xCoord - dev.aestoragevault.core.VaultGeometry.normal(front, 0);
        int cy = t.yCoord - dev.aestoragevault.core.VaultGeometry.normal(front, 1);
        int cz = t.zCoord - dev.aestoragevault.core.VaultGeometry.normal(front, 2);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (!dev.aestoragevault.core.VaultGeometry.shell(x, y, z) || !w.blockExists(cx + x, cy + y, cz + z))
                continue;
            TileEntity tile = w.getTileEntity(cx + x, cy + y, cz + z);
            if (role == 1 && tile instanceof TileAccess) return true;
            if (role >= 2 && tile instanceof IGregTechTileEntity) {
                Object meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
                if (meta != null && meta.getClass() == (role == 2 ? MTEHatchInputBus.class : MTEHatchOutputBus.class))
                    return true;
            }
        }
        return false;
    }

    private static IStructureElement<TileController> bus(boolean output) {
        return new IStructureElement<TileController>() {

            private ItemStack stack() {
                return (output ? ItemList.Hatch_Output_Bus_LuV : ItemList.Hatch_Input_Bus_LuV).get(1);
            }

            public boolean check(TileController t, World w, int x, int y, int z) {
                TileEntity tile = w.getTileEntity(x, y, z);
                if (!(tile instanceof IGregTechTileEntity)) return false;
                Object meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
                return meta != null && meta.getClass() == (output ? MTEHatchOutputBus.class : MTEHatchInputBus.class);
            }

            public boolean spawnHint(TileController t, World w, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(w, x, y, z, StructureLibAPI.getBlockHint(), output ? 1 : 0);
                return true;
            }

            public boolean placeBlock(TileController t, World w, int x, int y, int z, ItemStack trigger) {
                if (!w.setBlock(x, y, z, GregTechAPI.sBlockMachines, 0, 2)) return false;
                TileEntity tile = w.getTileEntity(x, y, z);
                if (!(tile instanceof IGregTechTileEntity)) return false;
                ((IGregTechTileEntity) tile).setInitialValuesAsNBT(null, (short) stack().getItemDamage());
                ((IGregTechTileEntity) tile).setFrontFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
                return true;
            }

            public BlocksToPlace getBlocksToPlace(TileController t, World w, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                List<ItemStack> stacks = new ArrayList<>();
                stacks.add(stack());
                for (gregtech.api.interfaces.metatileentity.IMetaTileEntity meta : GregTechAPI.METATILEENTITIES)
                    if (meta != null && meta.getClass() == (output ? MTEHatchOutputBus.class : MTEHatchInputBus.class))
                        stacks.add(meta.getStackForm(1));
                return BlocksToPlace.create(stacks);
            }
        };
    }
}
