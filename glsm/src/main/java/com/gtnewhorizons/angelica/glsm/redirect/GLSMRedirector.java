package com.gtnewhorizons.angelica.glsm.redirect;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core GL call redirector for GLSM. Rewrites bytecode so that GL calls go through {@code GLStateManager} for state tracking and backend abstraction.
 * <p>
 * THIS CLASS MIGHT BE LOADED ON A DIFFERENT CLASS LOADER.
 * IT SHOULD NOT CALL ANY CODE FROM THE MAIN MOD.
 */
public class GLSMRedirector {

    private static final boolean ASSERT_MAIN_THREAD = Boolean.getBoolean("angelica.assertMainThread");
    private static final boolean LOG_SPAM = Boolean.getBoolean("angelica.redirectorLogspam");
    private static final Logger LOGGER = LogManager.getLogger("GLSMRedirector");

    private static final String Drawable = "org/lwjgl/opengl/Drawable";
    private static final String GLStateManager = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GL_PREFIX = "org/lwjgl/opengl/GL";
    private static final String ARBVertexArrayObject = "org/lwjgl/opengl/ARBVertexArrayObject";
    private static final String APPLEVertexArrayObject = "org/lwjgl/opengl/APPLEVertexArrayObject";
    private static final String Project = "org/lwjgl/util/glu/Project";
    private static final String GLU = "org/lwjgl/util/glu/GLU";
    private static final String OpenGlHelper = "net/minecraft/client/renderer/OpenGlHelper";
    private static final String EXTBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";
    private static final String ARBMultiTexture = "org/lwjgl/opengl/ARBMultitexture";
    private static final String ARBShaderObjects = "org/lwjgl/opengl/ARBShaderObjects";
    private static final String ARBInstancedArrays = "org/lwjgl/opengl/ARBInstancedArrays";

    // Redirect VAO related calls from NHLib
    private static final String UniversalVAO = "com/gtnewhorizon/gtnhlib/client/opengl/UniversalVAO";

    private static final String MinecraftClient = "net.minecraft.client";
    private static final String SplashProgress = "cpw.mods.fml.client.SplashProgress";
    private static final Set<String> ExcludedMinecraftMainThreadChecks = ImmutableSet.of(
        "startGame", "func_71384_a",
        "initializeTextures", "func_77474_a"
    );

    private static final Map<String, Map<String, String>> methodRedirects = new HashMap<>(32);
    private static final Map<String, String> glMethodRedirects = new HashMap<>(256);
    private static final Map<Integer, String> glCapRedirects = new HashMap<>();
    private static final Map<String, String> typeRedirects = new HashMap<>();
    private static final ClassConstantPoolParser cstPoolParser;

    private static final String[] CORE_EXCLUSIONS = {
        "org.lwjgl",
        "com.gtnewhorizon.gtnhlib.asm",
        "com.gtnewhorizons.angelica.glsm.",
        "me.eigenraven.lwjgl3ify"
    };

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

