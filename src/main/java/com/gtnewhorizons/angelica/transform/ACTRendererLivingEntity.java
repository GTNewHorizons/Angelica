package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.SIPUSH;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.launchwrapper.IClassTransformer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;

/** Transformer for {@link RendererLivingEntity} */
public class ACTRendererLivingEntity implements IClassTransformer {

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

        private CVTransform(ClassVisitor cv) {
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
            if (Names.rendererLivingE_doRender.equalsNameDesc(name, desc)) {
                AngelicaTweaker.LOGGER.trace(" patching method {}.{}{}", classname, name, desc);
                return new MVdoRenderLiving(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.rendererLivingE_renderLabel.equalsNameDesc(name, desc)) {
                ALog.finer(" patching method %s.%s%s", classname, name, desc);
                return new MVrenderLivingLabel(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    /**
     * Wraps everything in
     * {@link RendererLivingEntity#doRender(net.minecraft.entity.EntityLivingBase, double, double, double, float, float)}
     * between {@code this.renderEquippedItems(p_76986_1_, p_76986_9_);} and
     * {@code GL11.glDisable(GL12.GL_RESCALE_NORMAL);} in an {@code if (!Shaders.useEntityHurtFlash)}-block
     */
    private static class MVdoRenderLiving extends MethodVisitor {

        private MVdoRenderLiving(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        /** end of vanilla hurt rendering */
        private Label labelEndVH = null;

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof Integer integer) {
                if (integer.intValue() == GL12.GL_RESCALE_NORMAL) {
                    if (labelEndVH != null) {
                        mv.visitLabel(labelEndVH);
                        labelEndVH = null;
                    }
                }
            }
            mv.visitLdcInsn(cst);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (opcode == INVOKEVIRTUAL && Names.rendererLivingE_renderEquippedItems.equals(owner, name, desc)) {
                mv.visitMethodInsn(opcode, owner, name, desc);
                mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "useEntityHurtFlash", "Z");
                labelEndVH = new Label();
                mv.visitJumpInsn(IFNE, labelEndVH);
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVrenderLivingLabel extends MethodVisitor {

        private MVrenderLivingLabel(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        private int pushedInt = 0;

        @Override
        public void visitIntInsn(int opcode, int operand) {
            mv.visitIntInsn(opcode, operand);
            if (opcode == SIPUSH && operand == GL11.GL_TEXTURE_2D) {
                pushedInt = GL11.GL_TEXTURE_2D;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (pushedInt == GL11.GL_TEXTURE_2D) {
                pushedInt = 0;
                if (opcode == INVOKESTATIC && owner.equals("org/lwjgl/opengl/GL11") && desc.equals("(I)V")) {
                    if (name.equals("glDisable")) {
                        owner = "com/gtnewhorizons/angelica/client/Shaders";
                        name = "sglDisableT2D";
                    } else if (name.equals("glEnable")) {
                        owner = "com/gtnewhorizons/angelica/client/Shaders";
                        name = "sglEnableT2D";
                    }
                }
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }
}
