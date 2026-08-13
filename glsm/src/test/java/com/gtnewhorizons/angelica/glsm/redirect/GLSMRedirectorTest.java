package com.gtnewhorizons.angelica.glsm.redirect;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.config.SystemProperties.UnmappedGLMode;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLSMRedirectorTest {

    private static final String GLSM = GLSMRedirector.getTargetClassName();

    @SuppressWarnings("unchecked")
    private static Set<String> seenSet(String field) {
        try {
            final Field f = GLSMRedirector.class.getDeclaredField(field);
            f.setAccessible(true);
            return (Set<String>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> unmappedSeen() { return seenSet("UNMAPPED_SEEN"); }
    private static Set<String> debugSeen() { return seenSet("DEBUG_SEEN"); }

    @BeforeEach
    void reset() {
        unmappedSeen().clear();
        debugSeen().clear();
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.WARN);
    }

    private static ClassNode makeClassWithMethodCall(String owner, String name, String desc) {
        final ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/TestClass";
        cn.superName = "java/lang/Object";
        cn.methods = new ArrayList<>();

        final MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        return cn;
    }

    private static MethodInsnNode firstCall(ClassNode cn) {
        for (AbstractInsnNode node : cn.methods.get(0).instructions.toArray()) {
            if (node instanceof MethodInsnNode) return (MethodInsnNode) node;
        }
        return null;
    }

    private static final Handle LAMBDA_METAFACTORY = new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;",
            false);

    private static ClassNode makeClassWithInvokeDynamic(Handle implHandle) {
        final ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/TestClass";
        cn.superName = "java/lang/Object";
        cn.methods = new ArrayList<>();

        final MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
        mn.instructions.add(new InvokeDynamicInsnNode(
                "run",
                "()Ljava/lang/Runnable;",
                LAMBDA_METAFACTORY,
                Type.getType("()V"),
                implHandle,
                Type.getType("()V")));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        return cn;
    }

    private static Handle getResultHandle(ClassNode cn) {
        final InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) cn.methods.get(0).instructions.get(0);
        return (Handle) dyn.bsmArgs[1];
    }

    @Test
    void invokeDynamicGL11HandleIsRedirected() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        final Handle result = getResultHandle(cn);
        assertEquals(GLSM, result.getOwner());
        assertEquals("glPopMatrix", result.getName());
        assertEquals("()V", result.getDesc());
        assertEquals(Opcodes.H_INVOKESTATIC, result.getTag());
    }

    @Test
    void invokeDynamicNamedClassHandleIsRedirected() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/EXTBlendFuncSeparate", "glBlendFuncSeparateEXT", "(IIII)V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        final Handle result = getResultHandle(cn);
        assertEquals(GLSM, result.getOwner());
        assertEquals("tryBlendFuncSeparate", result.getName());
        assertEquals("(IIII)V", result.getDesc());
    }

    @Test
    void invokeDynamicNonGLHandleIsNotTouched() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertFalse(changed);
        final Handle result = getResultHandle(cn);
        assertEquals("java/lang/System", result.getOwner());
        assertEquals("currentTimeMillis", result.getName());
    }

    @Test
    void invokeDynamicUnknownGLMethodIsNotTouched() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11", "glNonexistent", "()V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertFalse(changed);
        final Handle result = getResultHandle(cn);
        assertEquals("org/lwjgl/opengl/GL11", result.getOwner());
        assertTrue(unmappedSeen().contains("org/lwjgl/opengl/GL11.glNonexistent()V"));
    }

    @Test
    void unmappedMethodCallIsRecordedAndNotRedirected() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/ARBSparseTexture", "glTexPageCommitmentARB", "()V");

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertFalse(changed);
        assertEquals("org/lwjgl/opengl/ARBSparseTexture", firstCall(cn).owner);
        assertTrue(unmappedSeen().contains("org/lwjgl/opengl/ARBSparseTexture.glTexPageCommitmentARB()V"));
    }

    @Test
    void debugLabelIsRedirectedNotReported() {
        assertRedirected("org/lwjgl/opengl/KHRDebug", "glPushDebugGroup", "(IILjava/lang/CharSequence;)V", "glPushDebugGroup");
        assertRedirected("org/lwjgl/opengl/GL43C", "glObjectLabel", "(IILjava/lang/CharSequence;)V", "glObjectLabel");
        assertTrue(debugSeen().isEmpty());
    }

    @Test
    void nonAwareDebugCallbackIsRedirectedNotPassedThrough() {
        assertRedirected("org/lwjgl/opengl/GL43", "glDebugMessageCallback", "(Lorg/lwjgl/opengl/KHRDebugCallback;)V", "glDebugMessageCallback");
        assertTrue(debugSeen().isEmpty());
        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void debugOwnerAndWrongDescriptorArePassthrough() {
        makeAndTransform("org/lwjgl/opengl/AMDDebugOutput", "glDebugMessageEnableAMD", "(IILjava/nio/IntBuffer;Z)V");
        makeAndTransform("org/lwjgl/opengl/KHRDebug", "glObjectLabel", "(IIILjava/nio/ByteBuffer;)V");
        assertTrue(unmappedSeen().isEmpty());
        assertTrue(debugSeen().contains("org/lwjgl/opengl/AMDDebugOutput.glDebugMessageEnableAMD(IILjava/nio/IntBuffer;Z)V"));
        assertTrue(debugSeen().contains("org/lwjgl/opengl/KHRDebug.glObjectLabel(IIILjava/nio/ByteBuffer;)V"));
    }

    @Test
    void failModeDoesNotThrowOnDebugPassthrough() {
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.FAIL);
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL43C", "glDebugMessageCallback", "(Lorg/lwjgl/opengl/GLDebugMessageCallbackI;J)V");
        new GLSMRedirector().transformClassNode("com.example.TestClass", cn);
        assertTrue(debugSeen().contains("org/lwjgl/opengl/GL43C.glDebugMessageCallback(Lorg/lwjgl/opengl/GLDebugMessageCallbackI;J)V"));
    }

    private static void makeAndTransform(String owner, String name, String desc) {
        new GLSMRedirector().transformClassNode("com.example.TestClass", makeClassWithMethodCall(owner, name, desc));
    }

    @Test
    void nonGlNamedCallIsNotRecorded() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/DisplayMode", "<init>", "(II)V");

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void failModeThrowsOnUnmapped() {
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.FAIL);
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/ARBSparseTexture", "glTexPageCommitmentARB", "()V");

        assertThrows(IllegalStateException.class, () -> new GLSMRedirector().transformClassNode("com.example.TestClass", cn));
    }

    @Test
    void offModeSuppressesRecording() {
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.OFF);
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/ARBSparseTexture", "glTexPageCommitmentARB", "()V");

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void newlyMappedFramebufferAliasIsRedirected() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/EXTFramebufferObject", "glBindFramebufferEXT", "(II)V");

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        assertEquals(GLSM, firstCall(cn).owner);
        assertEquals("glBindFramebuffer", firstCall(cn).name);
        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void newlyMappedReadPixelsIsRedirected() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL11", "glReadPixels", "(IIIIIILjava/nio/IntBuffer;)V");

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        assertEquals(GLSM, firstCall(cn).owner);
        assertEquals("glReadPixels", firstCall(cn).name);
        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void newlyMappedOcclusionQueryAliasIsRedirected() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/ARBOcclusionQuery", "glGenQueriesARB", "(Ljava/nio/IntBuffer;)V");

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        assertEquals(GLSM, firstCall(cn).owner);
        assertEquals("glGenQueries", firstCall(cn).name);
        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void indexedGettersHaveRealTargets() throws Exception {
        final Class<?> glsm = Class.forName(GLSM.replace('/', '.'), false, getClass().getClassLoader());
        assertDoesNotThrow(() -> glsm.getDeclaredMethod("glGetInteger", int.class, int.class), "LWJGL2 GL30.glGetInteger(pname, index) redirects here");
        assertDoesNotThrow(() -> glsm.getDeclaredMethod("glGetIntegeri", int.class, int.class), "LWJGL3 GL31C.glGetIntegeri(pname, index) redirects here");
        assertDoesNotThrow(() -> glsm.getDeclaredMethod("glGetIntegeri_v", int.class, int.class, java.nio.IntBuffer.class));
        assertDoesNotThrow(() -> glsm.getDeclaredMethod("glGetBooleani_v", int.class, int.class, java.nio.ByteBuffer.class));
    }

    @Test
    void indexedGettersAreRedirected() {
        assertRedirected("org/lwjgl/opengl/GL30", "glGetInteger", "(II)I", "glGetInteger");
        assertRedirected("org/lwjgl/opengl/GL31C", "glGetIntegeri", "(II)I", "glGetIntegeri");
        assertRedirected("org/lwjgl/opengl/GL30", "glGetIntegeri_v", "(IILjava/nio/IntBuffer;)V", "glGetIntegeri_v");
        assertRedirected("org/lwjgl/opengl/GL30", "glGetBooleani_v", "(IILjava/nio/ByteBuffer;)V", "glGetBooleani_v");
    }

    private void assertRedirected(String owner, String name, String desc, String expectedName) {
        final ClassNode cn = makeClassWithMethodCall(owner, name, desc);
        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);
        assertTrue(changed, owner + "." + name + " should redirect");
        assertEquals(GLSM, firstCall(cn).owner);
        assertEquals(expectedName, firstCall(cn).name);
        assertTrue(unmappedSeen().isEmpty(), owner + "." + name + " should not be recorded");
    }

    @Test
    void descKeyedSyncRedirectsOnlyLongForm() {
        assertRedirected("org/lwjgl/opengl/GL32C", "glFenceSync", "(II)J", "glFenceSync");
        assertRedirected("org/lwjgl/opengl/GL32C", "glDeleteSync", "(J)V", "glDeleteSync");
        assertRedirected("org/lwjgl/opengl/GL15C", "nglMapBuffer", "(II)J", "nglMapBuffer");

        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL32", "glFenceSync", "(II)Lorg/lwjgl/opengl/GLSync;");
        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);
        assertFalse(changed);
        assertEquals("org/lwjgl/opengl/GL32", firstCall(cn).owner);
        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void glSyncObjectFormsAreNotReported() {
        final String[][] cases = {
            {"org/lwjgl/opengl/GL32", "glClientWaitSync", "(Lorg/lwjgl/opengl/GLSync;IJ)I"},
            {"org/lwjgl/opengl/ARBSync", "glWaitSync", "(Lorg/lwjgl/opengl/GLSync;IJ)V"},
            {"org/lwjgl/opengl/ARBSync", "glDeleteSync", "(Lorg/lwjgl/opengl/GLSync;)V"},
        };
        for (String[] c : cases) {
            final ClassNode cn = makeClassWithMethodCall(c[0], c[1], c[2]);
            assertFalse(new GLSMRedirector().transformClassNode("com.example.TestClass", cn));
        }
        assertTrue(unmappedSeen().isEmpty());
    }

    @Test
    void phase2NameKeyedGapsRedirect() {
        assertRedirected("org/lwjgl/opengl/GL33C", "glQueryCounter", "(II)V", "glQueryCounter");
        assertRedirected("org/lwjgl/opengl/GL33C", "glGetQueryObjectui64", "(II)J", "glGetQueryObjectui64");
        assertRedirected("org/lwjgl/opengl/ARBTimerQuery", "glGetQueryObjectui64", "(II)J", "glGetQueryObjectui64");
        assertRedirected("org/lwjgl/opengl/EXTTimerQuery", "glGetQueryObjectuEXT", "(II)J", "glGetQueryObjectui64");
        assertRedirected("org/lwjgl/opengl/EXTTimerQuery", "glGetQueryObjectui64EXT", "(II)J", "glGetQueryObjectui64");
        assertRedirected("org/lwjgl/opengl/GL30C", "glBindFragDataLocation", "(IILjava/lang/CharSequence;)V", "glBindFragDataLocation");
        assertRedirected("org/lwjgl/opengl/GL11C", "glGetIntegerv", "(I[I)V", "glGetInteger");
        assertRedirected("org/lwjgl/opengl/GL20C", "glUniform4fv", "(I[F)V", "glUniform4");
        assertRedirected("org/lwjgl/opengl/GL20C", "glUniform4fv", "(ILjava/nio/FloatBuffer;)V", "glUniform4");
    }

    @Test
    void phase1CeleritasGapsRedirect() {
        assertRedirected("org/lwjgl/opengl/EXTGpuShader4", "glVertexAttribIPointerEXT", "(IIIIJ)V", "glVertexAttribIPointer");
        assertRedirected("org/lwjgl/opengl/EXTGPUShader4", "glVertexAttribIPointerEXT", "(IIIIJ)V", "glVertexAttribIPointer");
        assertRedirected("org/lwjgl/opengl/ARBUniformBufferObject", "glGetUniformBlockIndex", "(ILjava/lang/CharSequence;)I", "glGetUniformBlockIndex");
        assertRedirected("org/lwjgl/opengl/GL30C", "glBindBufferBase", "(III)V", "glBindBufferBase");
        assertRedirected("org/lwjgl/opengl/GL31C", "glUniformBlockBinding", "(III)V", "glUniformBlockBinding");
        assertRedirected("org/lwjgl/opengl/GL32C", "glDrawElementsBaseVertex", "(IIIJI)V", "glDrawElementsBaseVertex");
        assertRedirected("org/lwjgl/opengl/GL43C", "glMultiDrawElementsIndirect", "(IIJII)V", "glMultiDrawElementsIndirect");
        assertRedirected("org/lwjgl/opengl/GL20C", "glUniform1fv", "(ILjava/nio/FloatBuffer;)V", "glUniform1");
        assertRedirected("org/lwjgl/opengl/GL11", "glLoadMatrixf", "(Ljava/nio/FloatBuffer;)V", "glLoadMatrix");
        assertRedirected("org/lwjgl/opengl/ARBVertexArrayObject", "glGenVertexArrays", "()I", "glGenVertexArrays");
        assertRedirected("org/lwjgl/opengl/APPLEVertexArrayObject", "glGenVertexArraysAPPLE", "()I", "glGenVertexArrays");
    }

    @Test
    void invokeDynamicGLCVariantIsRedirected() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11C", "glDrawArrays", "(III)V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        final Handle result = getResultHandle(cn);
        assertEquals(GLSM, result.getOwner());
        assertEquals("glDrawArrays", result.getName());
        assertEquals("(III)V", result.getDesc());
    }

    private static final String AWARE_SHIM = "com/gtnewhorizons/angelica/sdlgpu/compat/AwareGLDebugShim";
    private static final String CALLBACK_I = "Lorg/lwjgl/opengl/GLDebugMessageCallbackI;";

    @Test
    void awareCallerStillHasPlainGlCallsRedirected() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL11", "glPopMatrix", "()V");

        assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));

        assertEquals(GLSM, firstCall(cn).owner);
        assertEquals("glPopMatrix", firstCall(cn).name);
    }

    @Test
    void awareDebugMessageCallbackTargetsTheAwareShim() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL43", "glDebugMessageCallback", "(" + CALLBACK_I + "J)V");

        assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));

        assertEquals(AWARE_SHIM, firstCall(cn).owner, "GLStateManager cannot carry an org.lwjgl-typed descriptor for an aware caller");
        assertEquals("glDebugMessageCallback", firstCall(cn).name);
    }

    @Test
    void nonAwareDebugMessageCallbackTargetsGLStateManager() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/KHRDebug", "glDebugMessageCallback", "(Lorg/lwjgl/opengl/KHRDebugCallback;)V");

        assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, false));

        assertEquals(GLSM, firstCall(cn).owner);
    }

    @Test
    void awareGLUtilSetupDebugMessageCallbackIsRedirected() {
        for (String desc : new String[]{"()Lorg/lwjgl/system/Callback;", "(Ljava/io/PrintStream;)Lorg/lwjgl/system/Callback;"}) {
            final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GLUtil", "setupDebugMessageCallback", desc);

            assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true), desc);

            assertEquals(AWARE_SHIM, firstCall(cn).owner, desc);
        }
    }

    @Test
    void getDebugMessageLogReachesGLStateManager() {
        final String buffers = "Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;";
        for (String desc : new String[]{"(I" + buffers + ")I"}) {
            final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL43", "glGetDebugMessageLog", desc);

            assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, false), desc);

            assertEquals(GLSM, firstCall(cn).owner, desc);
            assertEquals(desc, firstCall(cn).desc, "descriptor must be preserved so the right overload is hit");
        }
    }

    @Test
    void awareUnroutableCallIsReported() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL32C", "glSomethingExotic", "(" + CALLBACK_I + ")V");

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true);

        assertTrue(unmappedSeen().stream().anyMatch(s -> s.startsWith("aware ") && s.contains("glSomethingExotic")), () -> "expected an aware-unroutable report, got " + unmappedSeen());
    }

    @Test
    void awareUnroutableCallFailsFastWhenRequested() {
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.STRICT);
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL32C", "glSomethingExotic", "(" + CALLBACK_I + ")V");

        assertThrows(IllegalStateException.class, () -> new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));
    }

    @Test
    void nonAwareCallerNeverTargetsTheAwareShim() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL43", "glDebugMessageCallback", "(" + CALLBACK_I + "J)V");

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn, false);

        assertFalse(AWARE_SHIM.equals(firstCall(cn).owner));
    }

    /** Crashed the game: it carries an LWJGL type but is only a ThreadLocal read plus a null check. */
    @Test
    void awareGetCapabilitiesIsNeitherRedirectedNorReported() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL", "getCapabilities", "()Lorg/lwjgl/opengl/GLCapabilities;");

        assertFalse(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));

        assertEquals("org/lwjgl/opengl/GL", firstCall(cn).owner);
        assertTrue(unmappedSeen().isEmpty(), () -> "must not be reported: " + unmappedSeen());
    }

    @Test
    void awareGetCapabilitiesDoesNotFailFastEvenInStrictMode() {
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.STRICT);
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL", "getCapabilities", "()Lorg/lwjgl/opengl/GLCapabilities;");

        assertDoesNotThrow(() -> new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));
    }

    @Test
    void awareSafeLwjglTypedMembersAreNotReported() {
        final String[][] safe = {
            {"org/lwjgl/opengl/GL", "setCapabilities", "(Lorg/lwjgl/opengl/GLCapabilities;)V"},
            {"org/lwjgl/opengl/GL", "getFunctionProvider", "()Lorg/lwjgl/system/FunctionProvider;"},
            {"org/lwjgl/opengl/GLCapabilities", "getAddressBuffer", "()Lorg/lwjgl/PointerBuffer;"},
            {"org/lwjgl/opengl/GLDebugMessageCallback", "create", "(Lorg/lwjgl/opengl/GLDebugMessageCallbackI;)Lorg/lwjgl/opengl/GLDebugMessageCallback;"},
        };
        for (String[] m : safe) {
            unmappedSeen().clear();
            final ClassNode cn = makeClassWithMethodCall(m[0], m[1], m[2]);
            new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true);
            assertTrue(unmappedSeen().isEmpty(), () -> m[0] + "." + m[1] + " must not be reported");
        }
    }

    @Test
    void awareDispatchingMembersOnTheGLClassAreStillReported() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL", "createCapabilities", "(Z)Lorg/lwjgl/opengl/GLCapabilities;");

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true);

        assertTrue(unmappedSeen().stream().anyMatch(s -> s.startsWith("aware ") && s.contains("createCapabilities")), () -> "expected a report, got " + unmappedSeen());
    }

    @Test
    void awareUnroutableFailFastIsNotOneShot() {
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.STRICT);
        for (int i = 0; i < 2; i++) {
            final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL32C", "glSomethingExotic", "(" + CALLBACK_I + ")V");
            final int attempt = i;
            assertThrows(IllegalStateException.class, () -> new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true), () -> "attempt " + attempt + " must still fail");
        }
    }

    @Test
    void transformedClassProducesValidBytecode() {
        final Handle impl = new Handle(Opcodes.H_INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false);
        final ClassNode cn = makeClassWithInvokeDynamic(impl);

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        assertDoesNotThrow(() -> {
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            assertTrue(bytes.length > 0);
        });
    }
}
