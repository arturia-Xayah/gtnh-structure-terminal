package dev.structureterminal.packing;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Client selectors checked against the pinned jar without starting a Minecraft client. */
public class IntegratedPatternHookTest {

    private ClassNode read(String name) throws Exception {
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream(name + ".class")) {
            assertNotNull(name, stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    @Test
    public void terminalClientUsesStableChildSearchAndValidationHooks() throws Exception {
        ClassNode node = read("appeng/client/gui/implementations/GuiInterfaceTerminal");
        Set<String> actual = new HashSet<>();
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            actual.add(method.name + method.desc);
        }
        assertTrue(actual.contains("itemStackMatchesSearchTerm(Lnet/minecraft/item/ItemStack;Ljava/lang/String;Z)Z"));
        assertTrue(actual.contains("recipeIsBroken(Lnet/minecraft/item/ItemStack;)Z"));
        assertTrue(actual.contains("isUseSubstitute(Lnet/minecraft/item/ItemStack;)Z"));
        assertTrue(
            actual.contains(
                "hasInvalidTypeStack(Lnet/minecraft/item/ItemStack;[Lappeng/api/storage/data/IAEStackType;)Z"));
    }

    @Test
    public void terminalRedirectTouchesOnlyTheCursorItemGuard() throws Exception {
        ClassNode node = read("appeng/container/implementations/ContainerInterfaceTerminal");
        int calls = 0;
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (!method.name.equals("doAction")) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.owner.equals("net/minecraft/item/ItemStack") && call.name.equals("getItem")) calls++;
                }
            }
        }
        assertEquals(1, calls);
    }
}
