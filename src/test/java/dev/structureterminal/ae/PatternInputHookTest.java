package dev.structureterminal.ae;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Checks pinned AE allocation sites and accessors using bytecode only; no client class initialization. */
public class PatternInputHookTest {

    private static ClassNode read(String name) throws Exception {
        try (InputStream in = PatternInputHookTest.class.getClassLoader()
            .getResourceAsStream(name + ".class")) {
            assertNotNull("Class missing: " + name, in);
            ClassNode result = new ClassNode();
            new ClassReader(in).accept(result, 0);
            return result;
        }
    }

    private static MethodNode method(ClassNode type, String name) {
        for (Object value : type.methods) {
            MethodNode m = (MethodNode) value;
            if (m.name.equals(name)) return m;
        }
        throw new AssertionError(type.name + "." + name + " missing");
    }

    @Test
    public void firstAllocationIsInputAndSecondIsOutput() throws Exception {
        MethodNode method = method(read("appeng/client/gui/implementations/GuiPatternTerm"), "initVirtualSlots");
        List<String> inventories = new ArrayList<>();
        String allocationInventory = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction
            != null; instruction = instruction.getNext()) {
            if (instruction instanceof TypeInsnNode && instruction.getOpcode() == Opcodes.NEW
                && ((TypeInsnNode) instruction).desc.equals("appeng/client/gui/slots/VirtualMEPatternSlot")) {
                allocationInventory = "missing";
            }
            if (instruction instanceof FieldInsnNode && allocationInventory != null) {
                String field = ((FieldInsnNode) instruction).name;
                if (field.equals("inputsSync") || field.equals("outputsSync")) allocationInventory = field;
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("appeng/client/gui/slots/VirtualMEPatternSlot") && call.name.equals("<init>")) {
                    assertEquals(
                        "(IILappeng/container/sync/handlers/AEStackInventorySyncHandler;ILappeng/client/gui/slots/VirtualMEPhantomSlot$TypeAcceptPredicate;)V",
                        call.desc);
                    inventories.add(allocationInventory);
                    allocationInventory = null;
                }
            }
        }
        assertEquals(java.util.Arrays.asList("inputsSync", "outputsSync"), inventories);
    }

    @Test
    public void repoAccessorsMatchPinnedPrivateFields() throws Exception {
        assertField("appeng/client/gui/implementations/GuiMEMonitorable", "repo", "Lappeng/client/me/ItemRepo;");
        assertField("appeng/client/me/ItemRepo", "list", "Lappeng/api/storage/data/IItemList;");
        assertEquals("()Z", method(read("appeng/client/me/ItemRepo"), "hasPower").desc);
    }

    private static void assertField(String type, String name, String descriptor) throws Exception {
        for (Object value : read(type).fields) {
            FieldNode field = (FieldNode) value;
            if (field.name.equals(name)) {
                assertEquals(descriptor, field.desc);
                assertTrue((field.access & Opcodes.ACC_FINAL) != 0);
                return;
            }
        }
        fail(type + "." + name + " missing");
    }

    @Test
    public void extendedTerminalUsesTheSameInputFactory() throws Exception {
        ClassNode extended = read("appeng/client/gui/implementations/GuiPatternTermEx");
        assertEquals("appeng/client/gui/implementations/GuiPatternTerm", extended.superName);
        for (Object value : extended.methods) assertNotEquals("initVirtualSlots", ((MethodNode) value).name);
    }

    @Test
    public void tooltipHookMatchesNativeTooltipAndHoverSignatures() throws Exception {
        ClassNode gui = read("appeng/client/gui/AEBaseGui");
        assertEquals(
            "(Lnet/minecraft/item/ItemStack;IILjava/util/List;)Ljava/util/List;",
            method(gui, "handleItemTooltip").desc);
        assertEquals("()Lappeng/client/gui/slots/VirtualMESlot;", method(gui, "getVirtualMESlotUnderMouse").desc);
    }
}
