package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ACTPrjRedIlluRenderHalo implements IClassTransformer {

    @Override
    public byte[] transform(String par1, String par2, byte[] par3) {
        ALog.fine("transforming %s %s", par1, par2);
        ClassReader cr = new ClassReader(par3);
        ClassWriter cw = new ClassWriter(cr, 0);
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
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.classname = name;
            // SMCLog.info(" class %s",name);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // SMCLog.info("  %s%s",name,desc);
            if ("prepareRenderState".equals(name) && "()V".equals(desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVprepare(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            if ("restoreRenderState".equals(name) && "()V".equals(desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVrestore(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVprepare extends MethodVisitor {
        public MVprepare(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.equals("codechicken/lib/render/CCRenderState", "reset", "()V", owner, name, desc)) {
                ALog.info("   beginHalo");
                mv.visitMethodInsn(
                        INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginProjectRedHalo", "()V");
            }
            super.visitMethodInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
        }
    }

    private static class MVrestore extends MethodVisitor {
        public MVrestore(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            super.visitMethodInsn(opcode, owner, name, desc);
            if (Names.equals("org/lwjgl/opengl/GL11", "glDisable", "(I)V", owner, name, desc)) {
                ALog.info("   endHalo");
                mv.visitMethodInsn(
                        INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endProjectRedHalo", "()V");
            }
        }
    }
}
