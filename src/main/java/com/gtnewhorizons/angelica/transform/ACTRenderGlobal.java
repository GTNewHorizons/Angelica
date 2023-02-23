package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;

import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ACTRenderGlobal implements IClassTransformer {

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
            // ALog.info(" class %s",name);
            cv.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            if (Names.renderGlobal_worldRenderers.name.equals(name)) {
                access = access & (~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC;
            }
            return cv.visitField(access, name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // String remappedName = SMCRemap.remapper.mapMethodName(classname, name, desc);
            // ALog.info(" method %s.%s%s = %s",classname,name,desc,remappedName);
            if (Names.renderGlobal_renderEntities.equalsNameDesc(name, desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVrenderEntities(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.renderGlobal_sortAndRender.equalsNameDesc(name, desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVendisTexFog(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.renderGlobal_renderSky.equalsNameDesc(name, desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVrenderSky(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.renderGlobal_drawBlockDamageTexture.equalsNameDesc(name, desc)
                    || name.equals("drawBlockDamageTexture")) // Optifine
            {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVdrawBlockDamageTexture(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.renderGlobal_drawSelectionBox.equalsNameDesc(name, desc)) {
                ALog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVendisTexFog(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVrenderEntities extends MethodVisitor {

        public MVrenderEntities(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int state = 0;

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof String) {
                String scst = (String) cst;
                if (scst.equals("entities")) {
                    state = 1;
                } else if (scst.equals("blockentities")) {
                    state = 4;
                }
            }
            mv.visitLdcInsn(cst);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (state == 2) {
                if (Names.renderManager_instance.equals(owner, name, desc)) {
                    state = 3;
                    mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "nextEntity", "()V");
                }
            }
            mv.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            mv.visitMethodInsn(opcode, owner, name, desc);
            if (state == 1) {
                state = 2;
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginEntities", "()V");
                ALog.finest("    %s", "beginEntities");
            } else if (state == 4) {
                state = 5;
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endEntities", "()V");
                ALog.finest("    %s", "endEntities");
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/Shaders",
                        "beginBlockEntities",
                        "()V");
                ALog.finest("    %s", "beginTileEntities");
            } else if (state == 5) {
                if (Names.entityRenderer_disableLightmap.equals(owner, name, desc)) {
                    state = 6;
                    mv.visitMethodInsn(
                            INVOKESTATIC,
                            "com/gtnewhorizons/angelica/client/Shaders",
                            "endBlockEntities",
                            "()V");
                    ALog.finest("    %s", "endTileEntities");
                }
            }
        }
    }

    /* detect glEnable or glDisable (TEXTURE_2D or FOG) */
    private static class MVendisTexFog extends MethodVisitor {

        public MVendisTexFog(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        int lastInt = 0;

        @Override
        public void visitIntInsn(int opcode, int operand) {
            mv.visitIntInsn(opcode, operand);
            if (opcode == SIPUSH && (operand == 3553 || operand == 2912)) {
                lastInt = operand;
            } else {
                lastInt = 0;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            mv.visitMethodInsn(opcode, owner, name, desc);
            if (owner.equals("org/lwjgl/opengl/GL11")) {
                if (name.equals("glEnable")) {
                    if (lastInt == GL11.GL_TEXTURE_2D) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "enableTexture2D",
                                "()V");
                        ALog.finest("    %s", "enableTexture2D");
                    } else if (lastInt == GL11.GL_FOG) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "enableFog",
                                "()V");
                        ALog.finest("    %s", "enableFog");
                    }
                } else if (name.equals("glDisable")) {
                    if (lastInt == GL11.GL_TEXTURE_2D) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "disableTexture2D",
                                "()V");
                        ALog.finest("    %s", "disableTexture2D");
                    } else if (lastInt == GL11.GL_FOG) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "disableFog",
                                "()V");
                        ALog.finest("    %s", "disableFog");
                    }
                }
            }
            lastInt = 0;
        }
    }

    /* extends MVenableTF */
    private static class MVrenderSky extends MVendisTexFog {

        public MVrenderSky(MethodVisitor mv) {
            super(mv);
        }

        int state = 0;
        boolean detectedOptifine = false;

        int lastInt = 0;
        int lastVar = 0;

        @Override
        public void visitIntInsn(int opcode, int operand) {
            mv.visitIntInsn(opcode, operand);
            if (opcode == SIPUSH) {
                lastInt = operand;
            } else {
                lastInt = 0;
            }
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            mv.visitVarInsn(opcode, var);
            if (opcode == ALOAD) {
                lastVar = var;
            } else {
                lastVar = 0;
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            switch (state) {
                case 0:
                    if (Names.vec3_xCoord.equals(owner, name)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "setSkyColor",
                                "(" + Names.vec3_.desc + ")V");
                        mv.visitVarInsn(ALOAD, lastVar);
                        break;
                    }
                    break;
                case 1:
                    if (Names.renderGlobal_glSkyList.equals(owner, name)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "preSkyList",
                                "()V");
                        break;
                    }
                    break;
            }
            mv.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            // ALog.info(" %s.%s%s",ownerM,nameM,descM);
            switch (state) {
                case 0:
                    if (owner.equals("Config") && name.equals("isSkyEnabled")) {
                        detectedOptifine = true;
                    }
                    break;
                case 2:
                    if (Names.worldClient_getRainStrength.equals(owner, name, desc)) {
                        ++state;
                    }
                    break;
            }
            //
            mv.visitMethodInsn(opcode, owner, name, desc);
            //
            if (owner.equals("org/lwjgl/opengl/GL11")) {
                if (name.equals("glEnable")) {
                    if (lastInt == GL11.GL_TEXTURE_2D) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "enableTexture2D",
                                "()V");
                        ALog.finest("    %s", "enableTexture2D");
                    } else if (lastInt == GL11.GL_FOG) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "enableFog",
                                "()V");
                        ALog.finest("    %s", "enableFog");
                    }
                } else if (name.equals("glDisable")) {
                    if (lastInt == GL11.GL_TEXTURE_2D) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "disableTexture2D",
                                "()V");
                        ALog.finest("    %s", "disableTexture2D");
                    } else if (lastInt == GL11.GL_FOG) {
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "disableFog",
                                "()V");
                        ALog.finest("    %s", "disableFog");
                    }
                } else if (name.equals("glRotatef")) {
                    ALog.finest("    *%s %d", "glRotatef", state);
                    if (state == 3) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "preCelestialRotate",
                                "()V");
                        ALog.finest("    %s", "preCelestialRotate");
                    } else if (state == 4) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "postCelestialRotate",
                                "()V");
                        ALog.finest("    %s", "postCelestialRotate");
                    }
                }
            }
        }
    }

    private static class MVdrawBlockDamageTexture extends MethodVisitor {

        int state = 0;

        public MVdrawBlockDamageTexture(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            switch (state) {
                case 0:
                    if (opcode == SIPUSH && operand == GL11.GL_ALPHA_TEST) {
                        ++state;
                    }
                    break;
                case 2:
                    if (opcode == SIPUSH && operand == GL11.GL_ALPHA_TEST) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "endBlockDestroyProgress",
                                "()V");
                        ALog.finest("    %s", "endBlockDestroyProgress");
                    }
                    break;
            }
            mv.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            switch (state) {
                case 1:
                    if (owner.equals("org/lwjgl/opengl/GL11") && name.equals("glEnable")) {
                        ++state;
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "beginBlockDestroyProgress",
                                "()V");
                        ALog.finest("    %s", "beginBlockDestroyProgress");
                        return;
                    }
                    break;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }
}
