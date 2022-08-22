package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;
import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class SMCCTBlock implements IClassTransformer {

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
			this.classname = name;
			//SMCLog.info(" class %s",name);
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions)
		{
			if (Names.block_getAoLight.equalsNameDesc(name, desc))
			{
				//SMCLog.info("  patching");
				return new MVgetAoLight(cv.visitMethod(access, name, desc, signature, exceptions));
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}

	}

	private static class MVgetAoLight extends MethodVisitor
	{
		public MVgetAoLight(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitLdcInsn(Object cst) {
			if (cst instanceof Float)
			{
				if (((Float)cst).floatValue() == 0.2f)
				{
					mv.visitFieldInsn(GETSTATIC, "shadersmodcore/client/Shaders", "blockAoLight", "F");
					SMCLog.info("   blockAoLight");
					return;
				}
			}
			mv.visitLdcInsn(cst);
		}

	}

}
