package shadersmodcore.transform;

import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.MethodNode;


import net.minecraft.launchwrapper.IClassTransformer;

public class SMCCTTextureDownload implements IClassTransformer {

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
		public FieldVisitor visitField(int access, String name, String desc,
				String signature, Object value) {
			//SMCLog.finest("  field %s %s %s %d", classname, name, desc, access);
			return super.visitField(access, name, desc, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) 
		{
			//String nameM = SMCRemap.remapper.mapMethodName(classname, name, desc);
			//SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
			if (name.equals("getMultiTexID")) {
				return null;
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}

		@Override
		public void visitEnd() {
			MethodVisitor mv;
			//getMultiTexID
			mv = cv.visitMethod(ACC_PUBLIC, "getMultiTexID", "()Lshadersmodcore/client/MultiTexID;", null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, Names.textureDownload_textureUploaded.clas, Names.textureDownload_textureUploaded.name, Names.textureDownload_textureUploaded.desc);
			Label l1 = new Label();
			mv.visitJumpInsn(IFNE, l1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, Names.textureDownload_.clas, Names.iTextureObject_getGlTextureId.name, Names.iTextureObject_getGlTextureId.desc);
			mv.visitInsn(POP);
			mv.visitLabel(l1);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, Names.abstractTexture_.clas, "getMultiTexID", "()Lshadersmodcore/client/MultiTexID;");
			mv.visitInsn(ARETURN);
			Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitLocalVariable("this", Names.textureDownload_.desc, null, l0, l3, 0);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
			//end
			cv.visitEnd();
		}
	}
}