        final var gl11 = RedirectMap.newMap()
            // glEnable/Disable - Special cased in GLSMRedirector, but included here for the external API
            .add("glEnable")
            .add("glDisable")
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
            .add("glGenTextures")
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
            .add("glFlush")
            .add("glFinish")
            .add("glGetError")
            .add("glGetString")
            .add("glGetTexImage")
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
            .add("glGetTexEnv")
            .add("glGetTexEnvi")
            .add("glGetTexEnvf")
            .add("glTexGeni")
            .add("glTexGenf")
            .add("glTexGend")
            .add("glTexGen")
            .add("glGetTexGen")
            .add("glGetTexGeni")
            .add("glGetTexGenf")
            .add("glGetTexGend")
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
            .add("glVertex2i")
            .add("glVertex3d")
            .add("glVertex3f")
            .add("glVertex3i")
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
            .add("glFeedbackBuffer")
            .add("glRenderMode")
            .add("glPassThrough")
            .add("glSelectBuffer")
            .add("glInitNames")
            .add("glPushName")
            .add("glPopName")
            .add("glLoadName");
        final var gl12 = RedirectMap.newMap()
            .add("glTexImage3D")
            .add("glTexSubImage3D")
            .add("glCopyTexSubImage3D");
        final var gl13 = RedirectMap.newMap()
            .add("glActiveTexture")
            .add("glSampleCoverage")
            .add("glClientActiveTexture")
            .add("glMultiTexCoord2f")
            .add("glMultiTexCoord2d")
            .add("glMultiTexCoord2s");
        final var gl14 = RedirectMap.newMap()
            .add("glBlendFuncSeparate", "tryBlendFuncSeparate")
            .add("glBlendColor")
            .add("glBlendEquation");
        final var gl15 = RedirectMap.newMap()
            .add("glGenBuffers")
            .add("glBindBuffer")
            .add("glDeleteBuffers")
            .add("glBufferData")
            .add("glBufferSubData")
            .add("glMapBuffer")
            .add("glUnmapBuffer")
            .add("glGetBufferSubData")
            .add("glGetBufferParameteri")
            .add("glIsBuffer");
        final var gl20 = RedirectMap.newMap()
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
            .add("glGetAttribLocation")
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
            .add("glDeleteShader")
            .add("glGetShaderi")
            .add("glGetShaderInfoLog")
            .add("glGetProgrami")
            .add("glGetProgramInfoLog")
            .add("glBindAttribLocation")
            .add("glVertexAttrib2f")
            .add("glVertexAttrib2s")
            .add("glVertexAttrib3f")
            .add("glVertexAttrib4f")
            .add("glGetActiveUniform")
            .add("glGetAttachedShaders")
            .add("glGetShaderSource")
            .add("glGetUniform")
            .add("glVertexAttribPointer")
            .add("glEnableVertexAttribArray")
            .add("glDisableVertexAttribArray");
        final var gl30 = RedirectMap.newMap()
            .add("glGenVertexArrays")
            .add("glBindVertexArray")
            .add("glDeleteVertexArrays")
            .add("glBindFramebuffer")
            .add("glDeleteFramebuffers")
            .add("glGenFramebuffers")
            .add("glCheckFramebufferStatus")
            .add("glFramebufferTexture2D")
            .add("glVertexAttribIPointer")
            .add("glGenerateMipmap")
            .add("glGetFramebufferAttachmentParameteri")
            .add("glBlitFramebuffer");
        final var gl31 = RedirectMap.newMap()
            .add("glDrawElementsInstanced");
        final var gl32 = RedirectMap.newMap()
            .add("glFramebufferTexture");
        final var gl33 = RedirectMap.newMap()
            .add("glGenSamplers")
            .add("glDeleteSamplers")
            .add("glBindSampler")
            .add("glSamplerParameteri")
            .add("glSamplerParameterf")
            .add("glVertexAttribDivisor");
        final var gl42 = RedirectMap.newMap()
            .add("glBindImageTexture")
            .add("glMemoryBarrier")
            .add("glTexStorage2D");
        final var gl43 = RedirectMap.newMap()
            .add("glDispatchCompute")
            .add("glClearBufferSubData")
            .add("glBindVertexBuffer")
            .add("glVertexAttribFormat")
            .add("glVertexAttribIFormat")
            .add("glVertexAttribBinding");
        final var gl44 = RedirectMap.newMap()
            .add("glBufferStorage")
            .add("glClearTexImage");

        // Merge all GL version maps into the flat lookup
        glMethodRedirects.putAll(gl11);
        glMethodRedirects.putAll(gl12);
        glMethodRedirects.putAll(gl13);
        glMethodRedirects.putAll(gl14);
        glMethodRedirects.putAll(gl15);
        glMethodRedirects.putAll(gl20);
        glMethodRedirects.putAll(gl30);
        glMethodRedirects.putAll(gl31);
        glMethodRedirects.putAll(gl32);
        glMethodRedirects.putAll(gl33);
        glMethodRedirects.putAll(gl42);
        glMethodRedirects.putAll(gl43);
        glMethodRedirects.putAll(gl44);

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
            .add("glGetAttribLocationARB", "glGetAttribLocation")
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
        methodRedirects.put(ARBInstancedArrays, RedirectMap.newMap()
            .add("glVertexAttribDivisorARB")
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

        // GLU
        methodRedirects.put(Project, RedirectMap.newMap()
            .add("gluPerspective")
            .add("gluLookAt")
            .add("gluPickMatrix")
        );
        methodRedirects.put(GLU, RedirectMap.newMap()
            .add("gluPerspective")
            .add("gluLookAt")
            .add("gluOrtho2D")
            .add("gluPickMatrix")
        );

        // Quadric type replacements
        typeRedirects.put("org/lwjgl/util/glu/Sphere", "com/gtnewhorizons/angelica/glsm/compat/lwjgl/AngelicaSphere");
        typeRedirects.put("org/lwjgl/util/glu/Cylinder", "com/gtnewhorizons/angelica/glsm/compat/lwjgl/AngelicaCylinder");
        typeRedirects.put("org/lwjgl/util/glu/Disk", "com/gtnewhorizons/angelica/glsm/compat/lwjgl/AngelicaDisk");
        typeRedirects.put("org/lwjgl/util/glu/PartialDisk", "com/gtnewhorizons/angelica/glsm/compat/lwjgl/AngelicaPartialDisk");

        // Build constant pool scanner
        final List<String> stringsToSearch = new ArrayList<>(32);
        stringsToSearch.add(GL_PREFIX);
        stringsToSearch.addAll(typeRedirects.keySet());
        for (String key : methodRedirects.keySet()) {
            stringsToSearch.add(key);
        }
        cstPoolParser = new ClassConstantPoolParser(stringsToSearch.toArray(new String[0]));
    }

