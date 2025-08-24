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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This transformer redirects many GL calls to our custom GLStateManager
 * <p>
 * THIS CLASS MIGHT BE LOADED ON A DIFFERENT CLASS LOADER,
 * IT SHOULD NOT CALL ANY CODE FROM THE MAIN MOD
 */
public class AngelicaRedirector {

    public AngelicaRedirector(boolean isObf) {
        IS_OBF = isObf;
    }

    private final boolean IS_OBF;
    private static final boolean LOG_SPAM = Boolean.getBoolean("angelica.redirectorLogspam");
    private static final Logger LOGGER = LogManager.getLogger("AngelicaRedirector");
    private static final String Drawable = "org/lwjgl/opengl/Drawable";
    private static final String GLStateManager = "com/gtnewhorizons/angelica/glsm/GLStateManager";

    private static final String BlockClass = "net/minecraft/block/Block";
    private static final String BlockPackage = "net/minecraft/block/Block";

    private static final String ThreadedBlockData = "com/gtnewhorizons/angelica/glsm/ThreadedBlockData";

    private static final List<Pair<String, String>> BlockBoundsFields = ImmutableList.of(
            Pair.of("minX", "field_149759_B"),
            Pair.of("minY", "field_149760_C"),
            Pair.of("minZ", "field_149754_D"),
            Pair.of("maxX", "field_149755_E"),
            Pair.of("maxY", "field_149756_F"),
            Pair.of("maxZ", "field_149757_G")
    );

    /** All classes in <tt>net.minecraft.block.*</tt> are the block subclasses save for these. */
    private static final String[] VanillaBlockExclusions = {
        "net/minecraft/block/IGrowable",
        "net/minecraft/block/ITileEntityProvider",
        "net/minecraft/block/BlockEventData",
        "net/minecraft/block/BlockSourceImpl",
        "net/minecraft/block/material/"
    };

    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(BlockPackage);

    private static final Set<String> moddedBlockSubclasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Block owners we *shouldn't* redirect because they shadow one of our fields
    private static final Set<String> blockOwnerExclusions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Needed because the config is loaded in LaunchClassLoader, but we need to access it in the parent system loader.
    private static final MethodHandle angelicaConfigCeleritasEnabledGetter;

    static {

        try {
            final Class<?> angelicaConfig = Class.forName("com.gtnewhorizons.angelica.config.AngelicaConfig", true, Launch.classLoader);
            final MethodHandle celeritasGetter = MethodHandles.lookup().findStaticGetter(angelicaConfig, "enableCeleritas", boolean.class);
            angelicaConfigCeleritasEnabledGetter = celeritasGetter;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isCeleritasEnabled() {
        try {
            return (boolean) angelicaConfigCeleritasEnabledGetter.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isVanillaBlockSubclass(String className) {
        if (className.startsWith(BlockPackage)) {
            for (String exclusion : VanillaBlockExclusions) {
                if (className.startsWith(exclusion)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isBlockSubclass(String className) {
        return isVanillaBlockSubclass(className) || moddedBlockSubclasses.contains(className);
    }

    private String getFieldName(Pair<String, String> fieldPair) {
        return IS_OBF ? fieldPair.getRight() : fieldPair.getLeft();
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
        if (cn == null) return false;

        // Track subclasses of Block
        if (!isVanillaBlockSubclass(cn.name) && isBlockSubclass(cn.superName)) {
            moddedBlockSubclasses.add(cn.name);
            cstPoolParser.addString(cn.name);
        }

        // Check if this class shadows any fields of the parent class
        if (moddedBlockSubclasses.contains(cn.name)) {
            // If a superclass shadows, then so do we, because JVM will resolve a reference on our class to that superclass
            boolean doWeShadow;
            if (blockOwnerExclusions.contains(cn.superName)) {
                doWeShadow = true;
            } else {
                // Check if we declare any known field names
                doWeShadow = BlockBoundsFields.stream().anyMatch(pair ->
                    cn.fields.stream().anyMatch(field -> field.name.equals(getFieldName(pair))));
            }
            if (doWeShadow) {
                LOGGER.info("Class '{}' shadows one or more block bounds fields, these accesses won't be redirected!", cn.name);
                blockOwnerExclusions.add(cn.name);
            }
        }

        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node instanceof MethodInsnNode mNode) {
                   if (mNode.owner.startsWith(Drawable) && mNode.name.equals("makeCurrent")) {
                        mNode.setOpcode(Opcodes.INVOKESTATIC);
                        mNode.owner = GLStateManager;
                        mNode.desc = "(L" + Drawable + ";)V";
                        mNode.itf = false;
                        changed = true;
                        if (LOG_SPAM) {
                            LOGGER.info("Redirecting call in {} to GLStateManager.makeCurrent()", transformedName);
                        }
                    }
                } else if ((node.getOpcode() == Opcodes.GETFIELD || node.getOpcode() == Opcodes.PUTFIELD) && node instanceof FieldInsnNode fNode) {
                    if (!blockOwnerExclusions.contains(fNode.owner) && isBlockSubclass(fNode.owner) && isCeleritasEnabled()) {
                        Pair<String, String> fieldToRedirect = BlockBoundsFields.stream()
                            .filter(pair -> fNode.name.equals(getFieldName(pair)))
                            .findFirst()
                            .orElse(null);
                        if (fieldToRedirect != null) {
                            if (LOG_SPAM) {
                                LOGGER.info("Redirecting Block.{} in {} to thread-safe wrapper", fNode.name, transformedName);
                            }
                            // Perform the redirect
                            fNode.name = fieldToRedirect.getLeft(); // use unobfuscated name
                            fNode.owner = ThreadedBlockData;
                            // Inject getter before the field access, to turn Block -> ThreadedBlockData
                            final MethodInsnNode getter = new MethodInsnNode(Opcodes.INVOKESTATIC, ThreadedBlockData, "get", "(L" + BlockClass + ";)L" + ThreadedBlockData + ";", false);
                            if (node.getOpcode() == Opcodes.GETFIELD) {
                                mn.instructions.insertBefore(fNode, getter);
                            } else if (node.getOpcode() == Opcodes.PUTFIELD) {
                                // FIXME: this code assumes doubles
                                // Stack: Block, double
                                final InsnList beforePut = new InsnList();
                                beforePut.add(new InsnNode(Opcodes.DUP2_X1));
                                // Stack: double, Block, double
                                beforePut.add(new InsnNode(Opcodes.POP2));
                                // Stack: double, Block
                                beforePut.add(getter);
                                // Stack: double, ThreadedBlockData
                                beforePut.add(new InsnNode(Opcodes.DUP_X2));
                                // Stack: ThreadedBlockData, double, ThreadedBlockData
                                beforePut.add(new InsnNode(Opcodes.POP));
                                // Stack: ThreadedBlockData, double
                                mn.instructions.insertBefore(fNode, beforePut);
                            }
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

}
