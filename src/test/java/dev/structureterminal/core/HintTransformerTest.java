package dev.structureterminal.core;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.structureterminal.coremod.HintTransformer;

public final class HintTransformerTest {

    private static final class FixtureLoader extends ClassLoader {

        FixtureLoader() {
            super(HintTransformerTest.class.getClassLoader());
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static byte[] hook() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        w.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "dev/structureterminal/bridge/StructureScanner",
            null,
            "java/lang/Object",
            null);
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "active", "Z", null, null)
            .visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "suppressHints", "()Z", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, "dev/structureterminal/bridge/StructureScanner", "active", "Z");
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        m = w.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "collectDefinition",
            HintTransformer.ITERATE_DESC,
            null,
            null);
        m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] fixture() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Hints", null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null)
            .visitEnd();
        for (String name : new String[] { "hintParticle", "hintParticleTinted" })
            for (String desc : new String[] { "()V", "(I)V" }) {
                MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, desc, null, null);
                m.visitCode();
                m.visitFieldInsn(Opcodes.GETSTATIC, "fixture/Hints", "calls", "I");
                m.visitInsn(Opcodes.ICONST_1);
                m.visitInsn(Opcodes.IADD);
                m.visitFieldInsn(Opcodes.PUTSTATIC, "fixture/Hints", "calls", "I");
                m.visitInsn(Opcodes.RETURN);
                m.visitMaxs(0, 0);
                m.visitEnd();
            }
        w.visitEnd();
        return w.toByteArray();
    }

    @Test
    public void nativeHintsRemainEnabledOutsidePreview() throws Exception {
        FixtureLoader loader = new FixtureLoader();
        Class<?> hook = loader.define("dev.structureterminal.bridge.StructureScanner", hook());
        byte[] transformed = new HintTransformer().transform(HintTransformer.TARGET, HintTransformer.TARGET, fixture());
        Class<?> c = loader.define("fixture.Hints", transformed);
        Method plain = c.getMethod("hintParticle"), tinted = c.getMethod("hintParticleTinted", int.class);
        plain.invoke(null);
        tinted.invoke(null, 1);
        assertEquals(
            2,
            c.getField("calls")
                .getInt(null));
        hook.getField("active")
            .setBoolean(null, true);
        plain.invoke(null);
        tinted.invoke(null, 1);
        assertEquals(
            2,
            c.getField("calls")
                .getInt(null));
        hook.getField("active")
            .setBoolean(null, false);
        plain.invoke(null);
        assertEquals(
            3,
            c.getField("calls")
                .getInt(null));
    }

    @Test
    public void otherClassesRemainByteIdentical() {
        byte[] bytes = fixture();
        assertSame(bytes, new HintTransformer().transform("other.Class", "other.Class", bytes));
    }

    @Test
    public void onlyPreviewTraversalUsesReadonlyCollector() throws Exception {
        FixtureLoader loader = new FixtureLoader();
        Class<?> hook = loader.define("dev.structureterminal.bridge.StructureScanner", hook());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Definition", null, "java/lang/Object", null);
        MethodVisitor body = writer
            .visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "iterate", HintTransformer.ITERATE_DESC, null, null);
        body.visitCode();
        body.visitInsn(Opcodes.ICONST_0);
        body.visitInsn(Opcodes.IRETURN);
        body.visitMaxs(0, 0);
        body.visitEnd();
        writer.visitEnd();
        Class<?> definition = loader.define(
            "fixture.Definition",
            new HintTransformer()
                .transform(HintTransformer.DEFINITION, HintTransformer.DEFINITION, writer.toByteArray()));
        Method method = definition.getDeclaredMethods()[0];
        Object[] arguments = { null, null, null, null, null, 0, 0, 0, 0, 0, 0, true, null };
        assertEquals(false, method.invoke(null, arguments));
        hook.getField("active")
            .setBoolean(null, true);
        assertEquals(true, method.invoke(null, arguments));
        arguments[11] = false;
        assertEquals(false, method.invoke(null, arguments));
        arguments[11] = true;
        arguments[12] = Boolean.TRUE;
        assertEquals(false, method.invoke(null, arguments));
    }
}
