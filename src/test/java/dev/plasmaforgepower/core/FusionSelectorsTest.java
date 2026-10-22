package dev.plasmaforgepower.core;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Bytecode compatibility checks without constructing a game or loading GT runtime classes. */
public final class FusionSelectorsTest {

    private ClassNode read(String type) throws Exception {
        java.net.URL resource = getClass().getClassLoader()
            .getResource(type + ".class");
        assertNotNull(type, resource);
        // GT is a multi-release JAR. Inspect the Java 8 root entry explicitly, without loading classes.
        java.net.JarURLConnection connection = (java.net.JarURLConnection) resource.openConnection();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(
            new java.io.File(
                connection.getJarFileURL()
                    .toURI()));
            InputStream in = zip.getInputStream(zip.getEntry(type + ".class"))) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    @Test
    public void nativeTraversalCallsUseExactDescriptors() throws Exception {
        ClassNode node = read("gregtech/api/metatileentity/implementations/MTEMultiBlockBase");
        int dual = 0, fluid = 0;
        for (Object o : node.methods) {
            MethodNode m = (MethodNode) o;
            if (!m.name.equals("doCheckRecipe")) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext())
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) n;
                    if (call.name.equals("getSharedItems")) {
                        assertEquals("()[Lnet/minecraft/item/ItemStack;", call.desc);
                        dual++;
                    }
                    if (call.name.equals("getStoredFluidsForColor")) {
                        assertEquals("(Ljava/util/Optional;)Ljava/util/ArrayList;", call.desc);
                        fluid++;
                    }
                }
        }
        assertEquals(1, dual);
        assertEquals(1, fluid);
    }

    @Test
    public void fusionValidatorsRetainNativeStartupCheck() throws Exception {
        for (String type : new String[] { "gregtech/common/tileentities/machines/multi/MTEFusionComputer$2",
            "goodgenerator/blocks/tileEntity/base/MTELargeFusionComputer$2" }) {
            ClassNode node = read(type);
            boolean validator = false, threshold = false;
            for (Object o : node.methods) {
                MethodNode m = (MethodNode) o;
                if (!m.name.equals("validateRecipe")) continue;
                validator = true;
                assertEquals("(Lgregtech/api/util/GTRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;", m.desc);
                for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext())
                    if (n instanceof FieldInsnNode && ((FieldInsnNode) n).name.equals("FUSION_THRESHOLD"))
                        threshold = true;
            }
            assertTrue(type, validator && threshold);
        }
    }
}
