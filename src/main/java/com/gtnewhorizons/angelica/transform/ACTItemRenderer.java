package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;

import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ACTItemRenderer implements IClassTransformer {

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

        Names.Meth renderItemIrt = new Names.Meth(
                Names.itemRenderer_,
                "renderItem",
                "(" + Names.entityLivingBase_.desc
                        + Names.itemStack_.desc
                        + "ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V");

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classname = name;
            // ALog.info(" class %s",name);
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // ALog.info(" method %s.%s%s",classname,name,desc);
            if (Names.itemRenderer_updateEquipped.equalsNameDesc(name, desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVupdate(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.itemRenderer_renderItem.equalsNameDesc(name, desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVrenderItem(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (renderItemIrt.equalsNameDesc(name, desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVrenderItem(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVupdate extends MethodVisitor {

        public MVupdate(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == PUTFIELD && Names.itemRenderer_itemToRender.equals(owner, name, desc)) {
                mv.visitInsn(DUP);
                mv.visitFieldInsn(
                        PUTSTATIC,
                        "com/gtnewhorizons/angelica/client/Shaders",
                        "itemToRender",
                        Names.itemStack_.desc);
            }
            mv.visitFieldInsn(opcode, owner, name, desc);
        }
    }

    private static class MVrenderItem extends MethodVisitor {

        public MVrenderItem(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int state = 0;

        @Override
        public void visitInsn(int opcode) {
            if (state == 1) {
                if (opcode == ICONST_0) {
                    ++state;
                    opcode = ICONST_1;
                }
            }
            mv.visitInsn(opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (state == 0) {
                if (opcode == SIPUSH && operand == GL11.GL_BLEND) {
                    ++state;
                }
            }
            mv.visitIntInsn(opcode, operand);
        }
    }
}
