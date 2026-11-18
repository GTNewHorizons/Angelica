package com.gtnewhorizons.angelica.loading;

import com.gtnewhorizons.angelica.glsm.testutil.DefiningClassLoader;
import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class GLSMApiRedirectsTest {

    private static final String CLASS_NAME = "com/example/OutsideMod";
    private static final String GLSM = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String FOG_STATE_STACK = "com/gtnewhorizons/angelica/glsm/stacks/FogStateStack";
    private static final String GL_STATE = "com/gtnewhorizons/angelica/api/glstate/GLState";
    private static final String VECTOR3D = "org/joml/Vector3d";
    private static final String GLSM_FOG = "com/gtnewhorizons/angelica/glsm/GLSMFog";

    private static ClassNode outsideClass() {
        final ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = CLASS_NAME;
        cn.superName = "java/lang/Object";

        final MethodNode fogBuffer = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fogRed", "()F", null, null);
        final LabelNode line = new LabelNode();
        fogBuffer.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GLSM, "getFogState", "()L" + FOG_STATE_STACK + ";", false));
        fogBuffer.instructions.add(line);
        fogBuffer.instructions.add(new LineNumberNode(7, line));
        fogBuffer.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FOG_STATE_STACK, "getFogColorBuffer", "()Ljava/nio/FloatBuffer;", false));
        fogBuffer.instructions.add(new InsnNode(Opcodes.ICONST_0));
        fogBuffer.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/nio/FloatBuffer", "get", "(I)F", false));
        fogBuffer.instructions.add(new InsnNode(Opcodes.FRETURN));
        cn.methods.add(fogBuffer);

        final MethodNode fogColor = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fogColorX", "()D", null, null);
        fogColor.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GLSM, "getFogColor", "()L" + VECTOR3D + ";", false));
        fogColor.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, VECTOR3D, "x", "D"));
        fogColor.instructions.add(new InsnNode(Opcodes.DRETURN));
        cn.methods.add(fogColor);

        final MethodNode fogMode = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fogMode", "()I", null, null);
        fogMode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GLSM, "getFogState", "()L" + FOG_STATE_STACK + ";", false));
        fogMode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FOG_STATE_STACK, "getFogMode", "()I", false));
        fogMode.instructions.add(new InsnNode(Opcodes.IRETURN));
        cn.methods.add(fogMode);

        final MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(ctor);
        return cn;
    }

    private static List<String> code(ClassNode cn, String name) {
        final MethodNode mn = cn.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
        final List<String> out = new ArrayList<>();
        for (AbstractInsnNode node : mn.instructions.toArray()) {
            if (node.getOpcode() < 0) continue;
            if (node instanceof MethodInsnNode m) {
                out.add(op(m.getOpcode(), m.owner + "." + m.name + m.desc));
            } else if (node instanceof FieldInsnNode f) {
                out.add(op(f.getOpcode(), f.owner + "." + f.name));
            } else if (node instanceof TypeInsnNode t) {
                out.add(op(t.getOpcode(), t.desc));
            } else {
                out.add(op(node.getOpcode()));
            }
        }
        return out;
    }

    private static String op(int opcode) {
        return String.valueOf(opcode);
    }

    private static String op(int opcode, String operand) {
        return opcode + " " + operand;
    }

    @Test
    void fogCallsBecomeGLStateCalls() {
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        outsideClass().accept(cw);
        final byte[] original = cw.toByteArray();
        final byte[] bytes = AngelicaRedirector.create().transform(CLASS_NAME.replace('/', '.'), original, null);
        assertNotSame(original, bytes, "redirector left the class untouched");
        DefiningClassLoader.define(CLASS_NAME.replace('/', '.'), bytes);

        final ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        assertEquals(List.of(
            op(Opcodes.ICONST_4),
            op(Opcodes.INVOKESTATIC, "java/nio/FloatBuffer.allocate(I)Ljava/nio/FloatBuffer;"),
            op(Opcodes.DUP),
            op(Opcodes.INVOKESTATIC, GL_STATE + ".getFogColor(Ljava/nio/FloatBuffer;)V"),
            op(Opcodes.ICONST_0),
            op(Opcodes.INVOKEVIRTUAL, "java/nio/FloatBuffer.get(I)F"),
            op(Opcodes.FRETURN)), code(cn, "fogRed"));
        assertEquals(List.of(
            op(Opcodes.NEW, VECTOR3D),
            op(Opcodes.DUP),
            op(Opcodes.INVOKESPECIAL, VECTOR3D + ".<init>()V"),
            op(Opcodes.INVOKESTATIC, GL_STATE + ".getFogColor(L" + VECTOR3D + ";)L" + VECTOR3D + ";"),
            op(Opcodes.GETFIELD, VECTOR3D + ".x"),
            op(Opcodes.DRETURN)), code(cn, "fogColorX"));
        assertEquals(List.of(
            op(Opcodes.INVOKESTATIC, GLSM_FOG + ".getFogState()L" + FOG_STATE_STACK + ";"),
            op(Opcodes.INVOKEVIRTUAL, FOG_STATE_STACK + ".getFogMode()I"),
            op(Opcodes.IRETURN)), code(cn, "fogMode"));
    }
}
