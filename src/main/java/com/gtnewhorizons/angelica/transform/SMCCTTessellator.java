package com.gtnewhorizons.angelica.transform;

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

/** transformer for net.minecraft.client.renderer.Tessellator */
public class SMCCTTessellator implements IClassTransformer {

	@Override
	public byte[] transform(String par1, String par2, byte[] par3) {
		SMCLog.fine("transforming %s %s",par1,par2);
		ClassReader cr = new ClassReader(par3);
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
		CVTransform cv = new CVTransform(cw);
		cr.accept(cv,0);
		return cw.toByteArray();
	}

	private static boolean inputHasStaticBuffer = false;

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
			//SMCLog.fine("%x %s %s %s",access,desc,name,nameM);
			if (	name.equals("vertexPos") ||
					name.equals("normalX") ||
					name.equals("normalY") ||
					name.equals("normalZ") ||
					name.equals("midTextureU") ||
					name.equals("midTextureV")
					) {
				return null;
			} else if (((access & ACC_STATIC) != 0) && (
					Names.tessellator_byteBuffer.name.equals(name) ||
					Names.tessellator_intBuffer.name.equals(name) ||
					Names.tessellator_floatBuffer.name.equals(name) ||
					Names.tessellator_shortBuffer.name.equals(name) ||
					Names.tessellator_vertexCount.name.equals(name)
					//nameM.equals(Names.Tessellator_useVBO) ||
					//nameM.equals(Names.Tessellator_vertexBuffers)
					)) {
				inputHasStaticBuffer = true;
				//SMCLog.finest(" input has static buffer");
				access = access & (~ACC_STATIC & ~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC;
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
				fv = cv.visitField(ACC_PUBLIC, "vertexPos", "[F", null, null);
				fv.visitEnd();
				fv = cv.visitField(ACC_PUBLIC, "normalX", "F", null, null);
				fv.visitEnd();
				fv = cv.visitField(ACC_PUBLIC, "normalY", "F", null, null);
				fv.visitEnd();
				fv = cv.visitField(ACC_PUBLIC, "normalZ", "F", null, null);
				fv.visitEnd();
				fv = cv.visitField(ACC_PUBLIC, "midTextureU", "F", null, null);
				fv.visitEnd();
				fv = cv.visitField(ACC_PUBLIC, "midTextureV", "F", null, null);
				fv.visitEnd();
			}
			//SMCLog.fine("  method %s.%s%s = %s",classname,name,desc,nameM);
			if (name.equals("<clinit>"))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVclinit(cv.visitMethod(access, name, desc, signature, exceptions));
			} else
			if (name.equals("<init>") && desc.equals("()V"))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVinit(cv.visitMethod(access, name, desc, signature, exceptions));
			} else
			if (name.equals("<init>") && desc.equals("(I)V"))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVinitI(
						cv.visitMethod(access, name, desc, signature, exceptions)
						);
			} else
			if (Names.tessellator_draw.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVdraw(cv.visitMethod(access, name, desc, signature, exceptions));
			} else
			if (Names.tessellator_reset.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				access = access & ~(ACC_PRIVATE|ACC_PROTECTED) | ACC_PUBLIC;
				return new MVreset(cv.visitMethod(access, name, desc, signature, exceptions));
			} else
			if (Names.tessellator_addVertex.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVaddVertex(cv.visitMethod(access, name, desc, signature, exceptions));
			} else
			if (Names.tessellator_setNormal.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVsetNormal(
						cv.visitMethod(access, name, desc, signature, exceptions));
			} else
			if (Names.tessellator_sortQuad.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVsortQuad(
						cv.visitMethod(access, name, desc, signature, exceptions));
			} else
			{
				access = access & ~(ACC_PRIVATE|ACC_PROTECTED) | ACC_PUBLIC;
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}
	}

	/*private static class MVclinit extends MethodVisitor
	{
		public MVclinit(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}
	}

	private static class MVinit extends MethodVisitor
	{
		public MVinit(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitInsn(int opcode) {
			if (opcode == RETURN) {
				mv.visitVarInsn(ALOAD, 0);
				mv.visitIntInsn(BIPUSH, 16);
				mv.visitIntInsn(NEWARRAY, T_FLOAT);
				mv.visitFieldInsn(PUTFIELD, SMCNames.Tessellator_, "vertexPos", "[F");
			}
			mv.visitInsn(opcode);
		}
	}*/

	private static class MVclinit extends MethodVisitor
	{
		public MVclinit(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name,
				String desc) {
			//SMCLog.finest("     F %d %s.%s %s", opcode, ownerM, nameM, descM);
			if (opcode==PUTSTATIC && (
					Names.tessellator_byteBuffer.equals(owner, name) ||
					Names.tessellator_intBuffer.equals(owner, name) ||
					Names.tessellator_floatBuffer.equals(owner, name) ||
					Names.tessellator_shortBuffer.equals(owner, name) ||
					Names.tessellator_vertexCount.equals(owner, name)
					//nameM.equals(Names.Tessellator_useVBO)||
					//nameM.equals(Names.Tessellator_vertexBuffers)
					))
			{
				mv.visitInsn(POP);
				return;
			} else
			if (opcode==GETSTATIC && (
					Names.tessellator_byteBuffer.equals(owner, name)
					//nameM.equals(Names.Tessellator_vertexBuffers)
					))
			{
				mv.visitInsn(ACONST_NULL);
				return;
			//} else
			//if (opcode==GETSTATIC && (
			//		nameM.equals(Names.Tessellator_useVBO)
			//		))
			//{
			//	mv.visitInsn(ICONST_0);
			//	return;
			}
			mv.visitFieldInsn(opcode, owner, name, desc);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name,
				String desc) {
			//SMCLog.finest("     M %d %s.%s %s", opcode, ownerM, nameM, descM);
			if (Names.glAllocation_createDirectByteBuffer.equals(owner, name, desc)) {
				mv.visitInsn(POP);
				mv.visitInsn(ACONST_NULL);
				return;
			} else if (Names.glAllocation_createDirectIntBuffer.equals(owner, name, desc)) {
				mv.visitInsn(POP);
				mv.visitInsn(ACONST_NULL);
				return;
			} else if (Names.equals("java/nio/ByteBuffer","asIntBuffer","()Ljava/nio/IntBuffer;",owner,name,desc) ) {
				mv.visitInsn(POP);
				mv.visitInsn(ACONST_NULL);
				return;
			} else if (Names.equals("java/nio/ByteBuffer","asFloatBuffer","()Ljava/nio/FloatBuffer;",owner,name,desc) ) {
				mv.visitInsn(POP);
				mv.visitInsn(ACONST_NULL);
				return;
			} else if (Names.equals("java/nio/ByteBuffer","asShortBuffer","()Ljava/nio/ShortBuffer;",owner,name,desc) ) {
				mv.visitInsn(POP);
				mv.visitInsn(ACONST_NULL);
				return;
			}
			mv.visitMethodInsn(opcode, owner, name, desc);
		}
	}

	private static class MVinit extends MethodVisitor
	{
		public MVinit(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String desc) {
			if (opcode==INVOKESPECIAL
					&& Names.equals("java/lang/Object","<init>","()V",owner, name, desc) )
			{
				mv.visitLdcInsn(new Integer(65536));
				mv.visitMethodInsn(INVOKESPECIAL, Names.tessellator_.clas, "<init>", "(I)V");
				return;
			}
			mv.visitMethodInsn(opcode, owner, name, desc);
		}
	}

	private static class MVinitI extends MethodVisitor
	{
		public MVinitI(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitInsn(int opcode) {
			if (opcode == RETURN) {
				if (inputHasStaticBuffer) {
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ILOAD, 1);
					mv.visitInsn(ICONST_4);
					mv.visitInsn(IMUL);
					mv.visitMethodInsn(INVOKESTATIC, Names.glAllocation_createDirectByteBuffer.clas, Names.glAllocation_createDirectByteBuffer.name, Names.glAllocation_createDirectByteBuffer.desc);
					mv.visitFieldInsn(PUTFIELD, Names.tessellator_byteBuffer.clas, Names.tessellator_byteBuffer.name, Names.tessellator_byteBuffer.desc);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitFieldInsn(GETFIELD, Names.tessellator_byteBuffer.clas, Names.tessellator_byteBuffer.name, Names.tessellator_byteBuffer.desc);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "asIntBuffer", "()Ljava/nio/IntBuffer;");
					mv.visitFieldInsn(PUTFIELD, Names.tessellator_intBuffer.clas, Names.tessellator_intBuffer.name, Names.tessellator_intBuffer.desc);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitFieldInsn(GETFIELD, Names.tessellator_byteBuffer.clas, Names.tessellator_byteBuffer.name, Names.tessellator_byteBuffer.desc);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "asFloatBuffer", "()Ljava/nio/FloatBuffer;");
					mv.visitFieldInsn(PUTFIELD, Names.tessellator_floatBuffer.clas, Names.tessellator_floatBuffer.name, Names.tessellator_floatBuffer.desc);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitFieldInsn(GETFIELD, Names.tessellator_byteBuffer.clas, Names.tessellator_byteBuffer.name, Names.tessellator_byteBuffer.desc);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "asShortBuffer", "()Ljava/nio/ShortBuffer;");
					mv.visitFieldInsn(PUTFIELD, Names.tessellator_shortBuffer.clas, Names.tessellator_shortBuffer.name, Names.tessellator_shortBuffer.desc);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ILOAD, 1);
					mv.visitIntInsn(NEWARRAY, T_INT);
					mv.visitFieldInsn(PUTFIELD, Names.tessellator_rawBuffer.clas, Names.tessellator_rawBuffer.name, Names.tessellator_rawBuffer.desc);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitInsn(ICONST_0);
					mv.visitFieldInsn(PUTFIELD, Names.tessellator_vertexCount.clas, Names.tessellator_vertexCount.name, Names.tessellator_vertexCount.desc);
					//mv.visitVarInsn(ALOAD, 0);
					//mv.visitFieldInsn(GETSTATIC, Names.Tessellator_, Names.Tessellator_tryVBO, "Z");
					//Label l28 = new Label();
					//mv.visitJumpInsn(IFEQ, l28);
					//mv.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/GLContext", "getCapabilities", "()Lorg/lwjgl/opengl/ContextCapabilities;");
					//mv.visitFieldInsn(GETFIELD, "org/lwjgl/opengl/ContextCapabilities", "GL_ARB_vertex_buffer_object", "Z");
					//mv.visitJumpInsn(IFEQ, l28);
					//mv.visitInsn(ICONST_1);
					//Label l29 = new Label();
					//mv.visitJumpInsn(GOTO, l29);
					//mv.visitLabel(l28);
					//mv.visitFrame(Opcodes.F_FULL, 2, new Object[] {Names.Tessellator_, Opcodes.INTEGER}, 1, new Object[] {Names.Tessellator_});
					//mv.visitInsn(ICONST_0);
					//mv.visitLabel(l29);
					//mv.visitFrame(Opcodes.F_FULL, 2, new Object[] {Names.Tessellator_, Opcodes.INTEGER}, 2, new Object[] {Names.Tessellator_, Opcodes.INTEGER});
					//mv.visitFieldInsn(PUTFIELD, Names.Tessellator_, Names.Tessellator_useVBO, "Z");
					//mv.visitVarInsn(ALOAD, 0);
					//mv.visitFieldInsn(GETFIELD, Names.Tessellator_, Names.Tessellator_useVBO, "Z");
					//Label l31 = new Label();
					//mv.visitJumpInsn(IFEQ, l31);
					//mv.visitVarInsn(ALOAD, 0);
					//mv.visitVarInsn(ALOAD, 0);
					//mv.visitFieldInsn(GETFIELD, Names.Tessellator_, Names.Tessellator_vboCount, "I");
					//mv.visitMethodInsn(INVOKESTATIC, Names.GLAllocation_, Names.GLAllocation_createDirectIntBuffer, Names.GLAllocation_createDirectIntBuffer_desc);
					//mv.visitFieldInsn(PUTFIELD, Names.Tessellator_, Names.Tessellator_vertexBuffers, "Ljava/nio/IntBuffer;");
					//mv.visitVarInsn(ALOAD, 0);
					//mv.visitFieldInsn(GETFIELD, Names.Tessellator_, Names.Tessellator_vertexBuffers, "Ljava/nio/IntBuffer;");
					//mv.visitMethodInsn(INVOKESTATIC, "org/lwjgl/opengl/ARBVertexBufferObject", "glGenBuffersARB", "(Ljava/nio/IntBuffer;)V");
					//mv.visitLabel(l31);
					//mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
				}
				mv.visitVarInsn(ALOAD, 0);
				mv.visitIntInsn(BIPUSH, 16);
				mv.visitIntInsn(NEWARRAY, T_FLOAT);
				mv.visitFieldInsn(PUTFIELD, Names.tessellator_.clas, "vertexPos", "[F");
			}
			mv.visitInsn(opcode);
		}
	}

	private static class MVdraw extends MethodVisitor
	{
		public MVdraw(MethodVisitor mv) {
			super(Opcodes.ASM4, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitLineNumber(185, l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESTATIC, "shadersmodcore/client/ShadersTess", "draw", "("+Names.tessellator_.desc+")I");
			mv.visitInsn(IRETURN);
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLocalVariable("this", Names.tessellator_.desc, null, l0, l1, 0);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}
	}

	private static class MVreset extends MethodVisitor
	{
		public MVreset(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name,
				String desc) {
			//SMCLog.finest("     F %d %s.%s %s", opcode, ownerM, nameM, descM);
			if (opcode==GETSTATIC && Names.tessellator_byteBuffer.equals(owner, name))
			{
				mv.visitVarInsn(ALOAD, 0);
				mv.visitFieldInsn(GETFIELD, owner, name, desc);
				return;
			}
			mv.visitFieldInsn(opcode, owner, name, desc);
		}
	}

	private static class MVaddVertex extends MethodVisitor
	{
		public MVaddVertex(MethodVisitor mv) {
			super(Opcodes.ASM4, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitLineNumber(466, l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(DLOAD, 1);
			mv.visitVarInsn(DLOAD, 3);
			mv.visitVarInsn(DLOAD, 5);
			mv.visitMethodInsn(INVOKESTATIC, "shadersmodcore/client/ShadersTess", "addVertex", "("+Names.tessellator_.desc+"DDD)V");
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLineNumber(467, l1);
			mv.visitInsn(RETURN);
			Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitLocalVariable("this", Names.tessellator_.desc, null, l0, l2, 0);
			mv.visitLocalVariable("par1", "D", null, l0, l2, 1);
			mv.visitLocalVariable("par3", "D", null, l0, l2, 3);
			mv.visitLocalVariable("par5", "D", null, l0, l2, 5);
			mv.visitMaxs(7, 7);
			mv.visitEnd();
		}
	}

	private static class MVsetNormal extends MethodVisitor
	{
		public MVsetNormal(MethodVisitor mv) {
			super(Opcodes.ASM4, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitInsn(ICONST_1);
			mv.visitFieldInsn(PUTFIELD, Names.tessellator_hasNormals.clas, Names.tessellator_hasNormals.name, Names.tessellator_hasNormals.desc);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(FLOAD, 1);
			mv.visitFieldInsn(PUTFIELD, Names.tessellator_.clas, "normalX", "F");
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(FLOAD, 2);
			mv.visitFieldInsn(PUTFIELD, Names.tessellator_.clas, "normalY", "F");
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(FLOAD, 3);
			mv.visitFieldInsn(PUTFIELD, Names.tessellator_.clas, "normalZ", "F");
			mv.visitInsn(RETURN);
			Label l5 = new Label();
			mv.visitLabel(l5);
			mv.visitLocalVariable("this", Names.tessellator_.desc, null, l0, l5, 0);
			mv.visitLocalVariable("par1", "F", null, l0, l5, 1);
			mv.visitLocalVariable("par2", "F", null, l0, l5, 2);
			mv.visitLocalVariable("par3", "F", null, l0, l5, 3);
			mv.visitMaxs(2, 4);
			mv.visitEnd();
		}
	}

	private static class MVsortQuad extends MethodVisitor
	{
		public MVsortQuad(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitIntInsn(int opcode, int operand) {
			if (opcode == BIPUSH && operand == 32)
			{
				operand = 64;
			}
			super.visitIntInsn(opcode, operand);
		}

	}


}
