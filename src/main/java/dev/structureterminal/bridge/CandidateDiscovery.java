package dev.structureterminal.bridge;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;

/** StructureLib chains expose both explicit fallback stacks and predicate-only alternatives. */
final class CandidateDiscovery {

    static final class Result {

        final List<ItemStack> items = new ArrayList<ItemStack>();
        boolean incomplete;
    }

    private CandidateDiscovery() {}

    static Result resolve(IStructureElement<?> element, IStructureElement.BlocksToPlace blocks,
        Iterable<ItemStack> pool, long deadline) {
        Result result = new Result();
        if (blocks == null) return result;
        Map<String, ItemStack> unique = new LinkedHashMap<String, ItemStack>();
        int inspected = 0;
        if (blocks.getStacks() != null) for (ItemStack stack : blocks.getStacks()) {
            if (++inspected > 4096 || System.nanoTime() > deadline) {
                result.incomplete = true;
                break;
            }
            put(unique, stack);
        }
        if (blocks.getPredicate() != null && (unique.isEmpty()
            || containsChain(element, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), 0))) {
            for (ItemStack stack : pool) {
                if (unique.size() >= 4096 || System.nanoTime() > deadline) {
                    result.incomplete = true;
                    break;
                }
                try {
                    if (blocks.getPredicate()
                        .test(stack)) put(unique, stack);
                } catch (RuntimeException invalidCandidate) {
                    result.incomplete = true;
                }
            }
        }
        result.items.addAll(unique.values());
        return result;
    }

    private static void put(Map<String, ItemStack> target, ItemStack stack) {
        if (stack != null && stack.getItem() != null) {
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            target.putIfAbsent(ItemKeys.key(copy), copy);
        }
    }

    private static boolean containsChain(Object value, Set<Object> seen, int depth) {
        if (value == null || depth > 20 || seen.size() > 1024 || !seen.add(value)) return false;
        if (value instanceof IStructureElementChain) return true;
        if (value.getClass()
            .isArray()) {
            for (int i = 0; i < Array.getLength(value); i++)
                if (containsChain(Array.get(value, i), seen, depth + 1)) return true;
        } else if (value instanceof IStructureElement)
            for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass())
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType()
                        .isPrimitive()) continue;
                    try {
                        field.setAccessible(true);
                        Object child = field.get(value);
                        if ((child instanceof IStructureElement || child != null && child.getClass()
                            .isArray()) && containsChain(child, seen, depth + 1)) return true;
                    } catch (ReflectiveOperationException | RuntimeException ignored) {}
                }
        return false;
    }
}
