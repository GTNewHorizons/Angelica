package shadersmodcore.transform;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.objectweb.asm.Opcodes.*;

/** transformer for net.minecraft.client.renderer.texture.AbstractTexture */
public class SMCCTTextureAbstract implements IClassTransformer {

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
		boolean endFields = false;

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
		public FieldVisitor visitField(int access, String name, String desc,
				String signature, Object value) {
			if (name.equals("multiTex")) {
				return null;
			} else {
				access = access & (~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC;
			}
			return cv.visitField(access, name, desc, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) 
		{
			if (!endFields) {
				endFields = true;
				FieldVisitor fv;
				// multiTex
				fv = cv.visitField(ACC_PUBLIC, "multiTex", "Lshadersmodcore/client/MultiTexID;", null, null);
				fv.visitEnd();
			}
			//SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
			if (Names.abstractTexture_deleteGlTexture.equalsNameDesc(name, desc)) {
				//SMCLog.finer("  patching method %s.%s%s = %s%s",classname,name,desc,nameM,descM);
				return new MVdeleteGlTexture(
						cv.visitMethod(access, name, desc, signature, exceptions));
			} else if (name.equals("getMultiTexID") && desc.equals("()Lshadersmodcore/client/MultiTexID;")) {
				return null;
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}

		@Override
		public void visitEnd() {
			MethodVisitor mv;
			// getMultiTexID
			mv = cv.visitMethod(ACC_PUBLIC, "getMultiTexID", "()Lshadersmodcore/client/MultiTexID;", null, null);
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESTATIC, "shadersmodcore/client/ShadersTex", "getMultiTexID", "("+Names.abstractTexture_.desc+")Lshadersmodcore/client/MultiTexID;");
			mv.visitInsn(ARETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
			// end
			cv.visitEnd();
		}
	}
	
	private static class MVdeleteGlTexture extends MethodVisitor
	{
		public MVdeleteGlTexture(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitCode() {
			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESTATIC, "shadersmodcore/client/ShadersTex", "deleteTextures", "("+Names.abstractTexture_.desc+")V");
		}

	}
}
