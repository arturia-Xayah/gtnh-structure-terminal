package dev.structureterminal.bridge;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;

public final class CandidateDiscoveryTest {

    @Test
    public void chainRetainsCasingAndPredicateOnlyHatch() throws Exception {
        Item item = new Item();
        ItemStack casing = new ItemStack(item, 1, 1), hatch = new ItemStack(item, 1, 2),
            unrelated = new ItemStack(item, 1, 3);
        IStructureElement<Object> chain = (IStructureElementChain<Object>) () -> new IStructureElement[0];
        IStructureElement.BlocksToPlace blocks = blocks(
            s -> s.getItemDamage() == 1 || s.getItemDamage() == 2,
            Collections.singletonList(casing));
        CandidateDiscovery.Result result = CandidateDiscovery
            .resolve(chain, blocks, Arrays.asList(hatch, unrelated, casing), System.nanoTime() + 1000000000L);
        assertEquals(2, result.items.size());
        assertTrue(ItemKeys.same(hatch, result.items.get(1)));
        assertFalse(result.incomplete);
    }

    @Test
    public void emptyExplicitListDoesNotHidePredicateMatches() throws Exception {
        ItemStack hatch = new ItemStack(new Item(), 1, 4);
        IStructureElement.BlocksToPlace blocks = blocks(s -> s.getItemDamage() == 4, Collections.emptyList());
        CandidateDiscovery.Result result = CandidateDiscovery
            .resolve(null, blocks, Collections.singletonList(hatch), System.nanoTime() + 1000000000L);
        assertEquals(1, result.items.size());
        assertTrue(ItemKeys.same(hatch, result.items.get(0)));
    }

    private static IStructureElement.BlocksToPlace blocks(java.util.function.Predicate<ItemStack> predicate,
        Iterable<ItemStack> stacks) throws Exception {
        java.lang.reflect.Constructor<IStructureElement.BlocksToPlace> constructor = IStructureElement.BlocksToPlace.class
            .getDeclaredConstructor(java.util.function.Predicate.class, Iterable.class);
        constructor.setAccessible(true);
        return constructor.newInstance(predicate, stacks);
    }
}
