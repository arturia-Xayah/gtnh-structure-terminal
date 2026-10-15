package dev.structureterminal.coremod;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Suppress visual particles only while our read-only collector is active; native projector behavior is preserved. */
public final class HintTransformer implements IClassTransformer {

    public static final String TARGET = "com.gtnewhorizon.structurelib.StructureLibAPI";
    public static final String DEFINITION = "com.gtnewhorizon.structurelib.structure.IStructureDefinition";
    public static final String ITERATE_DESC = "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;[Lcom/gtnewhorizon/structurelib/structure/IStructureElement;Lnet/minecraft/world/World;Lcom/gtnewhorizon/structurelib/alignment/enumerable/ExtendedFacing;IIIIIIZLjava/lang/Boolean;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        boolean definition = DEFINITION.equals(name) || DEFINITION.equals(transformedName);
        if (!definition && !TARGET.equals(name) && !TARGET.equals(transformedName)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (Object object : node.methods) {
            MethodNode method = (MethodNode) object;
            if (definition) {
                if (!method.name.equals("iterate") || !method.desc.equals(ITERATE_DESC)) continue;
                InsnList hook = new InsnList();
                LabelNode proceed = new LabelNode();
                hook.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "dev/structureterminal/bridge/StructureScanner",
                        "suppressHints",
                        "()Z",
                        false));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 11));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 12));
                hook.add(new JumpInsnNode(Opcodes.IFNONNULL, proceed));
                for (int i = 0; i < 13; i++)
                    hook.add(new VarInsnNode(i >= 5 && i <= 11 ? Opcodes.ILOAD : Opcodes.ALOAD, i));
                hook.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "dev/structureterminal/bridge/StructureScanner",
                        "collectDefinition",
                        ITERATE_DESC,
                        false));
                hook.add(new InsnNode(Opcodes.IRETURN));
                hook.add(proceed);
                hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(hook);
                changed++;
                continue;
            }
            if ((method.access & Opcodes.ACC_STATIC) == 0
                || (!method.name.equals("hintParticle") && !method.name.equals("hintParticleTinted"))) continue;
            if (!method.desc.endsWith(")V")) continue;
            InsnList guard = new InsnList();
            LabelNode proceed = new LabelNode();
            guard.add(
                new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "dev/structureterminal/bridge/StructureScanner",
                    "suppressHints",
                    "()Z",
                    false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(proceed);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            changed++;
        }
        if (changed != (definition ? 1 : 4))
            throw new IllegalStateException("StructureLib collection API changed: unexpected methods " + changed);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
