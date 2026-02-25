package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String GL11C = "org/lwjgl/opengl/GL11C";
    private static final String GL12 = "org/lwjgl/opengl/GL12";
    private static final String GL13 = "org/lwjgl/opengl/GL13";
    private static final String GL14 = "org/lwjgl/opengl/GL14";
    private static final String GL15 = "org/lwjgl/opengl/GL15";
    private static final String GL20 = "org/lwjgl/opengl/GL20";
    private static final String GL30 = "org/lwjgl/opengl/GL30";
    private static final String ARBVertexArrayObject = "org/lwjgl/opengl/ARBVertexArrayObject";
    private static final String APPLEVertexArrayObject = "org/lwjgl/opengl/APPLEVertexArrayObject";
    private static final String Project = "org/lwjgl/util/glu/Project";
    private static final String OpenGlHelper = "net/minecraft/client/renderer/OpenGlHelper";
    private static final String EXTBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";
    private static final String ARBMultiTexture = "org/lwjgl/opengl/ARBMultitexture";
    private static final String ARBShaderObjects = "org/lwjgl/opengl/ARBShaderObjects";

    // Redirect VAO related calls from NHLib and LWJGLService
    private static final String UniversalVAO = "com/gtnewhorizon/gtnhlib/client/opengl/UniversalVAO";
    private static final String UniversalVAODot = "com.gtnewhorizon.gtnhlib.client.opengl.UniversalVAO";
    private static final String VaoFunctions = "com/gtnewhorizon/gtnhlib/client/renderer/vao/VaoFunctions";
    private static final String LWJGLService = "com/mitchej123/lwjgl/LWJGLService";

    // Don't redirect these items inside of UniversalVAO, since we might be delegating to them
    private static final Set<String> UniversalVAOSkippedMethods = ImmutableSet.of(
        "glBindVertexArray", "glGenVertexArrays", "glDeleteVertexArrays", "glIsVertexArray"
    );

    private static final String MinecraftClient = "net.minecraft.client";
    private static final String SplashProgress = "cpw.mods.fml.client.SplashProgress";
    private static final Set<String> ExcludedMinecraftMainThreadChecks = ImmutableSet.of(
        "startGame", "func_71384_a",
        "initializeTextures", "func_77474_a"
    );

    private static final Map<String, Map<String, String>> methodRedirects = new HashMap<>();
    private static final Map<String, Map<String, String>> interfaceRedirects = new HashMap<>();
    private static final Map<Integer, String> glCapRedirects = new HashMap<>();
    private static final ClassConstantPoolParser cstPoolParser;

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
            .add("glCallLists")
            .add("glClear")
            .add("glClearColor")
            .add("glClearDepth")
            .add("glClearStencil")
            .add("glClipPlane")
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
            .add("glDeleteLists")
            .add("glGenLists")
            .add("glIsList")
            .add("glDeleteTextures")
            .add("glDepthFunc")
            .add("glDepthMask")
            .add("glDepthRange")
            .add("glDrawArrays")
            .add("glDrawBuffer")
            .add("glDrawElements")
            .add("glEdgeFlag")
            .add("glEnd")
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
            .add("glLineStipple")
            .add("glLineWidth")
            .add("glListBase")
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
            .add("glTexEnvi")
            .add("glTexEnvf")
            .add("glTexEnv")
            .add("glTexGeni")
            .add("glTexGenf")
            .add("glTexGend")
            .add("glTexGen")
            .add("glTexImage1D")
            .add("glTexImage2D")
            .add("glTexImage3D")
            .add("glTexSubImage1D")
            .add("glTexSubImage2D")
            .add("glCopyTexImage1D")
            .add("glCopyTexImage2D")
            .add("glCopyTexSubImage1D")
            .add("glCopyTexSubImage2D")
            .add("glPixelStoref")
            .add("glPixelStorei")
            .add("glPixelTransferf")
            .add("glPixelTransferi")
            .add("glTexParameter")
            .add("glTexParameterf")
            .add("glTexParameteri")
            .add("glTexParameteri")
            .add("glCullFace")
            .add("glFrontFace")
            .add("glHint")
            .add("glLineStipple")
            .add("glLineWidth")
            .add("glPointSize")
            .add("glPolygonMode")
            .add("glPolygonOffset")
            .add("glPolygonStipple")
            .add("glAccum")
            .add("glReadBuffer")
            .add("glSampleCoverage")
            .add("glScissor")
            .add("glStencilFunc")
            .add("glStencilFuncSeparate")
            .add("glStencilMask")
            .add("glStencilMaskSeparate")
            .add("glStencilOp")
            .add("glStencilOpSeparate")
            .add("glTranslated")
            .add("glTranslatef")
            .add("glVertex2d")
            .add("glVertex2f")
            .add("glVertex3d")
            .add("glVertex3f")
            .add("glVertexPointer")
            .add("glColorPointer")
            .add("glNormalPointer")
            .add("glTexCoordPointer")
            .add("glEnableClientState")
            .add("glDisableClientState")
            .add("glInterleavedArrays")
            .add("glPushClientAttrib")
            .add("glPopClientAttrib")
            .add("glViewport")
        );
        methodRedirects.put(GL12, RedirectMap.newMap()
            .add("glTexImage3D")
            .add("glTexSubImage3D")
            .add("glCopyTexSubImage3D")
        );
        methodRedirects.put(GL13, RedirectMap.newMap()
            .add("glActiveTexture")
            .add("glSampleCoverage")
            .add("glClientActiveTexture")
            .add("glMultiTexCoord2f")
            .add("glMultiTexCoord2s")
        );
        methodRedirects.put(GL14, RedirectMap.newMap()
            .add("glBlendFuncSeparate", "tryBlendFuncSeparate")
            .add("glBlendColor")
            .add("glBlendEquation")
        );
        methodRedirects.put(GL15, RedirectMap.newMap()
            .add("glBindBuffer")
        );
        methodRedirects.put(GL20, RedirectMap.newMap()
            .add("glBlendEquationSeparate")
            .add("glDrawBuffers")
            .add("glStencilFuncSeparate")
            .add("glStencilMaskSeparate")
            .add("glStencilOpSeparate")
            .add("glUseProgram")
            .add("glShaderSource")
            .add("glLinkProgram")
            .add("glDeleteProgram")
            .add("glCreateShader")
            .add("glCompileShader")
            .add("glCreateProgram")
            .add("glAttachShader")
            .add("glDetachShader")
            .add("glValidateProgram")
            .add("glGetUniformLocation")
            .add("glUniform1f")
            .add("glUniform2f")
            .add("glUniform3f")
            .add("glUniform4f")
            .add("glUniform1i")
            .add("glUniform2i")
            .add("glUniform3i")
            .add("glUniform4i")
            .add("glUniform1")
            .add("glUniform2")
            .add("glUniform3")
            .add("glUniform4")
            .add("glUniformMatrix2")
            .add("glUniformMatrix3")
            .add("glUniformMatrix4")
            .add("glGetActiveUniform")
            .add("glGetAttachedShaders")
            .add("glGetShaderSource")
            .add("glGetUniform")
        );
        methodRedirects.put(GL30, RedirectMap.newMap()
            .add("glBindVertexArray")
            .add("glDeleteVertexArrays")
            .add("glBindFramebuffer")
        );

        // MINECRAFT
        methodRedirects.put(OpenGlHelper, RedirectMap.newMap()
            .add("glBlendFunc", "tryBlendFuncSeparate")
            .add("func_148821_a", "tryBlendFuncSeparate")
            .add("func_153171_g", "glBindFramebuffer")
            .add("func_153174_h", "glDeleteFramebuffers")
            .add("func_153165_e", "glGenFramebuffers")
            .add("func_153167_i", "glCheckFramebufferStatus")
            .add("func_153188_a", "glFramebufferTexture2D")
            .add("setActiveTexture")
            .add("setLightmapTextureCoords")
            .add("isFramebufferEnabled")
        );
        methodRedirects.put(EXTBlendFunc, RedirectMap.newMap().add("glBlendFuncSeparateEXT", "tryBlendFuncSeparate"));

        // ARB
        methodRedirects.put(ARBMultiTexture, RedirectMap.newMap()
            .add("glActiveTextureARB")
        );
        methodRedirects.put(ARBShaderObjects, RedirectMap.newMap()
            .add("glUseProgramObjectARB", "glUseProgram")
            .add("glShaderSourceARB", "glShaderSource")
            .add("glLinkProgramARB", "glLinkProgram")
            .add("glCreateShaderObjectARB", "glCreateShader")
            .add("glCompileShaderARB", "glCompileShader")
            .add("glCreateProgramObjectARB", "glCreateProgram")
            .add("glAttachObjectARB", "glAttachShader")
            .add("glDetachObjectARB", "glDetachShader")
            .add("glValidateProgramARB", "glValidateProgram")
            .add("glGetUniformLocationARB", "glGetUniformLocation")
            .add("glDeleteObjectARB")
            .add("glGetObjectParameterARB")
            .add("glGetObjectParameteriARB")
            .add("glGetInfoLogARB")
            .add("glGetHandleARB")
            .add("glUniform1fARB", "glUniform1f")
            .add("glUniform2fARB", "glUniform2f")
            .add("glUniform3fARB", "glUniform3f")
            .add("glUniform4fARB", "glUniform4f")
            .add("glUniform1iARB", "glUniform1i")
            .add("glUniform2iARB", "glUniform2i")
            .add("glUniform3iARB", "glUniform3i")
            .add("glUniform4iARB", "glUniform4i")
            .add("glUniform1ARB", "glUniform1")
            .add("glUniform2ARB", "glUniform2")
            .add("glUniform3ARB", "glUniform3")
            .add("glUniform4ARB", "glUniform4")
            .add("glUniformMatrix2ARB", "glUniformMatrix2")
            .add("glUniformMatrix3ARB", "glUniformMatrix3")
            .add("glUniformMatrix4ARB", "glUniformMatrix4")
            .add("glGetActiveUniformARB", "glGetActiveUniform")
            .add("glGetAttachedObjectsARB", "glGetAttachedShaders")
            .add("glGetShaderSourceARB", "glGetShaderSource")
            .add("glGetUniformARB", "glGetUniform")
        );
        methodRedirects.put(ARBVertexArrayObject, RedirectMap.newMap()
            .add("glBindVertexArray")
            .add("glDeleteVertexArrays")
        );


        // APPLE
        methodRedirects.put(APPLEVertexArrayObject, RedirectMap.newMap()
            .add("glBindVertexArrayAPPLE", "glBindVertexArray")
        );

        // GTNHLib VAO
        methodRedirects.put(UniversalVAO, RedirectMap.newMap()
            .add("bindVertexArray", "glBindVertexArray")
            .add("deleteVertexArrays", "glDeleteVertexArrays")
        );

        // OTHER
        methodRedirects.put(Project, RedirectMap.newMap().add("gluPerspective"));

        // Interface/virtual redirects — callers invoke these on a receiver object
        interfaceRedirects.put(VaoFunctions, RedirectMap.newMap()
            .add("glBindVertexArray")
            .add("glDeleteVertexArrays")
        );
        interfaceRedirects.put(LWJGLService, RedirectMap.newMap()
            .add("glBindVertexArray")
        );

        final List<String> stringsToSearch = new ArrayList<>();
        stringsToSearch.add(GL11);
        stringsToSearch.add(GL12);
        stringsToSearch.add(GL13);
        stringsToSearch.add(GL14);
        stringsToSearch.add(GL20);
        stringsToSearch.add(OpenGlHelper);
        stringsToSearch.add(EXTBlendFunc);
        stringsToSearch.add(ARBMultiTexture);
        stringsToSearch.add(ARBShaderObjects);
        stringsToSearch.add(Project);
        stringsToSearch.add(UniversalVAO);
        stringsToSearch.add(VaoFunctions);
        stringsToSearch.add(LWJGLService);
        final String glPrefix = "org/lwjgl/opengl/GL";
        for (var entry : new HashMap<>(methodRedirects).entrySet()) {
            if (entry.getKey().startsWith(glPrefix)) {
                methodRedirects.put(entry.getKey() + "C", entry.getValue());
                stringsToSearch.add(entry.getKey() + "C");
            }
        }
        cstPoolParser = new ClassConstantPoolParser(stringsToSearch.toArray(new String[0]));
    }

    public String[] getTransformerExclusions() {
        return new String[]{
            "org.lwjgl",
            "com.gtnewhorizons.angelica.glsm.",
            "com.gtnewhorizons.angelica.transform",
            "com.gtnewhorizon.gtnhlib.asm.",
            "me.eigenraven.lwjgl3ify"
        };
    }

    public boolean shouldTransform(byte[] basicClass) {
        return cstPoolParser.find(basicClass, true);
    }

    /** @return Was the class changed? */
    public boolean transformClassNode(String transformedName, ClassNode cn) {
        boolean changed = false;
        final boolean isOpenGlHelper = transformedName.equals("net.minecraft.client.renderer.OpenGlHelper");
        final boolean isUniversalVAO = transformedName.startsWith(UniversalVAODot);
        for (MethodNode mn : cn.methods) {
            if (isOpenGlHelper && (mn.name.equals("glBlendFunc") || mn.name.equals("func_148821_a"))) {
                continue;
            }
            boolean redirectInMethod = false;
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node instanceof MethodInsnNode mNode) {
                    if (mNode.desc.equals("(I)V") && (mNode.owner.equals(GL11) || mNode.owner.equals(GL11C)) && (mNode.name.equals("glEnable") || mNode.name.equals("glDisable"))) {
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
                    } else if (mNode.name.equals("makeCurrent") && mNode.owner.startsWith(Drawable)) {
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
                        if (redirects != null) {
                            final String glsmName = redirects.get(mNode.name);
                            // Skip VAO method redirects inside UniversalVAO
                            if (glsmName != null && !(isUniversalVAO && UniversalVAOSkippedMethods.contains(glsmName))) {
                                if (LOG_SPAM) {
                                    final String shortOwner = mNode.owner.substring(mNode.owner.lastIndexOf("/") + 1);
                                    LOGGER.info("Redirecting call in {} from {}.{}{} to GLStateManager.{}{}", transformedName, shortOwner, mNode.name, mNode.desc, glsmName, mNode.desc);
                                }
                                mNode.owner = GLStateManager;
                                mNode.name = glsmName;
                                changed = true;
                                redirectInMethod = true;
                            }
                        }
                        // Interface/virtual redirects: rewrite receiver-based calls to static GLStateManager calls
                        if ((mNode.getOpcode() == Opcodes.INVOKEINTERFACE || mNode.getOpcode() == Opcodes.INVOKEVIRTUAL) && mNode.desc.equals("(I)V")) {
                            final Map<String, String> ifaceRedirects = interfaceRedirects.get(mNode.owner);
                            if (ifaceRedirects != null) {
                                final String glsmName = ifaceRedirects.get(mNode.name);
                                if (glsmName != null && !(isUniversalVAO && UniversalVAOSkippedMethods.contains(glsmName))) {
                                    if (LOG_SPAM) {
                                        final String shortOwner = mNode.owner.substring(mNode.owner.lastIndexOf("/") + 1);
                                        LOGGER.info("Redirecting interface call in {} from {}.{}{} to GLStateManager.{}{}", transformedName, shortOwner, mNode.name, mNode.desc, glsmName, "(I)V");
                                    }
                                    // Stack is: [receiver, int_arg]. We need just [int_arg] for static call.
                                    // Insert SWAP + POP before the call to remove the receiver.
                                    mn.instructions.insertBefore(mNode, new InsnNode(Opcodes.SWAP));
                                    mn.instructions.insertBefore(mNode, new InsnNode(Opcodes.POP));
                                    mNode.setOpcode(Opcodes.INVOKESTATIC);
                                    mNode.owner = GLStateManager;
                                    mNode.name = glsmName;
                                    mNode.desc = "(I)V";
                                    mNode.itf = false;
                                    changed = true;
                                    redirectInMethod = true;
                                }
                            }
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

    private static final class RedirectMap<K> extends HashMap<K, K> {

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
