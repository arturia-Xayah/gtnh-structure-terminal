package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Compatibility contracts checked as bytecode without initializing a game client. */
public class VaultRenderHookTest {

    private ClassNode read(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream(name + ".class")) {
            assertNotNull(name, in);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    @Test
    public void nativeCasingRendererSignatureMatchesClientHook() throws Exception {
        boolean found = false;
        for (Object entry : read("gregtech/common/render/GTRendererCasing").methods) {
            MethodNode method = (MethodNode) entry;
            if (method.name.equals("renderWorldBlock")) {
                assertEquals(
                    "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;ILnet/minecraft/client/renderer/RenderBlocks;)Z",
                    method.desc);
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void serverClassInitializerNeverTouchesStrippedClientIconField() throws Exception {
        for (Object entry : read("dev/aestoragevault/world/VaultSkin").methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals("<clinit>")) continue;
            for (AbstractInsnNode in = method.instructions.getFirst(); in != null; in = in.getNext())
                if (in instanceof FieldInsnNode) assertNotEquals("icons", ((FieldInsnNode) in).name);
        }
    }
}
