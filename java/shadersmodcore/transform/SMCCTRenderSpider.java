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

public class SMCCTRenderSpider implements IClassTransformer {

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
			if (Names.renderDragon_shouldRenderPass.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVshouldRenderPass(cv.visitMethod(access, name, desc, signature, exceptions));
			}
			if (Names.renderEnderman_shouldRenderPass.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVshouldRenderPass(cv.visitMethod(access, name, desc, signature, exceptions));
			}
			if (Names.renderSpider_shouldRenderPass.equalsNameDesc(name, desc))
			{
				//SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
				return new MVshouldRenderPass(cv.visitMethod(access, name, desc, signature, exceptions));
			}
			return cv.visitMethod(access, name, desc, signature, exceptions);
		}
		
	}

	private static class MVshouldRenderPass extends MethodVisitor
	{
		public MVshouldRenderPass(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name,
				String desc) {
			mv.visitMethodInsn(opcode, owner, name, desc);
			if (Names.equals("org/lwjgl/opengl/GL11","glColor4f","(FFFF)V",owner,name,desc)) {
				mv.visitMethodInsn(INVOKESTATIC, "shadersmodcore/client/Shaders", "beginSpiderEyes", "()V");
			}
		}
	}

}
