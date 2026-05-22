package com.gtnewhorizons.angelica.glsm.redirect;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLSMRedirectorTest {

    private static final String GLSM = GLSMRedirector.getTargetClassName();

    private static final Handle LAMBDA_METAFACTORY = new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;",
            false);

    private static ClassNode makeClassWithInvokeDynamic(Handle implHandle) {
        final ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/TestClass";
        cn.superName = "java/lang/Object";
        cn.methods = new ArrayList<>();

        final MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
        mn.instructions.add(new InvokeDynamicInsnNode(
                "run",
                "()Ljava/lang/Runnable;",
                LAMBDA_METAFACTORY,
                Type.getType("()V"),
                implHandle,
                Type.getType("()V")));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        return cn;
    }

    private static Handle getResultHandle(ClassNode cn) {
        final InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) cn.methods.get(0).instructions.get(0);
        return (Handle) dyn.bsmArgs[1];
    }

    @Test
    void invokeDynamicGL11HandleIsRedirected() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        final Handle result = getResultHandle(cn);
        assertEquals(GLSM, result.getOwner());
        assertEquals("glPopMatrix", result.getName());
        assertEquals("()V", result.getDesc());
        assertEquals(Opcodes.H_INVOKESTATIC, result.getTag());
    }

    @Test
    void invokeDynamicNamedClassHandleIsRedirected() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/EXTBlendFuncSeparate", "glBlendFuncSeparateEXT", "(IIII)V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        final Handle result = getResultHandle(cn);
        assertEquals(GLSM, result.getOwner());
        assertEquals("tryBlendFuncSeparate", result.getName());
        assertEquals("(IIII)V", result.getDesc());
    }

    @Test
    void invokeDynamicNonGLHandleIsNotTouched() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertFalse(changed);
        final Handle result = getResultHandle(cn);
        assertEquals("java/lang/System", result.getOwner());
        assertEquals("currentTimeMillis", result.getName());
    }

    @Test
    void invokeDynamicUnknownGLMethodIsNotTouched() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11", "glNonexistent", "()V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertFalse(changed);
        final Handle result = getResultHandle(cn);
        assertEquals("org/lwjgl/opengl/GL11", result.getOwner());
    }

    @Test
    void invokeDynamicGLCVariantIsRedirected() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11C", "glDrawArrays", "(III)V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        final Handle result = getResultHandle(cn);
        assertEquals(GLSM, result.getOwner());
        assertEquals("glDrawArrays", result.getName());
        assertEquals("(III)V", result.getDesc());
    }

    @Test
    void transformedClassProducesValidBytecode() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        assertDoesNotThrow(() -> {
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            assertTrue(bytes.length > 0);
        });
    }
}
