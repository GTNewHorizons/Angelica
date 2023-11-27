package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.coderbot.iris.IrisLogging;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This transformer redirects all Tessellator.instance field accesses to go through our TessellatorManager.
 * As well as redirect some GL calls to our custom GLStateManager
 * NOTE: Still need to verify compatibility with Mixins and Lwjgl3ify
 */
public class RedirectorTransformer implements IClassTransformer {

    private static final boolean ASSERT_MAIN_THREAD = Boolean.parseBoolean(System.getProperty("angelica.assertMainThread", "false"));
    private static final String GLStateTracker = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL13 = "org/lwjgl/opengl/GL13";
    private static final String GL14 = "org/lwjgl/opengl/GL14";
    private static final String EXTBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";
    private static final String ARBMultiTexture = "org/lwjgl/opengl/ARBMultitexture";
    private static final String tessellatorClass = "net/minecraft/client/renderer/Tessellator";

    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(GL11, GL13, GL14, EXTBlendFunc, ARBMultiTexture, tessellatorClass);

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
                    if ((fNode.name.equals("field_78398_a") || fNode.name.equals("instance")) && fNode.owner.equals("net/minecraft/client/renderer/Tessellator")) {
                        if (IrisLogging.ENABLE_SPAM) {
                            AngelicaTweaker.LOGGER.info("Redirecting Tessellator.instance field in {} to TessellatorManager.get()", transformedName);
                        }
                        mn.instructions.set(node, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/gtnewhorizons/angelica/glsm/TessellatorManager", "get", "()Lnet/minecraft/client/renderer/Tessellator;", false));
                        changed = true;
                    }
                }
            }
            if (ASSERT_MAIN_THREAD && redirectInMethod) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, GLStateTracker, "assertMainThread", "()V", false));
            }
        }

        if (changed) {
            ClassWriter cw = new ClassWriter(0);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }

}
