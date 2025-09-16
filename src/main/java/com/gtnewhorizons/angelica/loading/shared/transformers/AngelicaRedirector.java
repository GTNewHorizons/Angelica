package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This transformer redirects many GL calls to our custom GLStateManager
 * <p>
 * THIS CLASS MIGHT BE LOADED ON A DIFFERENT CLASS LOADER,
 * IT SHOULD NOT CALL ANY CODE FROM THE MAIN MOD
 */
public final class AngelicaRedirector {

    private static final boolean ASSERT_MAIN_THREAD = Boolean.getBoolean("angelica.assertMainThread");
    private static final boolean LOG_SPAM = Boolean.getBoolean("angelica.redirectorLogspam");
    private static final Logger LOGGER = LogManager.getLogger("AngelicaRedirector");
    private static final String Drawable = "org/lwjgl/opengl/Drawable";
    private static final String GLStateManager = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL13 = "org/lwjgl/opengl/GL13";
    private static final String GL14 = "org/lwjgl/opengl/GL14";
    private static final String GL20 = "org/lwjgl/opengl/GL20";
    private static final String Project = "org/lwjgl/util/glu/Project";
    private static final String OpenGlHelper = "net/minecraft/client/renderer/OpenGlHelper";
    private static final String EXTBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";
    private static final String ARBMultiTexture = "org/lwjgl/opengl/ARBMultitexture";
    private static final String MinecraftClient = "net.minecraft.client";
    private static final String SplashProgress = "cpw.mods.fml.client.SplashProgress";
    private static final Set<String> ExcludedMinecraftMainThreadChecks = ImmutableSet.of(
        "startGame", "func_71384_a",
        "initializeTextures", "func_77474_a"
    );

    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(GL11, GL13, GL14, OpenGlHelper, EXTBlendFunc, ARBMultiTexture, Project);
    private static final Map<String, Map<String, String>> methodRedirects = new HashMap<>();
    private static final Map<Integer, String> glCapRedirects = new HashMap<>();

