package dev.modernization.packtest.quantity.probe;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Explicit -Dfml.coreMods.load opt-in, never included in the player JAR. */
public final class QuantityTickTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.server.MinecraftServer".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int found = 0;
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (!method.desc.equals("()V")
                || !(method.name.equals("u") || method.name.equals("func_71217_p") || method.name.equals("tick")))
                continue;
            found++;
            method.instructions.insert(
                new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "dev/modernization/packtest/quantity/probe/TickProbe",
                    "begin",
                    "()V",
                    false));
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction
                != null; instruction = instruction.getNext())
                if (instruction.getOpcode() == Opcodes.RETURN) method.instructions.insertBefore(
                    instruction,
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "dev/modernization/packtest/quantity/probe/TickProbe",
                        "end",
                        "()V",
                        false));
        }
        if (found != 1) throw new IllegalStateException("Expected one MinecraftServer.tick, found " + found);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(out);
        return out.toByteArray();
    }
}
