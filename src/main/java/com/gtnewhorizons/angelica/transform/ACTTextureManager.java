package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.gtnewhorizons.angelica.ALog;

public class ACTTextureManager implements IClassTransformer {

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
            // ALog.info(" method %s.%s%s = %s%s",classname,name,desc,nameM,descM);
            if (Names.textureManager_bindTexture.equalsNameDesc(name, desc)) {
                // ALog.finer(" patching method %s.%s%s = %s%s",classname,name,desc,nameM,descM);
                return new MVbindTexture(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.textureManager_onResourceManagerReload.equalsNameDesc(name, desc)) {
                // ALog.finer(" patching method %s.%s%s = %s%s",classname,name,desc,nameM,descM);
                return new MVonReload(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVbindTexture extends MethodVisitor {

        public MVbindTexture(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.iTextureObject_getGlTextureId.equals(owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "bindTexture",
                        "(" + Names.iTextureObject_.desc + ")V");
                return;
            } else if (Names.textureUtil_bindTexture.equals(owner, name, desc)) {
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVonReload extends MethodVisitor {

        public MVonReload(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.iTextureObject_getGlTextureId.equals(owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "deleteMultiTex",
                        "(" + Names.iTextureObject_.desc + ")I");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }
}