    static {
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_ALPHA_TEST, "AlphaTest");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_BLEND, "Blend");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_DEPTH_TEST, "DepthTest");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_CULL_FACE, "Cull");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_LIGHTING, "Lighting");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, "Texture");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_FOG, "Fog");
        glCapRedirects.put(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL, "RescaleNormal");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST, "ScissorTest");
        methodRedirects.put(GL11, RedirectMap.newMap()
            .add("glAlphaFunc")
            .add("glBegin")
            .add("glBindTexture")
            .add("glBlendFunc")
            .add("glCallList")
            .add("glClear")
            .add("glClearColor")
            .add("glClearDepth")
            .add("glColor3b")
            .add("glColor3d")
            .add("glColor3f")
            .add("glColor3ub")
            .add("glColor4b")
            .add("glColor4d")
            .add("glColor4f")
            .add("glColor4ub")
            .add("glColorMask")
            .add("glColorMaterial")
            .add("glDeleteTextures")
            .add("glDepthFunc")
            .add("glDepthMask")
            .add("glDepthRange")
            .add("glDrawArrays")
            .add("glDrawBuffer")
            .add("glDrawElements")
            .add("glEdgeFlag")
            .add("glEndList")
            .add("glFog")
            .add("glFogf")
            .add("glFogi")
            .add("glFrustum")
            .add("glGetBoolean")
            .add("glGetFloat")
            .add("glGetInteger")
            .add("glGetLight")
            .add("glGetMaterial")
            .add("glGetTexLevelParameteri")
            .add("glGetTexParameterf")
            .add("glGetTexParameteri")
            .add("glIsEnabled")
            .add("glMaterial")
            .add("glMaterialf")
            .add("glMateriali")
            .add("glLight")
            .add("glLightf")
            .add("glLighti")
            .add("glLightModel")
            .add("glLightModelf")
            .add("glLightModeli")
            .add("glLoadIdentity")
            .add("glLoadMatrix")
            .add("glLogicOp")
            .add("glMatrixMode")
            .add("glMultMatrix")
            .add("glNewList")
            .add("glNormal3b")
            .add("glNormal3d")
            .add("glNormal3f")
            .add("glNormal3i")
            .add("glOrtho")
            .add("glPopAttrib")
            .add("glPopMatrix")
            .add("glPushAttrib")
            .add("glPushMatrix")
            .add("glRasterPos2d")
            .add("glRasterPos2f")
            .add("glRasterPos2i")
            .add("glRasterPos3d")
            .add("glRasterPos3f")
            .add("glRasterPos3i")
            .add("glRasterPos4d")
            .add("glRasterPos4f")
            .add("glRasterPos4i")
            .add("glRotated")
            .add("glRotatef")
            .add("glScaled")
            .add("glScalef")
            .add("glShadeModel")
            .add("glTexCoord1d")
            .add("glTexCoord1f")
            .add("glTexCoord2d")
            .add("glTexCoord2f")
            .add("glTexCoord3d")
            .add("glTexCoord3f")
            .add("glTexCoord4d")
            .add("glTexCoord4f")
            .add("glTexImage2D")
            .add("glTexParameter")
            .add("glTexParameterf")
            .add("glTexParameteri")
            .add("glTexParameteri")
            .add("glTranslated")
            .add("glTranslatef")
            .add("glViewport")
        );
        methodRedirects.put(GL13, RedirectMap.newMap().add("glActiveTexture"));
        methodRedirects.put(GL14, RedirectMap.newMap()
            .add("glBlendFuncSeparate", "tryBlendFuncSeparate")
            .add("glBlendColor")
            .add("glBlendEquation")
        );
        methodRedirects.put(GL20, RedirectMap.newMap()
            .add("glBlendEquationSeparate")
            .add("glUseProgram")
        );
        methodRedirects.put(OpenGlHelper, RedirectMap.newMap()
            .add("glBlendFunc", "tryBlendFuncSeparate")
            .add("func_148821_a", "tryBlendFuncSeparate"));
        methodRedirects.put(EXTBlendFunc, RedirectMap.newMap().add("glBlendFuncSeparateEXT", "tryBlendFuncSeparate"));
        methodRedirects.put(ARBMultiTexture, RedirectMap.newMap().add("glActiveTextureARB"));
        methodRedirects.put(Project, RedirectMap.newMap().add("gluPerspective"));
    }

    public String[] getTransformerExclusions() {
        return new String[]{
            "org.lwjgl",
            "com.gtnewhorizons.angelica.glsm.",
            "com.gtnewhorizons.angelica.transform",
            "me.eigenraven.lwjgl3ify"
        };
    }

    public boolean shouldTransform(byte[] basicClass) {
        return cstPoolParser.find(basicClass, true);
    }

    /** @return Was the class changed? */
    public boolean transformClassNode(String transformedName, ClassNode cn) {
        if (cn == null) {
            return false;
        }

        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            if (transformedName.equals("net.minecraft.client.renderer.OpenGlHelper") && (mn.name.equals("glBlendFunc") || mn.name.equals("func_148821_a"))) {
                continue;
            }
            boolean redirectInMethod = false;
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node instanceof MethodInsnNode mNode) {
                    if (mNode.owner.equals(GL11) && (mNode.name.equals("glEnable") || mNode.name.equals("glDisable")) && mNode.desc.equals("(I)V")) {
                        final AbstractInsnNode prevNode = node.getPrevious();
                        String name = null;
                        if (prevNode instanceof LdcInsnNode ldcNode) {
                            name = glCapRedirects.get(((Integer) ldcNode.cst));
                        } else if (prevNode instanceof IntInsnNode intNode) {
                            name = glCapRedirects.get(intNode.operand);
                        }
                        if (name != null) {
                            if (mNode.name.equals("glEnable")) {
                                name = "enable" + name;
                            } else {
                                name = "disable" + name;
                            }
                        }
                        if (LOG_SPAM) {
                            if (name == null) {
                                LOGGER.info("Redirecting call in {} from GL11.{}(I)V to GLStateManager.{}(I)V", transformedName, mNode.name, mNode.name);
                            } else {
                                LOGGER.info("Redirecting call in {} from GL11.{}(I)V to GLStateManager.{}()V", transformedName, mNode.name, name);
                            }
                        }
                        mNode.owner = GLStateManager;
                        if (name != null) {
                            mNode.name = name;
                            mNode.desc = "()V";
                            mn.instructions.remove(prevNode);
                        }
                        changed = true;
                        redirectInMethod = true;
                    } else if (mNode.owner.startsWith(Drawable) && mNode.name.equals("makeCurrent")) {
                        mNode.setOpcode(Opcodes.INVOKESTATIC);
                        mNode.owner = GLStateManager;
                        mNode.desc = "(L" + Drawable + ";)V";
                        mNode.itf = false;
                        changed = true;
                        if (LOG_SPAM) {
                            LOGGER.info("Redirecting call in {} to GLStateManager.makeCurrent()", transformedName);
                        }
                    } else {
                        final Map<String, String> redirects = methodRedirects.get(mNode.owner);
                        if (redirects != null && redirects.containsKey(mNode.name)) {
                            if (LOG_SPAM) {
                                final String shortOwner = mNode.owner.substring(mNode.owner.lastIndexOf("/") + 1);
                                LOGGER.info("Redirecting call in {} from {}.{}{} to GLStateManager.{}{}", transformedName, shortOwner, mNode.name, mNode.desc, redirects.get(mNode.name), mNode.desc);
                            }
                            mNode.owner = GLStateManager;
                            mNode.name = redirects.get(mNode.name);
                            changed = true;
                            redirectInMethod = true;
                        }
                    }
                }
            }
            if (ASSERT_MAIN_THREAD && redirectInMethod && !transformedName.startsWith(SplashProgress) && !(transformedName.startsWith(MinecraftClient) && ExcludedMinecraftMainThreadChecks.contains(mn.name))) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, GLStateManager, "assertMainThread", "()V", false));
            }
        }

        return changed;
    }

    private static class RedirectMap<K> extends HashMap<K, K> {

        public static RedirectMap<String> newMap() {
            return new RedirectMap<>();
        }

        public RedirectMap<K> add(K name) {
            this.put(name, name);
            return this;
        }

        public RedirectMap<K> add(K name, K newName) {
            this.put(name, newName);
            return this;
        }
    }

}
