package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ACTOpenGlHelper implements IClassTransformer {

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
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            classname = name;
            // SMCLog.info(" class %s",name);
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        // boolean has_activeTexUnit = false;

        //		@Override
        //		public FieldVisitor visitField(int access, String name, String desc,
        //				String signature, Object value) {
        //			//if (name.equals("activeTexUnit")) {
        //			//	has_activeTexUnit = true;
        //			//}
        //			return cv.visitField(access, name, desc, signature, value);
        //		}

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // if (!has_activeTexUnit) {
            //	has_activeTexUnit = true;
            //	FieldVisitor fv = cv.visitField(ACC_PUBLIC + ACC_STATIC, "activeTexUnit", "I", null, null);
            //	fv.visitEnd();
            //	SMCLog.finest("    add field activeTexUnit");
            // }

            // SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
            if (Names.openGlHelper_setActiveTexture.equalsNameDesc(name, desc)) {
                // SMCLog.info("  patching");
                return new MVsetActiveTexture(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVsetActiveTexture extends MethodVisitor {
        public MVsetActiveTexture(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitCode() {
            mv.visitCode();
            mv.visitVarInsn(ILOAD, 0);
            mv.visitFieldInsn(PUTSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "activeTexUnit", "I");
            ALog.finest("    set activeTexUnit");
        }
    }
}
