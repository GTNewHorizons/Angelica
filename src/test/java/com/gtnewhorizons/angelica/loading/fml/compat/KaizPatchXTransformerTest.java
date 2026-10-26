package com.gtnewhorizons.angelica.loading.fml.compat;

import com.gtnewhorizons.angelica.glsm.testutil.DefiningClassLoader;
import com.gtnewhorizons.angelica.loading.fml.compat.transformers.specific.KaizPatchXTransformer;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Label;
import org.spongepowered.asm.lib.MethodVisitor;
import org.spongepowered.asm.lib.Opcodes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KaizPatchXTransformerTest {

    private static final String INTERNAL = "jp/kaiz/kaizpatch/compat/AngelicaCompat";
    private static final String DOTTED = "jp.kaiz.kaizpatch.compat.AngelicaCompat";
    private static final String DESC = "(Ljava/lang/String;)Ljava/lang/String;";

    private static ClassWriter angelicaCompat() {
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, INTERNAL, null, "java/lang/Object", null);
        final MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "transformScript", DESC, null, null);
        mv.visitCode();
        final Label nonEmpty = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, nonEmpty);
        mv.visitLdcInsn("GL11.glPushMatrix();");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(nonEmpty);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn("");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", DESC, false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        return cw;
    }

    @Test
    void redirectsEveryReturn() throws ReflectiveOperationException {
        final ClassWriter cw = angelicaCompat();
        cw.visitEnd();
        final byte[] out = new KaizPatchXTransformer().transform(DOTTED, DOTTED, cw.toByteArray());
        final Method transformScript = DefiningClassLoader.define(DOTTED, out).getMethod("transformScript", String.class);
        for (String script : new String[] {"", "GLStateManager.glPopMatrix();"}) {
            final String result = (String) transformScript.invoke(null, script);
            assertTrue(result.contains("GLSMMatrices."), result);
        }
    }
}
