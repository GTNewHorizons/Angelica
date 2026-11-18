package com.gtnewhorizons.angelica.glsm.redirect;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.config.SystemProperties.UnmappedGLMode;
import com.gtnewhorizons.angelica.glsm.testutil.GlsmClasses;
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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLSMRedirectorTest {

    private static final String GLSM = "com/gtnewhorizons/angelica/glsm/GLStateManager";

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
        assertEquals(GLSMRedirector.getTargetOwner(expectedName), result.getOwner());
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
        assertEquals(GLSMRedirector.getTargetOwner(expectedName), firstCall(cn).owner);
        assertEquals(expectedName, firstCall(cn).name);
        assertTrue(unmappedSeen().isEmpty(), owner + "." + name + " should not be recorded");
        return firstCall(cn);
    }

    @Test
    void ownerIndexAndSearchStrings() {
        assertFalse(GLSMRedirector.getNamedClassMethodRedirects().containsKey(GLSM));
        assertTrue(Arrays.asList(new GLSMRedirector().getConstantPoolSearchStrings()).contains(GLSM));
        assertEquals("com/gtnewhorizons/angelica/glsm/GLSMMatrices", GLSMRedirector.getTargetOwner("glPushMatrix"));
    }

    @Test
    void ownerTableMatchesGlsmClasses() {
        final Map<String, List<String>> methods = new TreeMap<>();
        final Map<String, List<String>> fields = new TreeMap<>();
        for (String type : GlsmClasses.packageClasses()) {
            if (type.equals(GLSM)) continue;
            final ClassNode node = GlsmClasses.load(type);
            final List<String> m = node.methods.stream().filter(x -> isPublicStatic(x.access)).map(x -> x.name).distinct().sorted().toList();
            final List<String> f = node.fields.stream().filter(x -> isPublicStatic(x.access)).map(x -> x.name).sorted().toList();
            if (!m.isEmpty()) methods.put(type, m);
            if (!f.isEmpty()) fields.put(type, f);
        }
        final String expected = renderTable("METHODS", methods) + "\n" + renderTable("FIELDS", fields);
        final String actual = renderTable("METHODS", rows(GLSMOwners.METHODS)) + "\n" + renderTable("FIELDS", rows(GLSMOwners.FIELDS));
        assertEquals(expected, actual, "GLSMOwners is out of date. Replace its tables with:\n" + expected);
    }

    @Test
    void everyRedirectTargetResolves() {
        final List<String> missing = new ArrayList<>();
        for (String target : GLSMRedirector.redirectTargets()) {
            final String owner = GLSMRedirector.getTargetOwner(target);
            if (GlsmClasses.load(owner).methods.stream().noneMatch(m -> m.name.equals(target) && isPublicStatic(m.access))) missing.add(owner + "." + target);
        }
        assertTrue(missing.isEmpty(), "Redirect targets not declared: " + missing);
        for (String name : GLSMRedirector.CORE_REWRITE_TARGETS) {
            assertEquals(GLSM, GLSMRedirector.getTargetOwner(name), name);
        }
    }

    private static boolean isPublicStatic(int access) {
        return (access & Opcodes.ACC_PUBLIC) != 0 && (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_SYNTHETIC) == 0;
    }

    private static Map<String, List<String>> rows(String[][] table) {
        final Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String[] row : table) rows.put(row[0], Arrays.asList(row).subList(1, row.length));
        return rows;
    }

    private static String renderTable(String name, Map<String, List<String>> rows) {
        if (rows.isEmpty()) return "    static final String[][] " + name + " = {};\n";
        final StringBuilder out = new StringBuilder("    static final String[][] " + name + " = {\n");
        for (Map.Entry<String, List<String>> row : rows.entrySet()) {
            out.append("        {\"").append(row.getKey()).append("\",\n");
            StringBuilder line = new StringBuilder("            ");
            final List<String> names = row.getValue();
            for (int i = 0; i < names.size(); i++) {
                final String token = "\"" + names.get(i) + "\"" + (i == names.size() - 1 ? "}," : ",");
                if (line.length() > 12 && line.length() + 1 + token.length() > 120) {
                    out.append(line).append('\n');
                    line = new StringBuilder("            ");
                }
                if (line.length() > 12) line.append(' ');
                line.append(token);
            }
            out.append(line).append('\n');
        }
        return out.append("    };\n").toString();
    }

    @Test
    void legacyOwnerIsRewrittenToItsGroup() {
        final String fogStateDesc = "()Lcom/gtnewhorizons/angelica/glsm/stacks/FogStateStack;";
        final ClassNode moved = makeClassWithMethodCall(GLSM, "getFogState", fogStateDesc);
        assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", moved));
        assertEquals("com/gtnewhorizons/angelica/glsm/GLSMFog", firstCall(moved).owner);

        final ClassNode core = makeClassWithMethodCall(GLSM, "ctx", "()Lcom/gtnewhorizons/angelica/glsm/GLContextState;");
        assertFalse(new GLSMRedirector().transformClassNode("com.example.TestClass", core));
        assertEquals(GLSM, firstCall(core).owner);

        final ClassNode field = makeClassWithMethodCall("java/lang/Object", "hashCode", "()I");
        final FieldInsnNode read = new FieldInsnNode(Opcodes.GETSTATIC, GLSM, "DEFAULT_DRAW_BUFFER", "I");
        field.methods.get(0).instructions.insert(read);
        assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", field));
        assertEquals("com/gtnewhorizons/angelica/glsm/GLSMFramebuffer", read.owner);

        final ClassNode handle = makeClassWithInvokeDynamic(new Handle(Opcodes.H_INVOKESTATIC, GLSM, "getFogState", fogStateDesc, false));
        assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", handle));
        assertEquals("com/gtnewhorizons/angelica/glsm/GLSMFog", getResultHandle(handle).getOwner());
    }

    private static boolean glsmDeclares(String name, String desc) throws ClassNotFoundException {
        final Class<?> glsm = Class.forName(GLSMRedirector.getTargetOwner(name).replace('/', '.'), false, GLSMRedirectorTest.class.getClassLoader());
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
        "org/lwjgl/opengl/EXTFramebufferObject | glFramebufferTexture3DEXT | (IIIIII)V | glFramebufferTexture3D",
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
        "org/lwjgl/opengl/ARBGeometryShader4 | glFramebufferTextureFaceARB | (IIIII)V | glFramebufferTextureFace",
        "org/lwjgl/opengl/EXTGeometryShader4 | glFramebufferTextureEXT | (IIII)V | glFramebufferTexture",
        "org/lwjgl/opengl/EXTTextureArray | glFramebufferTextureLayerEXT | (IIIII)V | glFramebufferTextureLayer",
        "org/lwjgl/opengl/EXTShaderImageLoadStore | glBindImageTextureEXT | (IIIZIII)V | glBindImageTexture",
        "org/lwjgl/opengl/EXTFramebufferObject | glDeleteFramebuffersEXT | (Ljava/nio/IntBuffer;)V | glDeleteFramebuffers",
    })
    void aliasIsRedirected(String owner, String name, String desc, String expectedName) throws ClassNotFoundException {
        assertEquals(desc, assertRedirected(owner, name, desc, expectedName).desc);
        assertTrue(glsmDeclares(expectedName, desc), () -> GLSMRedirector.getTargetOwner(expectedName) + " lacks " + expectedName + desc);
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
        "org/lwjgl/opengl/GL30 | glFramebufferTexture1D | (IIIII)V",
        "org/lwjgl/opengl/GL30 | glFramebufferTexture3D | (IIIIII)V",
        "org/lwjgl/opengl/GL30 | glFramebufferTextureLayer | (IIIII)V",
        "org/lwjgl/opengl/GL30C | glDeleteFramebuffers | ([I)V",
        "org/lwjgl/opengl/GL11C | glDeleteTextures | ([I)V",
        "org/lwjgl/opengl/GL11C | glGenTextures | ([I)V",
        "org/lwjgl/opengl/GL44 | glBindTextures | (IILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL44C | glBindTextures | (ILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL44 | glBindTextures | (I[I)V",
        "org/lwjgl/opengl/GL44 | glBindImageTextures | (IILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL44C | glBindImageTextures | (ILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL44 | glBindImageTextures | (I[I)V",
        "org/lwjgl/opengl/GL45 | glBindTextureUnit | (II)V",
        "org/lwjgl/opengl/GL45 | glCreateTextures | (I)I",
        "org/lwjgl/opengl/GL45C | glCreateTextures | (ILjava/nio/IntBuffer;)V",
        "org/lwjgl/opengl/GL45C | glCreateTextures | (I[I)V",
        "org/lwjgl/opengl/GL45 | glNamedFramebufferTexture | (IIII)V",
        "org/lwjgl/opengl/GL45 | glNamedFramebufferTextureLayer | (IIIII)V",
        "org/lwjgl/opengl/ARBDirectStateAccess | glNamedFramebufferRenderbuffer | (IIII)V",
        "org/lwjgl/opengl/EXTDirectStateAccess | glNamedFramebufferTexture1DEXT | (IIIII)V",
        "org/lwjgl/opengl/EXTDirectStateAccess | glNamedFramebufferTexture2DEXT | (IIIII)V",
        "org/lwjgl/opengl/EXTDirectStateAccess | glNamedFramebufferTexture3DEXT | (IIIIII)V",
        "org/lwjgl/opengl/EXTDirectStateAccess | glNamedFramebufferTextureEXT | (IIII)V",
        "org/lwjgl/opengl/EXTDirectStateAccess | glNamedFramebufferTextureFaceEXT | (IIIII)V",
        "org/lwjgl/opengl/EXTDirectStateAccess | glBindMultiTextureEXT | (III)V",
        "org/lwjgl/opengl/ARBShaderImageLoadStore | glBindImageTexture | (IIIZIII)V",
        "org/lwjgl/opengl/GL30 | glEnablei | (II)V",
        "org/lwjgl/opengl/GL30 | glDisablei | (II)V",
        "org/lwjgl/opengl/GL33C | glEnablei | (II)V",
        "org/lwjgl/opengl/GL33C | glDisablei | (II)V",
        "org/lwjgl/opengl/GL43 | glCopyImageSubData | (IIIIIIIIIIIIIII)V",
    })
    void redirectsToADeclaredGlsmTarget(String owner, String name, String desc) throws ClassNotFoundException {
        for (boolean aware : new boolean[]{false, true}) {
            final String call = owner + "." + name + desc + (aware ? " aware" : "");
            final ClassNode cn = makeClassWithMethodCall(owner, name, desc);
            assertTrue(new GLSMRedirector().transformClassNode("com.example.TestClass", cn, aware), call);
            final MethodInsnNode target = firstCall(cn);
            assertEquals(GLSMRedirector.getTargetOwner(name), target.owner, call);
            assertEquals(name, target.name, call);
            assertEquals(desc, target.desc, call);
            assertTrue(glsmDeclares(target.name, target.desc), () -> target.owner + " lacks the target of " + call);
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

        assertEquals(AWARE_SHIM, firstCall(cn).owner, "GLSM cannot carry an org.lwjgl-typed descriptor for an aware caller");
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
