package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** transformer for net.minecraft.client.renderer.texture.TextureAtlasSprite */
public class ACTTextureAtlasSprite implements IClassTransformer {

    @Override
    public byte[] transform(String par1, String par2, byte[] par3) {
        ALog.fine("transforming %s %s", par1, par2);
        ClassReader cr = new ClassReader(par3);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        CVTransform cv = new CVTransform(cw);
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static class CVTransform extends ClassVisitor {

        String classname;

        public CVTransform(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classname = name;
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            // String nameM = SMCRemap.remapper.mapFieldName(classname, name, desc);
            access = access & ~(ACC_PRIVATE | ACC_PROTECTED) | ACC_PUBLIC;
            return cv.visitField(access, name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // SMCLog.info(" method %s.%s%s = %s",classname,name,desc,remappedName);
            access = access & ~(ACC_PRIVATE | ACC_PROTECTED) | ACC_PUBLIC;
            if (Names.textureAtlasSpri_updateAnimation.equalsNameDesc(name, desc)) {
                // SMCLog.finer(" patching method %s.%s%s = %s",classname,name,desc,nameM);
                return new MVanimation(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (name.equals("load")
                    && desc.equals("(" + Names.iResourceManager_.desc + Names.resourceLocation_.desc + ")Z")) {
                        // SMCLog.finer(" patching method %s.%s%s = %s",classname,name,desc,nameM);
                        return new MVload(cv.visitMethod(access, name, desc, signature, exceptions));
                    } else
                if (Names.textureAtlasSpri_loadSprite.equalsNameDesc(name, desc)) {
                    // SMCLog.finer(" patching method %s.%s%s = %s",classname,name,desc,nameM);
                    return new MVloadSprite(cv.visitMethod(access, name, desc, signature, exceptions));
                } else if (name.equals("uploadFrameTexture") && desc.equals("(III)V")) {
                    // SMCLog.finer(" patching method %s.%s%s = %s",classname,name,desc,nameM);
                    return new MVuploadFrameTexture(cv.visitMethod(access, name, desc, signature, exceptions));
                }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    // also used by clock and compass
    protected static class MVanimation extends MethodVisitor {

        // protected MethodVisitor mv;
        public MVanimation(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
            // this.mv = mv;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.textureUtil_uploadTexSub.equals(owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "uploadTexSub",
                        "([[IIIIIZZ)V");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVload extends MethodVisitor {

        // protected MethodVisitor mv;
        public MVload(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
            // this.mv = mv;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.iResourceManager_getResource.equals(owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "loadResource",
                        "(" + Names.iResourceManager_.desc
                                + Names.resourceLocation_.desc
                                + ")"
                                + Names.iResource_.desc);
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVloadSprite extends MethodVisitor {

        // protected MethodVisitor mv;
        public MVloadSprite(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
            // this.mv = mv;
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == NEWARRAY && operand == T_INT) {
                mv.visitInsn(ICONST_3);
                mv.visitInsn(IMUL);
            }
            mv.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.equals("java/awt/image/BufferedImage", "getRGB", "(IIII[III)[I", owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "loadAtlasSprite",
                        "(Ljava/awt/image/BufferedImage;IIII[III)[I");
                return;
            } else if (Names.textureAtlasSpri_getFrameTextureData.equals(owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "extractFrame",
                        "([IIII)[I");
                return;
            } else if (Names.equals(Names.textureAtlasSpri_.clas, "fixTransparentColor", "([I)V", owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "fixTransparentColor",
                        "(" + Names.textureAtlasSpri_.desc + "[I)V");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVuploadFrameTexture extends MethodVisitor {

        public MVuploadFrameTexture(MethodVisitor mv) {
            super(Opcodes.ASM4, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitVarInsn(ILOAD, 3);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "com/gtnewhorizons/angelica/client/ShadersTex",
                    "uploadFrameTexture",
                    "(" + Names.textureAtlasSpri_.desc + "III)V");
            mv.visitInsn(RETURN);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("this", Names.textureAtlasSpri_.desc, null, l0, l2, 0);
            mv.visitLocalVariable("frameIndex", "I", null, l0, l2, 1);
            mv.visitLocalVariable("xPos", "I", null, l0, l2, 2);
            mv.visitLocalVariable("yPos", "I", null, l0, l2, 3);
            mv.visitMaxs(4, 4);
            mv.visitEnd();
        }
    }
}
