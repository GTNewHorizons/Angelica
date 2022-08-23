package com.gtnewhorizons.angelica.transform;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** transformer for net.minecraft.client.renderer.EntityRenderer */
public class SMCCTEntityRenderer implements IClassTransformer {

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
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (Names.entityRenderer_disableLightmap.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVdisableLightmap(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.entityRenderer_enableLightmap.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVenableLightmap(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.entityRenderer_updateFogColor.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVupdateFogColor(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.entityRenderer_setFogColorBuffer.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVsetFogColorBuffer(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.entityRenderer_setupFog.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVsetupFog(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.entityRenderer_renderCloudsCheck.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVrenderCloudsCheck(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.entityRenderer_renderHand.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                access = access & (~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC;
                return new MVrenderHand(cv.visitMethod(access, name, desc, signature, exceptions));
            } else if (Names.entityRenderer_renderWorld.equalsNameDesc(name, desc)) {
                SMCLog.finer("  patch method %s.%s%s", classname, name, desc);
                return new MVrenderWorld(cv.visitMethod(access, name, desc, signature, exceptions));
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class MVdisableLightmap extends MethodVisitor {
        public MVdisableLightmap(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == RETURN) {
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "disableLightmap", "()V");
            }
            mv.visitInsn(opcode);
        }
    }

    private static class MVenableLightmap extends MethodVisitor {
        public MVenableLightmap(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == RETURN) {
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "enableLightmap", "()V");
            }
            mv.visitInsn(opcode);
        }
    }

    private static class MVupdateFogColor extends MethodVisitor {
        public MVupdateFogColor(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.equals("org/lwjgl/opengl/GL11", "glClearColor", "(FFFF)V", owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "setClearColor", "(FFFF)V");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVsetFogColorBuffer extends MethodVisitor {
        public MVsetFogColorBuffer(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitCode() {
            mv.visitCode();
            mv.visitVarInsn(FLOAD, 1);
            mv.visitVarInsn(FLOAD, 2);
            mv.visitVarInsn(FLOAD, 3);
            mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "setFogColor", "(FFF)V");
        }
    }

    private static class MVsetupFog extends MethodVisitor {
        public MVsetupFog(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.equals("org/lwjgl/opengl/GL11", "glFogi", "(II)V", owner, name, desc)) {
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "sglFogi", "(II)V");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVrenderCloudsCheck extends MethodVisitor {
        public MVrenderCloudsCheck(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.gameSettings_shouldRenderClouds.equals(owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/Shaders",
                        "shouldRenderClouds",
                        "(" + Names.gameSettings_.desc + ")Z");
                return;
            } else if (Names.renderGlobal_renderClouds.equals(owner, name, desc)) {
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginClouds", "()V");
                mv.visitMethodInsn(opcode, owner, name, desc);
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endClouds", "()V");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVrenderHand extends MethodVisitor {
        Label la1, la2;

        public MVrenderHand(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
            la1 = new Label();
            la2 = new Label();
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (Names.equals("org/lwjgl/util/glu/Project", "gluPerspective", "(FFFF)V", owner, name, desc)) {
                mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "applyHandDepth", "()V");
                mv.visitMethodInsn(opcode, owner, name, desc);
                return;
            } else if (Names.equals("org/lwjgl/opengl/GL11", "glPushMatrix", "()V", owner, name, desc)) {
                mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "isHandRendered", "Z");
                mv.visitJumpInsn(IFNE, la1);
                mv.visitMethodInsn(opcode, owner, name, desc);
                return;
            } else if (Names.equals("org/lwjgl/opengl/GL11", "glPopMatrix", "()V", owner, name, desc)) {
                mv.visitMethodInsn(opcode, owner, name, desc);
                mv.visitLabel(la1);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "isCompositeRendered", "Z");
                mv.visitJumpInsn(IFNE, la2);
                mv.visitInsn(RETURN);
                mv.visitLabel(la2);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(FLOAD, 1);
                mv.visitInsn(F2D);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        Names.entityRenderer_disableLightmap.clas,
                        Names.entityRenderer_disableLightmap.name,
                        Names.entityRenderer_disableLightmap.desc);
                return;
            } else if (Names.itemRenderer_renderItemInFirstPerson.equals(owner, name, desc)) {
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        "com/gtnewhorizons/angelica/client/ShadersRender",
                        "renderItemFP",
                        "(" + Names.itemRenderer_.desc + "F)V");
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    private static class MVrenderWorld extends MethodVisitor {
        public MVrenderWorld(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        private static final int stateEnd = 32;
        int state = 0;
        String section = "";
        Label labelAfterUpdate = null;
        Label labelEndUpdate = null;
        Label labelNoSky = null;

        @Override
        public void visitCode() {
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(
                    GETFIELD, Names.entityRenderer_mc.clas, Names.entityRenderer_mc.name, Names.entityRenderer_mc.desc);
            mv.visitVarInsn(FLOAD, 1);
            mv.visitVarInsn(LLOAD, 2);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "com/gtnewhorizons/angelica/client/Shaders",
                    "beginRender",
                    "(" + Names.minecraft_.desc + "FJ)V");
        }

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof String) {
                String scst = (String) cst;
                // SMCLog.finest("    %d Ldc %s", state, scst);
                section = scst;
            }
            mv.visitLdcInsn(cst);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            // SMCLog.finest("    %d Int %d %d", state ,opcode, operand);
            // switch (state) {
            // case 23:
            // case 25:
            //	if (opcode == SIPUSH && operand == 256) {
            //		state = 26;
            //		mv.visitInsn(ICONST_0);
            //		return;
            //	}
            //	break;
            // }
            mv.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            // SMCLog.finest("    %d, Jump %d", state, opcode);
            switch (state) {
                case 4:
                    if (opcode == IF_ICMPLT) {
                        ++state;
                        mv.visitInsn(POP2);
                        mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "isShadowPass", "Z");
                        mv.visitJumpInsn(IFNE, label);
                        return;
                    }
                    break;
                case 10:
                    if (opcode == IFNE) {
                        ++state;
                        labelAfterUpdate = label;
                        labelEndUpdate = new Label();
                        mv.visitJumpInsn(opcode, label);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginUpdateChunks", "()V");
                        return;
                    }
                    break;
                case 11:
                    if (label == labelAfterUpdate) {
                        mv.visitJumpInsn(opcode, labelEndUpdate);
                        return;
                    }
                    break;
                case 28:
                    if (opcode == IFNE) {
                        ++state;
                        mv.visitJumpInsn(opcode, label);
                        mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "isShadowPass", "Z");
                        mv.visitJumpInsn(IFNE, label);
                        //					mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders",
                        // "readCenterDepth",
                        // "()V");
                        //					mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders",
                        // "beginHand", "()V");
                        //					mv.visitVarInsn(ALOAD, 0);
                        //					mv.visitVarInsn(FLOAD, 1);
                        //					mv.visitVarInsn(ILOAD, 13);
                        //					mv.visitMethodInsn(INVOKESPECIAL, Names.EntityRenderer_, Names.EntityRenderer_renderHand,
                        // Names.EntityRenderer_renderHand_desc);
                        //					mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endHand",
                        // "()V");
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitVarInsn(FLOAD, 1);
                        mv.visitVarInsn(ILOAD, 13);
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/ShadersRender",
                                "renderHand1",
                                "(" + Names.entityRenderer_.desc + "FI)V");
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/Shaders",
                                "renderCompositeFinal",
                                "()V");
                        return;
                    }
                    break;
            }
            mv.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitLabel(Label label) {
            switch (state) {
                case 11:
                    if (label == labelAfterUpdate) {
                        ++state;
                        mv.visitLabel(labelEndUpdate);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endUpdateChunks", "()V");
                        mv.visitLabel(label);
                        labelAfterUpdate = labelEndUpdate = null;
                        return;
                    }
            }
            mv.visitLabel(label);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            // SMCLog.finest("    %d, Frame",state);
            switch (state) {
                case 7: {
                    state = 8;
                    mv.visitLabel(labelNoSky);
                    labelNoSky = null;
                    mv.visitFrame(type, nLocal, local, nStack, stack);
                    return;
                }

                case 31: {
                    ++state;
                    mv.visitFrame(type, nLocal, local, nStack, stack);
                    mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endRender", "()V");
                    return;
                }
            }
            mv.visitFrame(type, nLocal, local, nStack, stack);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            // SMCLog.finest("    %d F %d %s.%s %s", state, opcode, ownerM, nameM, descM);
            switch (state) {
                case 3:
                    if (Names.gameSettings_renderDistance.equals(owner, name)) {
                        ++state;
                    }
                    break;
                case 24:
                case 26:
                    if (Names.entityRenderer_cameraZoom.equals(owner, name)) {
                        state = 28;
                        mv.visitFieldInsn(opcode, owner, name, desc);
                        return;
                    }
                    break;
            }
            mv.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            switch (state) {
                case 29:
                    if (opcode == ALOAD) {
                        ++state;
                        mv.visitVarInsn(opcode, var);
                        return;
                    }
                    break;
            }
            mv.visitVarInsn(opcode, var);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            // SMCLog.finest("    %d M %d %s.%s%s", state, opcode, ownerM, nameM, descM);
            switch (state) {
                case 0:
                    if (Names.equals("org/lwjgl/opengl/GL11", "glViewport", "(IIII)V", owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "setViewport", "(IIII)V");
                        return;
                    }
                    break;
                case 1:
                    if (Names.equals("org/lwjgl/opengl/GL11", "glClear", "(I)V", owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "clearRenderBuffer", "()V");
                        return;
                    }
                    break;
                case 2:
                    if (Names.entityRenderer_setupCameraTransform.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitVarInsn(FLOAD, 1);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "setCamera", "(F)V");
                        return;
                    }
                    break;
                case 3:
                    if (Names.equals("Config", "isSkyEnabled", "()Z", owner, name, desc)) {
                        state = 6;
                        mv.visitFieldInsn(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "isShadowPass", "Z");
                        labelNoSky = new Label();
                        mv.visitJumpInsn(IFNE, labelNoSky);
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        return;
                    }
                    break;
                case 5:
                    if (Names.renderGlobal_renderSky.equals(owner, name, desc)) {
                        state = 8;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginSky", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endSky", "()V");
                        return;
                    }
                    break;
                case 6:
                    if (Names.renderGlobal_renderSky.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginSky", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endSky", "()V");
                        return;
                    }
                    break;
                case 8:
                    if (Names.iCamera_setPosition.equals(owner, name, desc)
                            || Names.frustrum_setPosition.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/ShadersRender",
                                "setFrustrumPosition",
                                "(" + Names.frustrum_.desc + "DDD)V");
                        return;
                    }
                    break;
                case 9:
                    if (Names.renderGlobal_clipRenderersByFrustum.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/ShadersRender",
                                "clipRenderersByFrustrum",
                                "(" + Names.renderGlobal_.desc + Names.frustrum_.desc + "F)V");
                        return;
                    }
                    break;
                case 12:
                    if (Names.renderGlobal_sortAndRender.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginTerrain", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endTerrain", "()V");
                        return;
                    }
                    break;
                case 13:
                    if (Names.effectRenderer_renderLitParticles.equals(owner, name, desc)) {
                        // vanilla or forge. not optfine
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginLitParticles", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        return;
                    } else if (Names.equals("org/lwjgl/opengl/GL11", "glDisable", "(I)V", owner, name, desc)) {
                        // optifine
                        state = 16;
                        break;
                    }
                    break;
                case 14: // vanilla or forge. not optfine
                    if (Names.effectRenderer_renderParticles.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginParticles", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endParticles", "()V");
                        return;
                    }
                    break;
                case 15: // vanilla or forge. not optfine
                    if (Names.equals("org/lwjgl/opengl/GL11", "glDisable", "(I)V", owner, name, desc)) {
                        ++state;
                        break;
                    }
                    break;
                case 16:
                    if (Names.entityRenderer_renderRainSnow.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginWeather", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endWeather", "()V");
                        return;
                    }
                    break;
                case 17:
                    if (Names.equals("org/lwjgl/opengl/GL11", "glDepthMask", "(Z)V", owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitVarInsn(FLOAD, 1);
                        mv.visitVarInsn(ILOAD, 13);
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/ShadersRender",
                                "renderHand0",
                                "(" + Names.entityRenderer_.desc + "FI)V");
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "preWater", "()V");
                        return;
                    }
                case 18:
                case 19:
                case 20:
                    if (Names.renderGlobal_sortAndRender.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginWater", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endWater", "()V");
                        return;
                    } else if (Names.equals(
                            Names.renderGlobal_.clas, "renderAllSortedRenderers", "(ID)I", owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginWater", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endWater", "()V");
                        return;
                    }
                    break;
                case 21:
                    if (Names.equals("org/lwjgl/opengl/GL11", "glDepthMask", "(Z)V", owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        return;
                    }
                    break;
                case 22:
                    if (Names.equals("org/lwjgl/opengl/GL11", "glDisable", "(I)V", owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        return;
                    }
                    break;
                case 23:
                    if (Names.equals("org/lwjgl/opengl/GL11", "glDisable", "(I)V", owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "disableFog", "()V");
                        return;
                    }
                    break;
                case 24:
                    if (Names.effectRenderer_renderLitParticles.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginLitParticles", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        return;
                    }
                    break;
                case 25:
                    if (Names.effectRenderer_renderParticles.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "beginParticles", "()V");
                        mv.visitMethodInsn(opcode, owner, name, desc);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "com/gtnewhorizons/angelica/client/Shaders", "endParticles", "()V");
                        return;
                    }
                    break;
                case 30:
                    if (Names.entityRenderer_renderHand.equals(owner, name, desc)) {
                        ++state;
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "com/gtnewhorizons/angelica/client/ShadersRender",
                                "renderFPOverlay",
                                "(" + Names.entityRenderer_.desc + "FI)V");
                        return;
                    }
                    break;
            }
            /*
            case :
            	if (Namer.equals(SMCNames.,SMCNames.,SMCNames._desc,owner,name,desc)) {
            		++state;
            		mv.visitMethodInsn(opcode, owner, name, desc);
            		return;
            	}
            	break;
            case :
            	if (Namer.equals("","","",owner,name,desc)) {
            		++state;
            		mv.visitMethodInsn(opcode, owner, name, desc);
            		return;
            	}
            	break;
            */
            mv.visitMethodInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitEnd() {
            if (state != stateEnd) SMCLog.severe("  state %d expect %d", state, stateEnd);
            mv.visitEnd();
        }
    }
}
