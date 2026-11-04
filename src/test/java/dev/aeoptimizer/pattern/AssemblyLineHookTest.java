package dev.aeoptimizer.pattern;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Pinned NEE injection/serialization checks without loading Minecraft client classes. */
public class AssemblyLineHookTest {

    private static final String PACKET = "com/github/vfyjxf/nee/network/packet/PacketNEIPatternRecipe";
    private static final String CLIENT = "com/github/vfyjxf/nee/nei/NEEPatternTerminalHandler";

    private static ClassNode read(String name) throws Exception {
        try (InputStream in = AssemblyLineHookTest.class.getClassLoader()
            .getResourceAsStream(name + ".class")) {
            assertNotNull(name, in);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(String type, String name) throws Exception {
        for (Object value : read(type).methods) {
            MethodNode m = (MethodNode) value;
            if (m.name.equals(name)) return m;
        }
        throw new AssertionError(type + "." + name);
    }

    @Test
    public void clientSelectorAndPacketFieldMatchPinnedNee() throws Exception {
        assertEquals(
            "(Lcodechicken/nei/recipe/IRecipeHandler;II)L" + PACKET + ";",
            method(CLIENT, "packProcessRecipe").desc);
        boolean found = false;
        for (Object f : read(PACKET).fields) {
            FieldNode field = (FieldNode) f;
            if (field.name.equals("input") || field.name.equals("output")) {
                assertEquals("Lnet/minecraft/nbt/NBTTagCompound;", field.desc);
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void serverHookRunsBeforeTheInputMatrixIsLoaded() throws Exception {
        boolean typedHandler = false;
        for (Object value : read(PACKET + "$Handler").methods) {
            MethodNode handler = (MethodNode) value;
            if (handler.name.equals("onMessage") && handler.desc.equals(
                "(L" + PACKET
                    + ";Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Lcpw/mods/fml/common/network/simpleimpl/IMessage;"))
                typedHandler = true;
        }
        assertTrue(typedHandler);
        MethodNode m = method(PACKET + "$Handler", "processRecipe");
        assertEquals("(Lappeng/container/implementations/ContainerPatternTerm;L" + PACKET + ";)V", m.desc);
        int matrixReads = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("getMatrix")) matrixReads++;
        }
        assertEquals(2, matrixReads);
    }

    @Test
    public void sourceMarkerSurvivesTheNativePacketTransport() throws Exception {
        assertCalls(method(PACKET, "toBytes"), "cpw/mods/fml/common/network/ByteBufUtils", "writeTag");
        assertCalls(method(PACKET, "fromBytes"), "cpw/mods/fml/common/network/ByteBufUtils", "readTag");
    }

    private static void assertCalls(MethodNode method, String owner, String name) {
        for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) i;
                if (call.owner.equals(owner) && call.name.equals(name)) return;
            }
        }
        fail("Missing " + owner + "." + name);
    }
}
