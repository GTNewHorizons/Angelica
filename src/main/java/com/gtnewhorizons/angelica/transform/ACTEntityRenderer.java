package com.gtnewhorizons.angelica.transform;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;

/** Transformer for {@link EntityRenderer} */
public class ACTEntityRenderer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        AngelicaTweaker.LOGGER.debug("transforming {} {}", name, transformedName);
        ClassReader cr = new ClassReader(basicClass);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        CVTransform cv = new CVTransform(cw);
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static class CVTransform extends ClassVisitor {

        private String classname;

        public CVTransform(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classname = name;
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (Names.entityRenderer_renderHand.equalsNameDesc(name, desc)) {
                AngelicaTweaker.LOGGER.trace(" patching method {}.{}{}", classname, name, desc);
                return new MVrenderHand(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVrenderHand extends MethodVisitor {

        private Label label;

        public MVrenderHand(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
            label = new Label();
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            // Wraps the code from GL11.glPushMatrix() to GL11.glPopMatrix() in an if(!Shaders.isHandRendered) check
            if (Names.equals("org/lwjgl/opengl/GL11", "glPushMatrix", "()V", owner, name, desc)) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "isHandRendered", "Z");
                mv.visitJumpInsn(Opcodes.IFNE, label);
                mv.visitMethodInsn(opcode, owner, name, desc);
                return;
            } else if (Names.equals("org/lwjgl/opengl/GL11", "glPopMatrix", "()V", owner, name, desc)) {
                mv.visitMethodInsn(opcode, owner, name, desc);
                mv.visitLabel(label);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }
}
