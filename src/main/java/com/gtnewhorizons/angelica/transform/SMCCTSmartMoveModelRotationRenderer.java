package com.gtnewhorizons.angelica.transform;

import java.util.Iterator;
import java.util.ListIterator;

import org.lwjgl.opengl.GL12;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import net.minecraft.launchwrapper.IClassTransformer;

public class SMCCTSmartMoveModelRotationRenderer implements IClassTransformer {

	@Override
	public byte[] transform(String par1, String par2, byte[] par3) {
		SMCLog.fine("transforming %s %s",par1,par2);
		ClassReader cr = new ClassReader(par3);
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
		CVTransform cv = new CVTransform(cw);
		cr.accept(cv,0);
		return cw.toByteArray();
	}

	private static class CVTransform extends ClassVisitor
	{
		String classname;
		public CVTransform(ClassVisitor cv)
		{
			super(Opcodes.ASM4, cv);
		}

		@Override
		public void visit(int version, int access, String name,
				String signature, String superName, String[] interfaces) {
			classname = name;
			//SMCLog.info(" class %s",name);
			cv.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions)
		{
			//SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
			if ("resetDisplayList".equals(name) && "()V".equals(desc)) {
				return null;
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}

		@Override
		public void visitEnd() {
			MethodVisitor mv;
			{
				mv = cv.visitMethod(ACC_PUBLIC, "resetDisplayList", "()V", null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 0);
				mv.visitMethodInsn(INVOKESPECIAL, Names.modelRenderer_.clas, "resetDisplayList", "()V");
				mv.visitVarInsn(ALOAD, 0);
				mv.visitInsn(ICONST_0);
				//mv.visitFieldInsn(PUTFIELD, "net/smart/render/ModelRotationRenderer", "compiled", "Z");
				mv.visitFieldInsn(PUTFIELD, "net/smart/render/ModelRotationRenderer", Names.modelRenderer_compiled.name, "Z");
				mv.visitVarInsn(ALOAD, 0);
				mv.visitInsn(ICONST_0);
				//mv.visitFieldInsn(PUTFIELD, "net/smart/render/ModelRotationRenderer", "displayList", "I");
				mv.visitFieldInsn(PUTFIELD, "net/smart/render/ModelRotationRenderer", Names.modelRenderer_displayList.name, "I");
				mv.visitInsn(RETURN);
				mv.visitMaxs(2, 1);
				mv.visitEnd();
			}
			super.visitEnd();
		}

	}
}
