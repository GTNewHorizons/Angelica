package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class SMCCTTextureLayered implements IClassTransformer {

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
            // String descM = SMCNames.remapper.mapMethodDesc(desc);
            // SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
            if (Names.iTextureObject_loadTexture.equalsNameDesc(name, desc)) {
                // SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
                return new MVloadTexture(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVloadTexture extends MethodVisitor {
        // protected MethodVisitor mv;
        public MVloadTexture(MethodVisitor mv) {
            super(Opcodes.ASM4);
            // replace method body
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(
                    GETFIELD,
                    Names.layeredTexture_layeredTextureNames.clas,
                    Names.layeredTexture_layeredTextureNames.name,
                    Names.layeredTexture_layeredTextureNames.desc);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "shadersmodcore/client/ShadersTex",
                    "loadLayeredTexture",
                    "(" + Names.layeredTexture_.desc + Names.iResourceManager_.desc + "Ljava/util/List;)V");
            mv.visitInsn(RETURN);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("this", Names.layeredTexture_.desc, null, l0, l2, 0);
            mv.visitLocalVariable("manager", Names.iResourceManager_.desc, null, l0, l2, 1);
            mv.visitMaxs(3, 2);
            mv.visitEnd();
        }
    }
}
