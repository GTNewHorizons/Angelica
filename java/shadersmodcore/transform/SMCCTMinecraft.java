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

public class SMCCTMinecraft implements IClassTransformer {

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
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) 
		{
			//SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
			if (Names.minecraft_startGame.equalsNameDesc(name, desc))
			{
				//SMCLog.info("  patching");
				return new MVstartGame(super.visitMethod(access, name, desc, signature, exceptions));
			}
			return super.visitMethod(access, name, desc, signature, exceptions);
		}
		
	}

	private static class MVstartGame extends MethodVisitor
	{
		int state = 0;

		public MVstartGame(MethodVisitor mv) {
			super(Opcodes.ASM4, mv);
		}

		@Override
		public void visitLdcInsn(Object cst) {
			if (cst instanceof String)
			{
				if (((String)cst).equals("Startup"))
				{
					if (state == 0) {
						state = 1;
					}
				}
			}
			super.visitLdcInsn(cst);
		}

		@Override
		public void visitVarInsn(int opcode, int var) {
			if (opcode == Opcodes.ALOAD) {
				if (var == 0) {
					if (state == 1) {
						state = 2;
						mv.visitVarInsn(ALOAD, 0);
						mv.visitMethodInsn(INVOKESTATIC, "shadersmodcore/client/Shaders", "startup", "("+Names.minecraft_.desc+")V");
						//SMCLog.finest("    startup");
					}
				}
			}
			super.visitVarInsn(opcode, var);
		}

	}

}
