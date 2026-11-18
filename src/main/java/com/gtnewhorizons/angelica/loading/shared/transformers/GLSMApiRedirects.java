package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.gtnewhorizons.angelica.glsm.redirect.LegacyRewriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class GLSMApiRedirects implements LegacyRewriter {

    public static final GLSMApiRedirects INSTANCE = new GLSMApiRedirects();

    private static final String GL_STATE = "com/gtnewhorizons/angelica/api/glstate/GLState";
    private static final String FOG_STATE_STACK = "com/gtnewhorizons/angelica/glsm/stacks/FogStateStack";
    private static final String VECTOR3D = "org/joml/Vector3d";

    private GLSMApiRedirects() {}

    @Override
    public String rewrite(InsnList instructions, MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC) return null;
        if (call.name.equals("getFogState") && call.desc.equals("()L" + FOG_STATE_STACK + ";")) {
            final AbstractInsnNode next = nextReal(call);
            if (!(next instanceof MethodInsnNode getter) || getter.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !getter.owner.equals(FOG_STATE_STACK) || !getter.name.equals("getFogColorBuffer")
                || !getter.desc.equals("()Ljava/nio/FloatBuffer;")) {
                return null;
            }
            final InsnList replacement = new InsnList();
            replacement.add(new InsnNode(Opcodes.ICONST_4));
            replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/nio/FloatBuffer", "allocate", "(I)Ljava/nio/FloatBuffer;", false));
            replacement.add(new InsnNode(Opcodes.DUP));
            replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_STATE, "getFogColor", "(Ljava/nio/FloatBuffer;)V", false));
            instructions.insertBefore(call, replacement);
            instructions.remove(call);
            instructions.remove(getter);
            return "com.gtnewhorizons.angelica.api.glstate.GLState.getFogColor(FloatBuffer)";
        }
        if (call.name.equals("getFogColor") && call.desc.equals("()L" + VECTOR3D + ";")) {
            final InsnList replacement = new InsnList();
            replacement.add(new TypeInsnNode(Opcodes.NEW, VECTOR3D));
            replacement.add(new InsnNode(Opcodes.DUP));
            replacement.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, VECTOR3D, "<init>", "()V", false));
            replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_STATE, "getFogColor", "(L" + VECTOR3D + ";)L" + VECTOR3D + ";", false));
            instructions.insertBefore(call, replacement);
            instructions.remove(call);
            return "com.gtnewhorizons.angelica.api.glstate.GLState.getFogColor(Vector3d)";
        }
        return null;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode node) {
        AbstractInsnNode next = node.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }
}
