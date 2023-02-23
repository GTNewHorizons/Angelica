package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;

import org.lwjgl.opengl.GL12;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ACTRendererLivingEntity implements IClassTransformer {

    @Override
    public byte[] transform(String par1, String par2, byte[] par3) {
        ALog.fine("transforming %s %s", par1, par2);
        ClassReader cr = new ClassReader(par3);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        CVTransform cv = new CVTransform(cw);
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static class CVTransform extends ClassVisitor {

        String classname;

        public CVTransform(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classname = name;
            // ALog.info(" class %s",name);
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            if (Names.rendererLivingE_mainModel.name.equals(name)
                    || Names.rendererLivingE_renderPassModel.name.equals(name)) {
                access = access & ~(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED) | ACC_PUBLIC;
            }
            return cv.visitField(access, name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // ALog.info(" method %s.%s%s = %s",classname,name,desc,remappedName);
            if (Names.rendererLivingE_doRender.equalsNameDesc(name, desc)) {
                // ALog.finer(" patching method %s.%s%s = %s",classname,name,desc,nameM);
                return new MVdoRenderLiving(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.rendererLivingE_renderLabel.equalsNameDesc(name, desc)) {
                // ALog.finer(" patching method %s.%s%s = %s",classname,name,desc,nameM);
                return new MVrenderLivingLabel(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVdoRenderLiving extends MethodVisitor {

        public MVdoRenderLiving(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitCode() {
            // ALog.info(" insert code");
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "useEntityHurtFlash", "Z");
            Label label1 = new Label();
            mv.visitJumpInsn(IFEQ, label1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(
                    GETFIELD,
                    Names.entityLivingBase_hurtTime.clas,
                    Names.entityLivingBase_hurtTime.name,
                    Names.entityLivingBase_hurtTime.desc);
            Label label2 = new Label();
            mv.visitJumpInsn(IFGT, label2);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(
                    GETFIELD,
                    Names.entityLivingBase_deathTime.clas,
                    Names.entityLivingBase_deathTime.name,
                    Names.entityLivingBase_deathTime.desc);
            Label label3 = new Label();
            mv.visitJumpInsn(IFLE, label3);
            mv.visitLabel(label2);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitIntInsn(BIPUSH, 102);
            Label label4 = new Label();
            mv.visitJumpInsn(GOTO, label4);
            mv.visitLabel(label3);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitInsn(ICONST_0);
            mv.visitLabel(label4);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { Opcodes.INTEGER });
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(FLOAD, 9);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    Names.entity_getBrightness.clas,
                    Names.entity_getBrightness.name,
                    Names.entity_getBrightness.desc);
            mv.visitVarInsn(FLOAD, 9);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    Names.rendererLivingE_getColorMultiplier.clas,
                    Names.rendererLivingE_getColorMultiplier.name,
                    Names.rendererLivingE_getColorMultiplier.desc);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "com/gtnewhorizons/angelica/client/Shaders",
                    "setEntityHurtFlash",
                    "(II)V");
            mv.visitLabel(label1);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            // ALog.info(" end insert");
        }

        /** last SIPUSH operand */
        private int lastInt = 0;
        /** state */
        private int state = 0;

        private static final int stateEnd = 7;
        /** end of vanilla hurt rendering */
        Label labelEndVH = null;

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == SIPUSH) {
                lastInt = operand;
            }
            mv.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof Integer) {
                int icst = ((Integer) cst).intValue();
                if (icst == GL12.GL_RESCALE_NORMAL) {
                    // ALog.info(" rescale_normal");
                    if (labelEndVH != null) {
                        // ALog.info(" jump target");
                        mv.visitLabel(labelEndVH);
                        labelEndVH = null;
                    }
                }
            }
            mv.visitLdcInsn(cst);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            // ALog.info(" %s.%s%s",ownerM,nameM,descM);
            if (opcode == INVOKEVIRTUAL) {
                if (Names.rendererLivingE_renderEquippedItems.equals(owner, name, desc)) {
                    // ALog.info(" renderEquippedItems");
                    mv.visitMethodInsn(
                            INVOKESTATIC,
                            "com/gtnewhorizons/angelica/client/Shaders",
                            "resetEntityHurtFlash",
                            "()V");
                    mv.visitMethodInsn(opcode, owner, name, desc);
                    mv.visitFieldInsn(
                            GETSTATIC,
                            "com/gtnewhorizons/angelica/client/Shaders",
                            "useEntityHurtFlash",
                            "Z");
                    labelEndVH = new Label();
                    mv.visitJumpInsn(IFNE, labelEndVH);
                    state = 1;
                    return;
                }
            }
            //
            mv.visitMethodInsn(opcode, owner, name, desc);
            //
            if (opcode == INVOKESTATIC) {
                if (Names.equals("org/lwjgl/opengl/GL11", "glDepthFunc", "(I)V", owner, name, desc)) {
                    // ALog.info(" glDepthFunc");
                    if (state == 3) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "beginLivingDamage",
                                "()V");
                        ++state;
                    } else if (state == 4) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "endLivingDamage",
                                "()V");
                        ++state;
                    }
                } else if (Names.openGlHelper_setActiveTexture.equals(owner, name, desc)) {
                    // ALog.info(" setActiveTexture");
                    if (state == 1) {
                        ++state;
                    } else if (state == 2) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "disableLightmap",
                                "()V");
                        ++state;
                    } else if (state == 5) {
                        ++state;
                    } else if (state == 6) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "enableLightmap",
                                "()V");
                        ++state;
                    }
                }
            }
        }

        @Override
        public void visitEnd() {
            mv.visitEnd();
            if (state != stateEnd) {
                ALog.severe("state %d expected %d", state, stateEnd);
            }
        }
    }

    private static class MVrenderLivingLabel extends MethodVisitor {

        public MVrenderLivingLabel(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int pushedInt = 0;

        @Override
        public void visitIntInsn(int opcode, int operand) {
            mv.visitIntInsn(opcode, operand);
            if (opcode == SIPUSH && operand == 3553) pushedInt = 3553;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (pushedInt == 3553) {
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

    // private static class MVNTransform extends MethodVisitor
    // {
    // protected MethodVisitor mv;
    // protected MethodNode mn;
    // public MVNTransform(MethodNode mn, MethodVisitor mv) {
    // super(Opcodes.ASM4);
    // super.mv = this.mn = mn;
    // this.mv = mv;
    // }
    //
    // @Override
    // public void visitEnd() {
    // super.visitEnd();
    // Iterator<AbstractInsnNode> it = mn.instructions.iterator();
    // while (it.hasNext())
    // {
    // AbstractInsnNode node = it.next();
    // System.out.println(node.toString());
    // }
    // // send to target
    // mn.accept(mv);
    // }
    // }

}
