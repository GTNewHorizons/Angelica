package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class SMCCTTextureSimple implements IClassTransformer {

    private static final int logDetail = 0;

    @Override
    public byte[] transform(String par1, String par2, byte[] par3) {
        SMCLog.fine("transforming %s %s", par1, par2);
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
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            classname = name;
            // SMCLog.info(" class %s",name);
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // SMCLog.info("  method %s.%s%s = %s%s",classname,name,desc,nameM,descM);
            if (Names.iTextureObject_loadTexture.equalsNameDesc(name, desc)) {
                // SMCLog.finer("  patching method %s.%s%s = %s%s",classname,name,desc,nameM,descM);
                return new MVloadTexture(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVloadTexture extends MethodVisitor {
        public MVloadTexture(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.textureUtil_uploadTextureImageAllocate.equals(owner, name, desc)) {
                // mv.visitMethodInsn(opcode, owner, name, desc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(
                        GETFIELD,
                        Names.simpleTexture_textureLocation.clas,
                        Names.simpleTexture_textureLocation.name,
                        Names.simpleTexture_textureLocation.desc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        Names.simpleTexture_.clas,
                        "getMultiTexID",
                        "()Lshadersmodcore/client/MultiTexID;");
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "shadersmodcore/client/ShadersTex",
                        "loadSimpleTexture",
                        "(ILjava/awt/image/BufferedImage;ZZ" + Names.iResourceManager_.desc
                                + Names.resourceLocation_.desc + "Lshadersmodcore/client/MultiTexID;)I");
                SMCLog.finer("    loadSimpleTexture");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }
}
