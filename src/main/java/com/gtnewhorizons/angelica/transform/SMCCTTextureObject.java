package com.gtnewhorizons.angelica.transform;

import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.MethodNode;


import net.minecraft.launchwrapper.IClassTransformer;

public class SMCCTTextureObject implements IClassTransformer {

	@Override
	public byte[] transform(String par1, String par2, byte[] par3) {
		SMCLog.fine("transforming %s %s",par1,par2);
		ClassReader cr = new ClassReader(par3);
		ClassWriter cw = new ClassWriter(cr, 0);
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
			if (name.equals("getMultiTexID") && desc.equals("()Lshadersmodcore/client/MultiTexID;")) {
				return null;
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}

		@Override
		public void visitEnd() {
			MethodVisitor mv;
			// getMultiTexID
			mv = cv.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "getMultiTexID", "()Lshadersmodcore/client/MultiTexID;", null, null);
			mv.visitEnd();
			// end
			cv.visitEnd();
		}

	}

}
