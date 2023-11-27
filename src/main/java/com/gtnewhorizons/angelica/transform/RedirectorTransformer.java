package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.coderbot.iris.IrisLogging;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This transformer redirects all Tessellator.instance field accesses to go through our TessellatorManager.
 * As well as redirect some GL calls to our custom GLStateManager
 */
public class RedirectorTransformer implements IClassTransformer {

    private static final boolean ASSERT_MAIN_THREAD = Boolean.parseBoolean(System.getProperty("angelica.assertMainThread", "false"));
    private static final boolean DUMP_CLASSES = Boolean.parseBoolean(System.getProperty("angelica.dumpClass", "false"));
    private static final String GLStateTracker = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL13 = "org/lwjgl/opengl/GL13";
    private static final String GL14 = "org/lwjgl/opengl/GL14";
    private static final String EXTBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";
    private static final String ARBMultiTexture = "org/lwjgl/opengl/ARBMultitexture";
    private static final String TessellatorClass = "net/minecraft/client/renderer/Tessellator";
    private static final String MinecraftClient = "net.minecraft.client";
    private static final Set<String> ExcludedMinecraftMainThreadChecks = ImmutableSet.of(
        "startGame", "func_71384_a",
        "initializeTextures", "func_77474_a"
    );

    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(GL11, GL13, GL14, EXTBlendFunc, ARBMultiTexture, TessellatorClass);

    private static final Map<String, Set<String>> EnabledRedirects = ImmutableMap.of(
        GL11, Sets.newHashSet("glBindTexture", "glTexImage2D", "glDeleteTextures", "glEnable", "glDisable", "glDepthFunc", "glDepthMask",
            "glColorMask", "glAlphaFunc", "glDrawArrays", "glColor3f", "glColor4f", "glShadeModel", "glFog", "glFogi", "glFogf", "glClearColor")
        , GL13, Sets.newHashSet("glActiveTexture")
        , GL14, Sets.newHashSet("glBlendFuncSeparate")
        , EXTBlendFunc, Sets.newHashSet("glBlendFuncSeparate")
        , ARBMultiTexture, Sets.newHashSet("glActiveTextureARB")
    );

    private static final List<String> TransformerExclusions = Arrays.asList(
        "org.lwjgl",
        "com.gtnewhorizons.angelica.glsm.",
        "com.gtnewhorizons.angelica.transform",
        "me.eigenraven.lwjgl3ify",
        "cpw.mods.fml.client.SplashProgress"
    );
    private static int remaps = 0;

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        // Ignore classes that are excluded from transformation - Doesn't fully work without the
        // TransformerExclusions due to some nested classes
        for (String exclusion : TransformerExclusions) {
            if (className.startsWith(exclusion)) {
                return basicClass;
            }
        }

        if (!cstPoolParser.find(basicClass)) {
            return basicClass;
        }

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            boolean redirectInMethod = false;
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node instanceof MethodInsnNode mNode) {
                    final Set<String> redirects = EnabledRedirects.get(mNode.owner);
                    if (redirects != null && redirects.contains(mNode.name)) {
                        if (IrisLogging.ENABLE_SPAM) {
                            final String shortOwner = mNode.owner.substring(mNode.owner.lastIndexOf("/") + 1);
                            AngelicaTweaker.LOGGER.info("Redirecting call in {} from {}.{}{} to GLStateManager.{}{}", transformedName, shortOwner, mNode.name, mNode.desc, mNode.name, mNode.desc);
                        }
                        ((MethodInsnNode) node).owner = GLStateTracker;
                        changed = true;
                        redirectInMethod = true;
                        remaps++;
                    }
                } else if (node.getOpcode() == Opcodes.GETSTATIC && node instanceof FieldInsnNode fNode) {
                    if ((fNode.name.equals("field_78398_a") || fNode.name.equals("instance")) && fNode.owner.equals(TessellatorClass)) {
                        if (IrisLogging.ENABLE_SPAM) {
                            AngelicaTweaker.LOGGER.info("Redirecting Tessellator.instance field in {} to TessellatorManager.get()", transformedName);
                        }
                        mn.instructions.set(node, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/gtnewhorizons/angelica/glsm/TessellatorManager", "get", "()Lnet/minecraft/client/renderer/Tessellator;", false));
                        changed = true;
                    }
                }
            }
            if (ASSERT_MAIN_THREAD && redirectInMethod && !(transformedName.startsWith(MinecraftClient) && ExcludedMinecraftMainThreadChecks.contains(mn.name))) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, GLStateTracker, "assertMainThread", "()V", false));
            }
        }

        if (changed) {
            ClassWriter cw = new ClassWriter(0);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            saveTransformedClass(bytes, transformedName);
            return bytes;
        }
        return basicClass;
    }

    private File outputDir = null;

    private void saveTransformedClass(final byte[] data, final String transformedName) {
        if (!DUMP_CLASSES) {
            return;
        }
        if (outputDir == null) {
            outputDir = new File(Launch.minecraftHome, "ASM_REDIRECTOR");
            try {
                FileUtils.deleteDirectory(outputDir);
            } catch (IOException ignored) {}
            if (!outputDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outputDir.mkdirs();
            }
        }
        final String fileName = transformedName.replace('.', File.separatorChar);
        final File classFile = new File(outputDir, fileName + ".class");
        final File outDir = classFile.getParentFile();
        if (!outDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
        }
        if (classFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            classFile.delete();
        }
        try (final OutputStream output = Files.newOutputStream(classFile.toPath())) {
            output.write(data);
        } catch (IOException e) {
            AngelicaTweaker.LOGGER.error("Could not save transformed class (byte[]) " + transformedName, e);
        }
    }

}