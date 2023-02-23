package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

public class ACTRenderBlocks implements IClassTransformer {

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
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // ALog.info(" method %s.%s%s = %s",classname,name,desc,remappedName);
            if (Names.renderBlocks_renderBlockByRenderType.equalsNameDesc(name, desc)) {
                // ALog.finer(" patching method %s.%s%s = %s",classname,name,desc,remappedName);
                return new MVrenBlkByRenType(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.renderBlocks_renderBlockFlowerPot.equalsNameDesc(name, desc)) {
                // ALog.finer(" patching method %s.%s%s = %s",classname,name,desc,remappedName);
                return new MVrenBlkFlowerPot(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.renderBlocks_renderStdBlockWithAOP.equalsNameDesc(name, desc)
                    || Names.renderBlocks_renderStdBlockWithAO.equalsNameDesc(name, desc)) {
                        // ALog.finer(" patching method %s.%s%s = %s",classname,name,desc,remappedName);
                        return new MVrenBlkWithAO(
                                access,
                                name,
                                desc,
                                signature,
                                exceptions,
                                cv.visitMethod(access, name, desc, signature, exceptions));
                    } else
                if (Names.renderBlocks_renderStdBlockWithCM.equalsNameDesc(name, desc)
                        || Names.renderBlocks_renderBlockCactusImpl.equalsNameDesc(name, desc)
                        || Names.renderBlocks_renderBlockBed.equalsNameDesc(name, desc)
                        || Names.renderBlocks_renderBlockFluids.equalsNameDesc(name, desc)
                        || Names.renderBlocks_renderBlockDoor.equalsNameDesc(name, desc)
                        || Names.renderBlocks_renderBlockSandFalling.equalsNameDesc(name, desc)) {
                            // ALog.finer(" patching method %s.%s%s = %s",classname,name,desc,remappedName);
                            return new MVrenBlkFVar(cv.visitMethod(access, name, desc, signature, exceptions));
                        } else
                    if (Names.renderBlocks_renderPistonExtension.equalsNameDesc(name, desc)) {
                        // ALog.finer(" patching method %s.%s%s = %s",classname,name,desc,remappedName);
                        return new MVrenBlkPistonExt(cv.visitMethod(access, name, desc, signature, exceptions));
                    }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVrenBlkByRenType extends MethodVisitor {

        public MVrenBlkByRenType(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int nPatch = 0;

        @Override
        public void visitCode() {
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitVarInsn(ILOAD, 3);
            mv.visitVarInsn(ILOAD, 4);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "com/gtnewhorizons/angelica/client/Shaders",
                    "pushEntity",
                    "(" + Names.renderBlocks_.desc + Names.block_.desc + "III)V");
            // ALog.info(" pushEntity");
            ++nPatch;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.IRETURN) {
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "popEntity", "()V");
                // ALog.info(" popEntity");
                ++nPatch;
            }
            mv.visitInsn(opcode);
        }

        @Override
        public void visitEnd() {
            mv.visitEnd();
            // ALog.info(" %d", nPatch);
        }
    }

    private static class MVrenBlkFlowerPot extends MethodVisitor {

        public MVrenBlkFlowerPot(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int nPatch = 0;
        int state = 0;

        @Override
        public void visitVarInsn(int opcode, int var) {
            super.visitVarInsn(opcode, var);
            if (state == 1) {
                ++state;
                mv.visitVarInsn(ALOAD, var);
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/Shaders",
                        "pushEntity",
                        "(" + Names.block_.desc + ")V");
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            super.visitMethodInsn(opcode, owner, name, desc);
            switch (state) {
                case 0:
                    if (Names.block_getBlockFromItem.equals(owner, name, desc)) {
                        ++state;
                    }
                    break;
                case 2:
                    if (Names.tessellator_addTranslation.equals(owner, name, desc)) {
                        ++state;
                    }
                    break;
                case 3:
                    if (Names.tessellator_addTranslation.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "popEntity",
                                "()V");
                    }
                    break;
            }
        }

