package com.gtnewhorizons.angelica.glsm.redirect;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.config.SystemProperties.UnmappedGLMode;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLSMRedirectorTest {

    private static final String GLSM = GLSMRedirector.getTargetClassName();

    private static Set<String> unmappedSeen() { return Reflect.getStatic(GLSMRedirector.class, "UNMAPPED_SEEN"); }
    private static Set<String> debugSeen() { return Reflect.getStatic(GLSMRedirector.class, "DEBUG_SEEN"); }

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

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "GL11, org/lwjgl/opengl/GL11, glPopMatrix, ()V, glPopMatrix",
        "extension class with a renamed target, org/lwjgl/opengl/EXTBlendFuncSeparate, glBlendFuncSeparateEXT, (IIII)V, tryBlendFuncSeparate",
        "GLxxC variant, org/lwjgl/opengl/GL11C, glDrawArrays, (III)V, glDrawArrays"
    })
    void invokeDynamicHandleIsRedirected(String name, String owner, String method, String desc, String expectedName) {
        final ClassNode cn = makeClassWithInvokeDynamic(new Handle(Opcodes.H_INVOKESTATIC, owner, method, desc, false));

        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);

        assertTrue(changed);
        final Handle result = getResultHandle(cn);
        assertEquals(GLSM, result.getOwner());
        assertEquals(expectedName, result.getName());
        assertEquals(desc, result.getDesc());
        assertEquals(Opcodes.H_INVOKESTATIC, result.getTag());
        assertDoesNotThrow(() -> cn.accept(new ClassWriter(ClassWriter.COMPUTE_MAXS)));
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

    private MethodInsnNode assertRedirected(String owner, String name, String desc, String expectedName) {
        final ClassNode cn = makeClassWithMethodCall(owner, name, desc);
        final boolean changed = new GLSMRedirector().transformClassNode("com.example.TestClass", cn);
        assertTrue(changed, owner + "." + name + " should redirect");
        assertEquals(GLSM, firstCall(cn).owner);
        assertEquals(expectedName, firstCall(cn).name);
        assertTrue(unmappedSeen().isEmpty(), owner + "." + name + " should not be recorded");
        return firstCall(cn);
    }

    private static boolean glsmDeclares(String name, String desc) throws ClassNotFoundException {
        final Class<?> glsm = Class.forName(GLSM.replace('/', '.'), false, GLSMRedirectorTest.class.getClassLoader());
        for (Method m : glsm.getDeclaredMethods()) {
            final int mods = m.getModifiers();
            if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || !m.getName().equals(name)) continue;
            if (Type.getMethodDescriptor(m).equals(desc)) return true;
        }
        return false;
    }

    @ParameterizedTest(name = "{0}.{1}{2}")
    @CsvSource(delimiter = '|', value = {
        "org/lwjgl/opengl/EXTFramebufferObject | glBindFramebufferEXT | (II)V | glBindFramebuffer",
        "org/lwjgl/opengl/ARBFramebufferObject | glGenerateMipmap | (I)V | glGenerateMipmap",
        "org/lwjgl/opengl/EXTFramebufferObject | glGenerateMipmapEXT | (I)V | glGenerateMipmap",
        "org/lwjgl/opengl/GL11 | glReadPixels | (IIIIIILjava/nio/IntBuffer;)V | glReadPixels",
        "org/lwjgl/opengl/ARBOcclusionQuery | glGenQueriesARB | (Ljava/nio/IntBuffer;)V | glGenQueries",
        "org/lwjgl/opengl/KHRDebug | glPushDebugGroup | (IILjava/lang/CharSequence;)V | glPushDebugGroup",
        "org/lwjgl/opengl/GL43C | glObjectLabel | (IILjava/lang/CharSequence;)V | glObjectLabel",
        "org/lwjgl/opengl/GL43 | glDebugMessageCallback | (Lorg/lwjgl/opengl/KHRDebugCallback;)V | glDebugMessageCallback",
        "org/lwjgl/opengl/KHRDebug | glDebugMessageCallback | (Lorg/lwjgl/opengl/KHRDebugCallback;)V | glDebugMessageCallback",
        "org/lwjgl/opengl/GL32C | glFenceSync | (II)J | glFenceSync",
        "org/lwjgl/opengl/GL32C | glDeleteSync | (J)V | glDeleteSync",
        "org/lwjgl/opengl/GL15C | nglMapBuffer | (II)J | nglMapBuffer",
        "org/lwjgl/opengl/GL33C | glQueryCounter | (II)V | glQueryCounter",
        "org/lwjgl/opengl/GL33C | glGetQueryObjectui64 | (II)J | glGetQueryObjectui64",
        "org/lwjgl/opengl/ARBTimerQuery | glGetQueryObjectui64 | (II)J | glGetQueryObjectui64",
        "org/lwjgl/opengl/EXTTimerQuery | glGetQueryObjectuEXT | (II)J | glGetQueryObjectui64",
        "org/lwjgl/opengl/EXTTimerQuery | glGetQueryObjectui64EXT | (II)J | glGetQueryObjectui64",
        "org/lwjgl/opengl/GL30C | glBindFragDataLocation | (IILjava/lang/CharSequence;)V | glBindFragDataLocation",
        "org/lwjgl/opengl/GL11C | glGetIntegerv | (I[I)V | glGetInteger",
        "org/lwjgl/opengl/GL20C | glUniform4fv | (I[F)V | glUniform4",
        "org/lwjgl/opengl/GL20C | glUniform4fv | (ILjava/nio/FloatBuffer;)V | glUniform4",
        "org/lwjgl/opengl/EXTGpuShader4 | glVertexAttribIPointerEXT | (IIIIJ)V | glVertexAttribIPointer",
        "org/lwjgl/opengl/EXTGPUShader4 | glVertexAttribIPointerEXT | (IIIIJ)V | glVertexAttribIPointer",
        "org/lwjgl/opengl/ARBUniformBufferObject | glGetUniformBlockIndex | (ILjava/lang/CharSequence;)I | glGetUniformBlockIndex",
        "org/lwjgl/opengl/GL30C | glBindBufferBase | (III)V | glBindBufferBase",
        "org/lwjgl/opengl/GL31C | glUniformBlockBinding | (III)V | glUniformBlockBinding",
        "org/lwjgl/opengl/GL32C | glDrawElementsBaseVertex | (IIIJI)V | glDrawElementsBaseVertex",
        "org/lwjgl/opengl/GL43C | glMultiDrawElementsIndirect | (IIJII)V | glMultiDrawElementsIndirect",
        "org/lwjgl/opengl/GL20C | glUniform1fv | (ILjava/nio/FloatBuffer;)V | glUniform1",
        "org/lwjgl/opengl/GL11 | glLoadMatrixf | (Ljava/nio/FloatBuffer;)V | glLoadMatrix",
        "org/lwjgl/opengl/ARBVertexArrayObject | glGenVertexArrays | ()I | glGenVertexArrays",
        "org/lwjgl/opengl/APPLEVertexArrayObject | glGenVertexArraysAPPLE | ()I | glGenVertexArrays",
    })
    void aliasIsRedirected(String owner, String name, String desc, String expectedName) {
        assertEquals(desc, assertRedirected(owner, name, desc, expectedName).desc);
        assertTrue(debugSeen().isEmpty());
    }

    @ParameterizedTest(name = "{0}.{1}{2}")
    @CsvSource(delimiter = '|', value = {
        "org/lwjgl/opengl/GL11 | glPopMatrix | ()V",
        "org/lwjgl/opengl/GL11 | glReadPixels | (IIIIIIJ)V",
        "org/lwjgl/opengl/GL11C | glReadPixels | (IIIIIIJ)V",
        "org/lwjgl/opengl/GL11 | glGetTexImage | (IIIIJ)V",
        "org/lwjgl/opengl/Util | checkGLError | ()V",
        "org/lwjgl/opengl/GL30 | glGetInteger | (II)I",
        "org/lwjgl/opengl/GL31C | glGetIntegeri | (II)I",
        "org/lwjgl/opengl/GL30 | glGetIntegeri_v | (IILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL30 | glGetBooleani_v | (IILjava/nio/ByteBuffer;)V",
        "org/lwjgl/opengl/GL33 | glGenSamplers | ()I",
        "org/lwjgl/opengl/GL33 | glGenSamplers | (Ljava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL33C | glGenSamplers | ([I)V",
        "org/lwjgl/opengl/GL33 | glGenSamplers | ([I)V",
        "org/lwjgl/opengl/GL33 | glDeleteSamplers | (I)V",
        "org/lwjgl/opengl/GL33 | glDeleteSamplers | (Ljava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL33C | glDeleteSamplers | ([I)V",
        "org/lwjgl/opengl/GL33 | glDeleteSamplers | ([I)V",
        "org/lwjgl/opengl/GL44 | glBindSamplers | (IILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL44C | glBindSamplers | (ILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL44 | glBindSamplers | (I[I)V",
        "org/lwjgl/opengl/ARBMultiBind | glBindSamplers | (IILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/ARBMultiBind | glBindSamplers | (ILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/ARBMultiBind | glBindSamplers | (I[I)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glGenSamplers | ()I",
        "org/lwjgl/opengl/ARBSamplerObjects | glGenSamplers | (Ljava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glGenSamplers | ([I)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glDeleteSamplers | (I)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glDeleteSamplers | (Ljava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glDeleteSamplers | ([I)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glIsSampler | (I)Z",
        "org/lwjgl/opengl/ARBSamplerObjects | glBindSampler | (II)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glSamplerParameteri | (III)V",
        "org/lwjgl/opengl/ARBSamplerObjects | glSamplerParameterf | (IIF)V",
        "org/lwjgl/opengl/GL43 | glGetDebugMessageLog | (ILjava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;)I",
    })
    void redirectsToADeclaredGlsmTarget(String owner, String name, String desc) throws ClassNotFoundException {
        for (boolean aware : new boolean[]{false, true}) {
            final String call = owner + "." + name + desc + (aware ? " aware" : "");
            final ClassNode cn = makeClassWithMethodCall(owner, name, desc);
            assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, aware), call);
            final MethodInsnNode target = firstCall(cn);
            assertEquals(GLSM, target.owner, call);
            assertEquals(name, target.name, call);
            assertEquals(desc, target.desc, call);
            assertTrue(glsmDeclares(target.name, target.desc), () -> "GLStateManager lacks the target of " + call);
        }
        assertTrue(unmappedSeen().isEmpty(), () -> "unexpected reports: " + unmappedSeen());
    }

    @Test
    void glSyncObjectFormsAreNotReported() {
        final String[][] cases = {
            {"org/lwjgl/opengl/GL32", "glFenceSync", "(II)Lorg/lwjgl/opengl/GLSync;"},
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

    private static final String AWARE_SHIM = "com/gtnewhorizons/angelica/sdlgpu/compat/AwareGLDebugShim";
    private static final String CALLBACK_I = "Lorg/lwjgl/opengl/GLDebugMessageCallbackI;";

    @ParameterizedTest(name = "{0}.{1}{2}")
    @CsvSource(delimiter = '|', value = {
        "org/lwjgl/opengl/GL43 | glDebugMessageCallback | (Lorg/lwjgl/opengl/GLDebugMessageCallbackI;J)V",
        "org/lwjgl/opengl/GLUtil | setupDebugMessageCallback | ()Lorg/lwjgl/system/Callback;",
        "org/lwjgl/opengl/GLUtil | setupDebugMessageCallback | (Ljava/io/PrintStream;)Lorg/lwjgl/system/Callback;",
    })
    void awareDebugCallbackSetupTargetsTheAwareShim(String owner, String name, String desc) {
        final ClassNode cn = makeClassWithMethodCall(owner, name, desc);

        assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));

        assertEquals(AWARE_SHIM, firstCall(cn).owner, "GLStateManager cannot carry an org.lwjgl-typed descriptor for an aware caller");
        assertEquals(name, firstCall(cn).name);
    }

    @ParameterizedTest(name = "{0}.{1}{2}")
    @CsvSource(delimiter = '|', value = {
        "org/lwjgl/opengl/GL | getCapabilities | ()Lorg/lwjgl/opengl/GLCapabilities;",
        "org/lwjgl/opengl/GL | setCapabilities | (Lorg/lwjgl/opengl/GLCapabilities;)V",
        "org/lwjgl/opengl/GL | getFunctionProvider | ()Lorg/lwjgl/system/FunctionProvider;",
        "org/lwjgl/opengl/GLCapabilities | getAddressBuffer | ()Lorg/lwjgl/PointerBuffer;",
        "org/lwjgl/opengl/GLDebugMessageCallback | create | (Lorg/lwjgl/opengl/GLDebugMessageCallbackI;)Lorg/lwjgl/opengl/GLDebugMessageCallback;",
    })
    void awareSafeLwjglTypedMembersAreNeitherRedirectedNorReported(String owner, String name, String desc) {
        final ClassNode cn = makeClassWithMethodCall(owner, name, desc);

        assertFalse(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));

        assertEquals(owner, firstCall(cn).owner);
        assertTrue(unmappedSeen().isEmpty(), () -> "must not be reported: " + unmappedSeen());
    }

    @Test
    void awareUnroutableCallIsReported() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL32C", "glSomethingExotic", "(" + CALLBACK_I + ")V");

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true);

        assertTrue(unmappedSeen().stream().anyMatch(s -> s.startsWith("aware ") && s.contains("glSomethingExotic")), () -> "expected an aware-unroutable report, got " + unmappedSeen());
    }

    @Test
    void nonAwareCallerNeverTargetsTheAwareShim() {
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL43", "glDebugMessageCallback", "(" + CALLBACK_I + "J)V");

        new GLSMRedirector().transformClassNode("com.example.TestClass", cn, false);

        assertFalse(AWARE_SHIM.equals(firstCall(cn).owner));
    }

    @Test
    void awareGetCapabilitiesDoesNotFailFastEvenInStrictMode() {
        Reflect.setStaticFinal(SystemProperties.class, "UNMAPPED_GL", UnmappedGLMode.class, UnmappedGLMode.STRICT);
        final ClassNode cn = makeClassWithMethodCall("org/lwjgl/opengl/GL", "getCapabilities", "()Lorg/lwjgl/opengl/GLCapabilities;");

        assertDoesNotThrow(() -> new GLSMRedirector().transformClassNode("com.example.TestClass", cn, true));
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
}