    /** Core exclusions that GLSM always requires. */
    public String[] getCoreExclusions() {
        return CORE_EXCLUSIONS.clone();
    }

    /** The internal-name prefix used to match GL version classes (e.g., {@code "org/lwjgl/opengl/GL"}). */
    public static String getGLPrefix() { return GL_PREFIX; }

    /** The internal name of the redirect target class. */
    public static String getTargetClassName() { return GLStateManager; }

    /** Method redirects for GL-prefix classes. Key: original method name, Value: GLStateManager method name. */
    public static Map<String, String> getGLPrefixMethodRedirects() {
        return Collections.unmodifiableMap(glMethodRedirects);
    }

    /** Method redirects for named (non-GL-prefix) classes. Key: internal class name, Value: per-method redirect map. */
    public static Map<String, Map<String, String>> getNamedClassMethodRedirects() {
        return Collections.unmodifiableMap(methodRedirects);
    }

    public boolean shouldTransform(byte[] basicClass) {
        return cstPoolParser.find(basicClass, true);
    }

    /** @return Was the class changed? */
    public boolean transformClassNode(String transformedName, ClassNode cn) {
        boolean changed = false;
        final boolean isOpenGlHelper = transformedName.equals("net.minecraft.client.renderer.OpenGlHelper");

        for (MethodNode mn : cn.methods) {
            if (isOpenGlHelper && (mn.name.equals("glBlendFunc") || mn.name.equals("func_148821_a"))) {
                continue;
            }
            boolean redirectInMethod = false;
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node instanceof TypeInsnNode tNode) {
                    // Redirect NEW and CHECKCAST for quadric classes
                    if (tNode.getOpcode() == Opcodes.NEW || tNode.getOpcode() == Opcodes.CHECKCAST) {
                        final String redirect = typeRedirects.get(tNode.desc);
                        if (redirect != null) {
                            if (LOG_SPAM) {
                                LOGGER.info("Redirecting {} in {} from {} to {}", tNode.getOpcode() == Opcodes.NEW ? "NEW" : "CHECKCAST", transformedName, tNode.desc, redirect);
                            }
                            tNode.desc = redirect;
                            changed = true;
                        }
                    }
                } else if (node instanceof MethodInsnNode mNode) {
                    if (mNode.desc.equals("(I)V") && mNode.owner.startsWith(GL_PREFIX) && (mNode.name.equals("glEnable") || mNode.name.equals("glDisable"))) {
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
                            final String shortOwner = mNode.owner.substring(mNode.owner.lastIndexOf("/") + 1);
                            if (name == null) {
                                LOGGER.info("Redirecting call in {} from {}.{}(I)V to GLStateManager.{}(I)V", transformedName, shortOwner, mNode.name, mNode.name);
                            } else {
                                LOGGER.info("Redirecting call in {} from {}.{}(I)V to GLStateManager.{}()V", transformedName, shortOwner, mNode.name, name);
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
                        final Map<String, String> redirects = mNode.owner.startsWith(GL_PREFIX) ? glMethodRedirects : methodRedirects.get(mNode.owner);
                        if (redirects != null) {
                            final String glsmName = redirects.get(mNode.name);
                            if (glsmName != null) {
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
                    }
                    // Redirect <init> calls for quadric classes
                    if (mNode.getOpcode() == Opcodes.INVOKESPECIAL && mNode.name.equals("<init>")) {
                        final String redirect = typeRedirects.get(mNode.owner);
                        if (redirect != null) {
                            if (LOG_SPAM) {
                                LOGGER.info("Redirecting <init> in {} from {} to {}", transformedName, mNode.owner, redirect);
                            }
                            mNode.owner = redirect;
                            changed = true;
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
