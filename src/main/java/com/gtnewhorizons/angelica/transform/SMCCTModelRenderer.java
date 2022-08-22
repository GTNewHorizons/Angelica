package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class SMCCTModelRenderer implements IClassTransformer {

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

        //		@Override
        //		public FieldVisitor visitField(int access, String name, String desc,
        //				String signature, Object value) {
        //			if (Names.modelRenderer_compiled.name.equals(name) ||
        //				Names.modelRenderer_displayList.name.equals(name)) {
        //				access = access & ~(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED) | ACC_PUBLIC;
        //			}
        //			return cv.visitField(access, name, desc, signature, value);
        //		}

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
            //			if (Names.modelRenderer_render.equalsNameDesc(name, desc))
            //			{
            //				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,remappedName);
            //				return new MVrender(
            //						cv.visitMethod(access, name, desc, signature, exceptions));
            //			}
            //			else
            //			if (Names.modelRenderer_renderWithRotation.equalsNameDesc(name, desc))
            //			{
            //				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,remappedName);
            //				return new MVrender(
            //						cv.visitMethod(access, name, desc, signature, exceptions));
            //			}
            //			else
            if ("resetDisplayList".equals(name) && "()V".equals(desc)
                    || "getCompiled".equals(name) && "()Z".equals(desc)
                    || "getDisplayList".equals(name) && "()I".equals(desc)) {
                return null;
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            MethodVisitor mv;
            {
                mv = cv.visitMethod(ACC_PUBLIC, "getCompiled", "()Z", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(
                        GETFIELD,
                        Names.modelRenderer_compiled.clas,
                        Names.modelRenderer_compiled.name,
                        Names.modelRenderer_compiled.desc);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cv.visitMethod(ACC_PUBLIC, "getDisplayList", "()I", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(
                        GETFIELD,
                        Names.modelRenderer_displayList.clas,
                        Names.modelRenderer_displayList.name,
                        Names.modelRenderer_displayList.desc);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = cv.visitMethod(ACC_PUBLIC, "resetDisplayList", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(
                        GETFIELD,
                        Names.modelRenderer_compiled.clas,
                        Names.modelRenderer_compiled.name,
                        Names.modelRenderer_compiled.desc);
                Label l1 = new Label();
                mv.visitJumpInsn(IFEQ, l1);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(
                        GETFIELD,
                        Names.modelRenderer_displayList.clas,
                        Names.modelRenderer_displayList.name,
                        Names.modelRenderer_displayList.desc);
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        Names.glAllocation_deleteDisplayLists.clas,
                        Names.glAllocation_deleteDisplayLists.name,
                        Names.glAllocation_deleteDisplayLists.desc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(ICONST_0);
                mv.visitFieldInsn(
                        PUTFIELD,
                        Names.modelRenderer_displayList.clas,
                        Names.modelRenderer_displayList.name,
                        Names.modelRenderer_displayList.desc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(ICONST_0);
                mv.visitFieldInsn(
                        PUTFIELD,
                        Names.modelRenderer_compiled.clas,
                        Names.modelRenderer_compiled.name,
                        Names.modelRenderer_compiled.desc);
                mv.visitLabel(l1);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            super.visitEnd();
        }
    }
}
