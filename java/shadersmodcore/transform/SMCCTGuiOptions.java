package shadersmodcore.transform;

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

public class SMCCTGuiOptions implements IClassTransformer {

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
			this.classname = name;
			//SMCLog.info(" class %s",name);
			cv.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) 
		{
			//SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
			if (Names.guiOptions_initGui.equalsNameDesc(name, desc))
			{
				//SMCLog.info("  patching");
				return new MVinitGui(
						cv.visitMethod(access, name, desc, signature, exceptions));
			}
			if (Names.guiOptions_actionPerformed.equalsNameDesc(name, desc))
			{
				//SMCLog.info("  patching");
				return new MVactionPerformed(
						cv.visitMethod(access, name, desc, signature, exceptions));
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}
		
	}

	private static class MVinitGui extends MethodVisitor
	{
		int state = 0;

		public MVinitGui(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitInsn(int opcode) {
			super.visitInsn(opcode);
			if (opcode == Opcodes.POP){
				if (state == 1) {
					state = 2;
					mv.visitVarInsn(ALOAD, 0);
					mv.visitFieldInsn(GETFIELD, Names.guiOptions_buttonList.clas, Names.guiOptions_buttonList.name, Names.guiOptions_buttonList.desc);
					mv.visitTypeInsn(NEW, Names.guiButton_.clas);
					mv.visitInsn(DUP);
					mv.visitIntInsn(SIPUSH, 190);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitFieldInsn(GETFIELD, Names.guiOptions_width.clas, Names.guiOptions_width.name, Names.guiOptions_width.desc);
					mv.visitInsn(ICONST_2);
					mv.visitInsn(IDIV);
					mv.visitIntInsn(SIPUSH, 155);
					mv.visitInsn(ISUB);
					mv.visitIntInsn(BIPUSH, 76);
					mv.visitInsn(IADD);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitFieldInsn(GETFIELD, Names.guiOptions_height.clas, Names.guiOptions_height.name, Names.guiOptions_height.desc);
					mv.visitIntInsn(BIPUSH, 6);
					mv.visitInsn(IDIV);
					mv.visitIntInsn(BIPUSH, 120);
					mv.visitInsn(IADD);
					mv.visitIntInsn(BIPUSH, 6);
					mv.visitInsn(ISUB);
					mv.visitIntInsn(BIPUSH, 74);
					mv.visitIntInsn(BIPUSH, 20);
					mv.visitLdcInsn("Shaders...");
					mv.visitMethodInsn(INVOKESPECIAL, Names.guiButton_.clas, "<init>", "(IIIIILjava/lang/String;)V");
					mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z");
					mv.visitInsn(POP);
					SMCLog.finest("    add shaders button");
				}
			}
		}

		@Override
		public void visitLdcInsn(Object cst) {
			if (cst instanceof String)
			{
				if (((String)cst).equals("options.language"))
				{
					if (state == 0) {
						state = 1;
						mv.visitInsn(POP);
						mv.visitInsn(POP);
						mv.visitIntInsn(BIPUSH, 74);
						mv.visitIntInsn(BIPUSH, 20);
						SMCLog.finest("    decrease language button size");
					}
				}
			}
			super.visitLdcInsn(cst);
		}

	}

	private static class MVactionPerformed extends MethodVisitor
	{
		public MVactionPerformed(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitCode() {
			super.visitCode();
			mv.visitVarInsn(ALOAD, 1);
			mv.visitFieldInsn(GETFIELD, Names.guiButton_id.clas, Names.guiButton_id.name, Names.guiButton_id.desc);
			mv.visitIntInsn(SIPUSH, 190);
			Label l1 = new Label();
			mv.visitJumpInsn(IF_ICMPNE, l1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, Names.guiOptions_mc.clas, Names.guiOptions_mc.name, Names.guiOptions_mc.desc);
			mv.visitFieldInsn(GETFIELD, Names.minecraft_gameSettings.clas, Names.minecraft_gameSettings.name, Names.minecraft_gameSettings.desc);
			mv.visitMethodInsn(INVOKEVIRTUAL, Names.gameSettings_saveOptions.clas, Names.gameSettings_saveOptions.name, Names.gameSettings_saveOptions.desc);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, Names.guiOptions_mc.clas, Names.guiOptions_mc.name, Names.guiOptions_mc.desc);
			mv.visitTypeInsn(NEW, "shadersmodcore/client/GuiShaders");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(GETFIELD, Names.guiOptions_options.clas, Names.guiOptions_options.name, Names.guiOptions_options.desc);
			mv.visitMethodInsn(INVOKESPECIAL, "shadersmodcore/client/GuiShaders", "<init>", "("+Names.guiScreen_.desc+Names.gameSettings_.desc+")V");
			mv.visitMethodInsn(INVOKEVIRTUAL, Names.minecraft_displayGuiScreen.clas, Names.minecraft_displayGuiScreen.name, Names.minecraft_displayGuiScreen.desc);
			mv.visitLabel(l1);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			SMCLog.finest("    shaders button action");
		}

	}

}
