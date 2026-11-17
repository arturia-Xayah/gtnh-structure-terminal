package dev.aestoragevault.core;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
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
    public void nativeCasingRendererConsumesWorldScopedOverlays() throws Exception {
        boolean found = false;
        for (Object entry : read("gregtech/common/render/GTRendererCasing").methods) {
            MethodNode method = (MethodNode) entry;
            if (method.name.equals("renderWorldBlock")) {
                assertEquals(
                    "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;ILnet/minecraft/client/renderer/RenderBlocks;)Z",
                    method.desc);
                for (AbstractInsnNode in = method.instructions.getFirst(); in != null; in = in.getNext()) {
                    if (in instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) in;
                        if (call.owner.equals("gregtech/api/render/RenderOverlay") && call.name.equals("get"))
                            found = true;
                    }
                }
            }
        }
        assertTrue(found);
    }

    @Test
    public void manualShellLightingIsIsolatedFromGtDeferredAo() throws Exception {
        MethodNode render = null;
        for (Object entry : read("dev/aestoragevault/world/VaultSkin$ShellTexture").methods) {
            MethodNode method = (MethodNode) entry;
            if (method.name.equals("render")) render = method;
        }
        assertNotNull(render);
        int stop = -1, draw = -1, restart = -1, position = 0;
        for (AbstractInsnNode instruction : render.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.name.equals("finishLighting")) stop = position;
                if (call.name.equals("renderShellFace")) draw = position;
                if (call.name.equals("tryStartAngelicaLighting") && restart < 0) restart = position;
            }
            position++;
        }
        assertTrue(
            "Clear the GT/Angelica AO marker before emitting manually shaded vertices",
            stop >= 0 && stop < draw);
        assertTrue("Restore native lighting for subsequent hatch and cover layers", restart > draw);
        assertFalse("Lighting restoration must also run if face rendering throws", render.tryCatchBlocks.isEmpty());

        // Check the pinned GT implementation: disabling RenderBlocks.enableAO alone does not clear these.
        boolean rendererMarker = false, tessellatorMarker = false;
        for (Object entry : read("gregtech/common/render/SBRWorldContext").methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals("finishLighting")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                String name = ((MethodInsnNode) instruction).name;
                rendererMarker |= name.equals("angelica$setApplyingCeleritasAO");
                tessellatorMarker |= name.equals("angelica$setAppliedAo");
            }
        }
        assertTrue("GT must clear both render and emitted-vertex AO state", rendererMarker && tessellatorMarker);
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
