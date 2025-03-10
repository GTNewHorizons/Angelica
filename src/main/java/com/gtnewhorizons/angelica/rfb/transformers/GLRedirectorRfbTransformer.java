package com.gtnewhorizons.angelica.rfb.transformers;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.gtnewhorizons.angelica.rfb.util.RedirectConfigLoader;
import com.gtnewhorizons.angelica.rfb.util.UnredirectedMethodLogger;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * RFB Transformer that redirects OpenGL method calls to GLStateManager.
 * Uses CSV configuration files for redirects.
 */
public class GLRedirectorRfbTransformer implements RfbClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("GLRedirectorRfbTransformer");

    // LWJGL class constants
    private static final String GL11 = "org/lwjgl/opengl/GL11";

    // Target class for redirection
    private static final String GLStateManager = "com/gtnewhorizons/angelica/glsm/GLStateManager";

    // Classes to exclude from transformation
    private static final ObjectArrayList<String> TRANSFORMER_EXCLUSIONS = new ObjectArrayList<>(Arrays.asList(
        "org.lwjgl.",
        "com.gtnewhorizons.angelica.glsm.",
        "com.gtnewhorizons.angelica.compat.lwjgl.",
        "com.gtnewhorizons.angelica.asm.",
        "com.gtnewhorizon.gtnhlib.",
        "it.unimi.dsi.fastutil."

    ));

    // Set of (mostly) LWJGL classes to check for redirections - now built dynamically from method_redirects.csv
    private static final Set<String> LWJGL_CLASSES = new ObjectOpenHashSet<>();

    // Redirects loaded from CSV files
    private final Map<String, Map<String, RedirectConfigLoader.MethodRedirect>> methodRedirects;
    private final Int2ObjectOpenHashMap<String> glCapRedirects;

    /**
     * Constructor loads redirect configurations
     */
    public GLRedirectorRfbTransformer() {
        methodRedirects = RedirectConfigLoader.loadMethodRedirects();
        glCapRedirects = RedirectConfigLoader.loadGlCapRedirects();

        // Build LWJGL_CLASSES set from method_redirects.csv
        LWJGL_CLASSES.addAll(methodRedirects.keySet());

        LOGGER.info("Loaded {} method redirect classes and {} GL capability redirects", methodRedirects.size(), glCapRedirects.size());
        LOGGER.info("LWJGL classes for redirection: {}", String.join(", ", LWJGL_CLASSES));

    }

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "gl-redirector";
    }

    @Override
    public @NotNull String @Nullable [] sortAfter() {
        return new String[] {"*", "mixin:mixin", "lwjgl3ify:redirect"};
    }

    @Override
    public @NotNull String @Nullable [] additionalExclusions() {
        return TRANSFORMER_EXCLUSIONS.toArray(new String[0]);
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader,
        @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className,
        @NotNull ClassNodeHandle classNode) {
        if (!classNode.isPresent()) {
            return false;
        }

        for (String exclusion : TRANSFORMER_EXCLUSIONS) {
            if (className.startsWith(exclusion)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context,
        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        if (transformClassNode(className, classNode.getNode())) {
            classNode.computeMaxs();
        }
    }

    /**
     * Transforms a ClassNode by redirecting OpenGL method calls.
     *
     * @param transformedName The transformed class name
     * @param cn The ClassNode to transform
     * @return true if the class was transformed, false otherwise
     */
    private boolean transformClassNode(String transformedName, ClassNode cn) {
        boolean changed = false;

        // Skip special cases
        if (transformedName.equals("net.minecraft.client.renderer.OpenGlHelper")) {
            return false;
        }

        // Process all methods in the class
        for (MethodNode mn : cn.methods) {
            if ((transformedName.equals("net.minecraft.client.renderer.OpenGlHelper") &&
                 (mn.name.equals("glBlendFunc") || mn.name.equals("func_148821_a")))) {
                continue;
            }

            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node instanceof MethodInsnNode methodNode) {

                    // Handle GL11.glEnable and GL11.glDisable with capability redirects
                    if (methodNode.owner.equals(GL11) &&
                        (methodNode.name.equals("glEnable") || methodNode.name.equals("glDisable")) &&
                        methodNode.desc.equals("(I)V")) {

                        final AbstractInsnNode prevNode = node.getPrevious();
                        String name = null;

                        if (prevNode instanceof LdcInsnNode ldcNode) {
                            if (ldcNode.cst instanceof Integer) {
                                name = glCapRedirects.get(((Integer) ldcNode.cst).intValue());
                            }
                        } else if (prevNode instanceof IntInsnNode intNode) {
                            name = glCapRedirects.get(intNode.operand);
                        }

                        if (name != null) {
                            name = methodNode.name.equals("glEnable") ? "enable" + name : "disable" + name;
                        }

                        // Redirect the call to GLStateManager
                        methodNode.owner = GLStateManager;
                        if (name != null) {
                            methodNode.name = name;
                            methodNode.desc = "()V";
                            // Remove the previous node (the GL constant)
                            mn.instructions.remove(prevNode);
                        }

                        changed = true;
                    }
                    // Handle other method redirects
                    else if (LWJGL_CLASSES.contains(methodNode.owner)) {
                        final Map<String, RedirectConfigLoader.MethodRedirect> classMethods = methodRedirects.get(methodNode.owner);
                        if (classMethods != null) {
                            final String methodKey = methodNode.name + methodNode.desc;
                            final RedirectConfigLoader.MethodRedirect redirectInfo = classMethods.get(methodKey);

                            if (redirectInfo != null) {
                                // Redirect the call
                                methodNode.owner = redirectInfo.targetClass();
                                methodNode.name = redirectInfo.targetMethod();
                                methodNode.desc = redirectInfo.targetDescriptor();
                                changed = true;
                            } else {
                                // Log unredirected method
                                UnredirectedMethodLogger.logUnredirectedMethod(methodNode.owner, methodNode.name, methodNode.desc);
                            }
                        } else if (UnredirectedMethodLogger.isLwjglClass(methodNode.owner)) {
                            // Log any other LWJGL calls that aren't redirected
                            UnredirectedMethodLogger.logUnredirectedMethod(methodNode.owner, methodNode.name, methodNode.desc);
                        }
                    }
                }
            }
        }

        return changed;
    }
}
