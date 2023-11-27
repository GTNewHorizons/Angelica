package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.coderbot.iris.IrisLogging;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GLStateManagerTransformer implements IClassTransformer {
    /*
     * Redirects a subset of GL11 calls to GLStateManager
     *  NOTE: Still need to verify compatibility with Mixins and Lwjgl3ify
     */
    private static final boolean ASSERT_MAIN_THREAD = Boolean.parseBoolean(System.getProperty("angelica.assertMainThread", "false"));
    private static final String GLStateTracker = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL13 = "org/lwjgl/opengl/GL13";
    private static final String GL14 = "org/lwjgl/opengl/GL14";
    private static final String EXTBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";
    private static final String ARBMultiTexture = "org/lwjgl/opengl/ARBMultitexture";

    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(GL11, GL13, GL14, EXTBlendFunc, ARBMultiTexture);

    private static final Map<String, Set<String>> EnabledRedirects = ImmutableMap.of(
        GL11, Sets.newHashSet("glBindTexture", "glTexImage2D", "glDeleteTextures", "glEnable", "glDisable", "glDepthFunc", "glDepthMask",
            "glColorMask", "glAlphaFunc", "glDrawArrays", "glColor3f", "glColor4f", "glShadeModel", "glFog", "glFogi", "glFogf", "glClearColor")
        , GL13, Sets.newHashSet("glActiveTexture")
        , GL14, Sets.newHashSet("glBlendFuncSeparate")
        , EXTBlendFunc, Sets.newHashSet("glBlendFuncSeparate")
        , ARBMultiTexture, Sets.newHashSet("glActiveTextureARB")
    );

    public static final List<String> TransformerExclusions = Arrays.asList(
        "org.lwjgl",
        "com.gtnewhorizons.angelica.glsm.",
        "com.gtnewhorizons.angelica.transform",
        "me.eigenraven.lwjgl3ify",
        "cpw.mods.fml.client.SplashProgress"
    );
    public static int remaps = 0, calls = 0;

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
                            AngelicaTweaker.LOGGER.info("Redirecting call in {} from {}.{}{} to GLStateManager.{}{}", className, shortOwner, mNode.name, mNode.desc, mNode.name, mNode.desc);
                        }
                        ((MethodInsnNode) node).owner = GLStateTracker;
                        changed = true;
                        redirectInMethod = true;
                        remaps++;
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
