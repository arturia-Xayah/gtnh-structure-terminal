package dev.structureterminal.packing;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** The restoration boundary must remain before native grid joining in the pinned AE build. */
public class PackedPartHookTest {

    @Test
    public void pinnedCableBusHasBothLifecycleHooks() throws Exception {
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("appeng/parts/CableBusContainer.class")) {
            assertNotNull(stream);
            ClassNode type = new ClassNode();
            new ClassReader(stream).accept(type, 0);
            int hooks = 0;
            for (Object value : type.methods) {
                MethodNode method = (MethodNode) value;
                if (!method.name.equals("addPart")) continue;
                assertEquals(
                    "(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraftforge/common/util/ForgeDirection;",
                    method.desc);
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.owner.equals("appeng/api/parts/IPart") && call.name.equals("addToWorld")
                        && call.desc.equals("()V")) hooks++;
                }
            }
            assertEquals(2, hooks);
        }
    }
}
