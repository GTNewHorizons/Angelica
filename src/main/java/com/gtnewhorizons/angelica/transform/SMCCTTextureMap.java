package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class SMCCTTextureMap implements IClassTransformer {

    @Override
    public byte[] transform(String par1, String par2, byte[] par3) {
        SMCLog.fine("transforming %s %s", par1, par2);
        ClassReader cr = new ClassReader(par3);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        CVTransform cv = new CVTransform(cw);
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static class CVTransform extends ClassVisitor {
        String classname;
        boolean endFields = false;

        public CVTransform(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            classname = name;
            // SMCLog.info(" class %s",name);
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            if (name.equals("atlasWidth")) {
                return null;
            } else if (name.equals("atlasHeight")) {
                return null;
            } else if (Names.textureMap_anisotropic.name.equals(name)) {
                access = access & (~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC;
            }
            return super.visitField(access, name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (!endFields) {
                endFields = true;
                FieldVisitor fv;
                fv = cv.visitField(ACC_PUBLIC, "atlasWidth", "I", null, null);
                fv.visitEnd();
                fv = cv.visitField(ACC_PUBLIC, "atlasHeight", "I", null, null);
                fv.visitEnd();
            }
            // SMCLog.info("  method %s.%s%s = %s",classname,name,desc,remappedName);
            if (Names.textureMap_getIconResLoc.equalsNameDesc(name, desc)) {
                access = access & (~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC;
            } else if (Names.textureMap_loadTextureAtlas.equalsNameDesc(name, desc)) {
                // SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
                return new MVloadAtlas(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.textureMap_updateAnimations.equalsNameDesc(name, desc)) {
                // SMCLog.finer("  patching method %s.%s%s = %s",classname,name,desc,nameM);
                return new MVanimation(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVloadAtlas extends MethodVisitor {
        // protected MethodVisitor mv;
        public MVloadAtlas(MethodVisitor mv) {
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

        int varStitcher = 0;
        boolean isStitcher = false;
        int state = 0;

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (opcode == ASTORE) {
                if (isStitcher) {
                    isStitcher = false;
                    varStitcher = var;
                }
            }
            mv.visitVarInsn(opcode, var);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            // SMCLog.finest("    %s.%s%s",ownerM,nameM,descM);
            if (Names.iResourceManager_getResource.equals(owner, name, desc)) {
                SMCLog.finest("    %s", "loadRes");
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "loadResource",
                        "(" + Names.iResourceManager_.desc + Names.resourceLocation_.desc + ")"
                                + Names.iResource_.desc);
                return;
            } else if (opcode == INVOKESPECIAL
                    && Names.equals(Names.stitcher_.clas, "<init>", "(IIZII)V", owner, name, desc)) {
                isStitcher = true;
            } else if (Names.textureUtil_allocateTextureMipmapAniso.equals(owner, name, desc)) {
                SMCLog.finest("    %s", "allocateTextureMap");
                mv.visitVarInsn(ALOAD, varStitcher);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "allocateTextureMap",
                        "(IIIIF" + Names.stitcher_.desc + Names.textureMap_.desc + ")V");
                state = 1;
                return;
            } else if (state == 1 && Names.textureAtlasSpri_getIconName.equals(owner, name, desc)) {
                SMCLog.finest("    %s", "setSprite setIconName");
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "setSprite",
                        "(" + Names.textureAtlasSpri_.desc + ")" + Names.textureAtlasSpri_.desc);
                mv.visitMethodInsn(opcode, owner, name, desc);
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "setIconName",
                        "(Ljava/lang/String;)Ljava/lang/String;");
                state = 0;
                return;
            } else if (Names.textureUtil_uploadTexSub.equals(owner, name, desc)) {
                SMCLog.finest("    %s", "uploadTexSubForLoadAtlas");
                mv.visitMethodInsn(
                        INVOKESTATIC, "com/gtnewhorizons/angelica/client/ShadersTex", "uploadTexSubForLoadAtlas", "([[IIIIIZZ)V");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVanimation extends MethodVisitor {
        public MVanimation(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitCode() {
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL, Names.textureMap_.clas, "getMultiTexID", "()Lcom/gtnewhorizons/angelica/client/MultiTexID;");
            mv.visitFieldInsn(
                    PUTSTATIC, "com/gtnewhorizons/angelica/client/ShadersTex", "updatingTex", "Lcom/gtnewhorizons/angelica/client/MultiTexID;");
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == RETURN) {
                mv.visitInsn(ACONST_NULL);
                mv.visitFieldInsn(
                        PUTSTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersTex",
                        "updatingTex",
                        "Lcom/gtnewhorizons/angelica/client/MultiTexID;");
            }
            mv.visitInsn(opcode);
        }
    }
}