        @Override
        public void visitCode() {
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitVarInsn(ILOAD, 3);
            mv.visitVarInsn(ILOAD, 4);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "com/gtnewhorizons/angelica/client/Shaders",
                    "pushEntity",
                    "(" + Names.renderBlocks_.desc + Names.block_.desc + "III)V");
            // ALog.info(" pushEntity");
            ++nPatch;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.IRETURN) {
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "popEntity", "()V");
                // ALog.info(" popEntity");
                ++nPatch;
            }
            mv.visitInsn(opcode);
        }

        @Override
        public void visitEnd() {
            mv.visitEnd();
            // ALog.info(" %d", nPatch);
        }
    }

    static final String[] fieldsBlockLightLevel = { null, "blockLightLevel05", "blockLightLevel06",
            "blockLightLevel08" };

    private static class MVrenBlkWithAO extends MethodVisitor {

        MethodVisitor mv1;
        MethodNode mn;
        int nPatch = 0;

        public MVrenBlkWithAO(final int access, final String name, final String desc, final String signature,
                final String[] exceptions, MethodVisitor mv) {
            super(Opcodes.ASM4);
            super.mv = this.mn = new MethodNode(access, name, desc, signature, exceptions);
            this.mv1 = mv;
        }

        @Override
        public void visitEnd() {
            mn.visitEnd();
            mn.accept(mv1);
            // ALog.info(" %d", nPatch);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            int match1 = 0;
            if (cst instanceof Float) {
                float fcst = ((Float) cst).floatValue();
                if (fcst == 0.5f) match1 = 1;
                else if (fcst == 0.6f) match1 = 2;
                else if (fcst == 0.8f) match1 = 3;
            }
            if (match1 != 0) {
                int match2 = 0;
                InsnList insns = mn.instructions;
                AbstractInsnNode insn = insns.getLast();
                if (insn != null && insn.getOpcode() == Opcodes.FLOAD) {
                    insn = insn.getPrevious();
                    if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                        insn = insn.getPrevious();
                        if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                            insn = insn.getPrevious();
                            if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                                insn = insn.getPrevious();
                                if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                                    match2 = match1;
                                }
                            }
                        }
                    }
                }
                if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                    insn = insn.getPrevious();
                    if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                        insn = insn.getPrevious();
                        if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                            insn = insn.getPrevious();
                            if (insn != null && insn.getOpcode() == Opcodes.ALOAD) {
                                match2 = match1;
                            }
                        }
                    }
                }
                if (match2 != 0) {
                    String fieldName = fieldsBlockLightLevel[match2];
                    mn.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", fieldName, "F");
                    // ALog.info(" %s", fieldName);
                    ++nPatch;
                    return;
                }
            }
            mn.visitLdcInsn(cst);
        }
    }

    private static class MVrenBlkFVar extends MethodVisitor {

        int nPatch = 0;

        public MVrenBlkFVar(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int state = 0;

        @Override
        public void visitLdcInsn(Object cst) {
            int match1 = 0;
            if (cst instanceof Float) {
                float fcst = ((Float) cst).floatValue();
                if (fcst == 0.5f) match1 = 1;
                else if (fcst == 0.6f) match1 = 2;
                else if (fcst == 0.8f) match1 = 3;
            }
            if (match1 != 0 && state < 3) {
                ++state;
                String fieldName = fieldsBlockLightLevel[match1];
                mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", fieldName, "F");
                // ALog.info(" %s", fieldName);
                ++nPatch;
                return;
            }
            mv.visitLdcInsn(cst);
        }

        @Override
        public void visitEnd() {
            mv.visitEnd();
            // ALog.info(" %d", nPatch);
        }
    }

    private static class MVrenBlkPistonExt extends MethodVisitor {

        int nPatch = 0;

        public MVrenBlkPistonExt(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int state = 0;

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            state = 1;
            mv.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            int match1 = 0;
            if (cst instanceof Float) {
                float fcst = ((Float) cst).floatValue();
                if (fcst == 0.5f) match1 = 1;
                else if (fcst == 0.6f) match1 = 2;
                else if (fcst == 0.8f) match1 = 3;
            }
            if (match1 != 0 && state == 1) {
                String fieldName = fieldsBlockLightLevel[match1];
                mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", fieldName, "F");
                // ALog.info(" %s", fieldName);
                ++nPatch;
                return;
            }
            mv.visitLdcInsn(cst);
        }

        @Override
        public void visitEnd() {
            mv.visitEnd();
            // ALog.info(" %d", nPatch);
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
