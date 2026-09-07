package com.gtnewhorizons.angelica.glsm.loading;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModRedirectorTest {

    private static final String EXTRA_EXCLUSION = "com.example.excluded";

    private static ClassNode emptyClass() {
        final ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/TestClass";
        cn.superName = "java/lang/Object";
        cn.methods = new ArrayList<>();

        final MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);
        return cn;
    }

    private static byte[] bytesWithoutGlCall() {
        return toBytes(emptyClass());
    }

    private static byte[] bytesWithGlCall() {
        final ClassNode cn = emptyClass();
        final MethodNode mn = cn.methods.get(0);
        mn.instructions.clear();
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        return toBytes(cn);
    }

    private static byte[] toBytes(ClassNode cn) {
        final ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static ModRedirector redirector() {
        return new ModRedirector(ClassDump.DISABLED, EXTRA_EXCLUSION);
    }

    private static void assertStandardExclusions(String[] actual) {
        final List<String> exclusions = Arrays.asList(actual);
        final String[] coreExclusions = new GLSMRedirector().getCoreExclusions();
        for (String coreExclusion : coreExclusions) {
            assertTrue(exclusions.contains(coreExclusion), coreExclusion);
        }
        assertTrue(exclusions.contains("com.gtnewhorizons.angelica.lwjgl3."));
        assertTrue(exclusions.contains(EXTRA_EXCLUSION));
        assertEquals(coreExclusions.length + 2, exclusions.size(), exclusions.toString());
    }

    @Test
    void exclusionsCombineCoreLwjgl3AndExtra() {
        assertStandardExclusions(redirector().getTransformerExclusions());
    }

    @Test
    void earlyRedirectorExclusionsCombineCoreLwjgl3AndExtra() {
        assertStandardExclusions(new EarlyRedirectorCore(EXTRA_EXCLUSION).getTransformerExclusions());
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(redirector().transform("com.example.TestClass", null, this));
    }

    @Test
    void excludedPrefixReturnsOriginalArray() {
        final byte[] original = bytesWithGlCall();
        assertSame(original, redirector().transform(EXTRA_EXCLUSION + ".TestClass", original, this));
    }

    @Test
    void coreExcludedPrefixReturnsOriginalArray() {
        final byte[] original = bytesWithGlCall();
        assertSame(original, redirector().transform("org.lwjgl.opengl.TestClass", original, this));
    }

    @Test
    void unchangedClassReturnsOriginalArray() {
        final byte[] original = bytesWithoutGlCall();
        assertSame(original, redirector().transform("com.example.TestClass", original, this));
    }

    @Test
    void redirectedClassReturnsNewArray() {
        final byte[] original = bytesWithGlCall();
        assertNotSame(original, redirector().transform("com.example.TestClass", original, this));
    }

    @Test
    void earlyRedirectorSkipsClassesOutsideItsTargets() {
        final byte[] original = bytesWithGlCall();
        final EarlyRedirectorCore early = new EarlyRedirectorCore(EXTRA_EXCLUSION);
        assertSame(original, early.transform("com.example.TestClass", original, this));
    }

    @Test
    void earlyRedirectorTransformsItsTargets() {
        final byte[] original = bytesWithGlCall();
        final EarlyRedirectorCore early = new EarlyRedirectorCore(EXTRA_EXCLUSION);
        final String target = EcosystemNarrowRules.EARLY_REDIRECTOR_TARGETS[0] + "TestClass";
        assertNotSame(original, early.transform(target, original, this));
    }
}
