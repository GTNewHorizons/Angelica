package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CeleritasBlockTransform implements Opcodes {

    private static final boolean LOG_SPAM = Boolean.getBoolean("angelica.redirectorLogspam");
    private static final Logger LOGGER = LogManager.getLogger("CeleritasBlockTransformer");
    private static final String BlockClass = "net/minecraft/block/Block";
    private static final String ThreadedBlockData = "com/gtnewhorizons/angelica/client/rendering/ThreadedBlockData";
    /** All classes under <tt>net.minecraft.block.Block*</tt> are Block subclasses save for these. */
    private static final String[] VanillaBlockExclusions = {
        "net/minecraft/block/IGrowable",
        "net/minecraft/block/ITileEntityProvider",
        "net/minecraft/block/BlockEventData",
        "net/minecraft/block/BlockSourceImpl",
        "net/minecraft/block/material/"
    };

    private final Map<String, String> fieldNameToRedirect = new HashMap<>();
    private final Set<String> moddedBlockSubclasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Block subclass owners we shouldn't redirect because they shadow some fields we want to redirect
    private final Set<String> blockSubclassExclusions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ClassConstantPoolParser cstPoolParser;

    public CeleritasBlockTransform(boolean isObf) {
        final List<Pair<String, String>> mappings = ImmutableList.of(
            Pair.of("minX", "field_149759_B"),
            Pair.of("minY", "field_149760_C"),
            Pair.of("minZ", "field_149754_D"),
            Pair.of("maxX", "field_149755_E"),
            Pair.of("maxY", "field_149756_F"),
            Pair.of("maxZ", "field_149757_G")
        );
        for (Pair<String, String> pair : mappings) {
            final String name = isObf ? pair.getRight() : pair.getLeft();
            this.fieldNameToRedirect.put(name, pair.getLeft());
        }

        this.cstPoolParser = new ClassConstantPoolParser(this.fieldNameToRedirect.keySet().toArray(new String[0]));
    }

    private boolean isVanillaBlockSubclass(String className) {
        if (!className.startsWith(BlockClass)) {
            return false;
        }
        for (String exclusion : VanillaBlockExclusions) {
            if (className.startsWith(exclusion)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlockSubclass(String className) {
        return isVanillaBlockSubclass(className) || moddedBlockSubclasses.contains(className);
    }

    // This method needs to be called for every class, including the ones we don't want to transform.
    // Vanilla blocks are recognized by name; only modded subclasses need tracking, and FML's
    // EventSubscriptionTransformer guarantees a modded class's superclass loads first.
    public void trackBlockSubclasses(String className, String superClassName) {
        if (!isVanillaBlockSubclass(className) && isBlockSubclass(superClassName)) {
            moddedBlockSubclasses.add(className);

            if (blockSubclassExclusions.contains(superClassName)) {
                LOGGER.info("Class {} extends a class with shadowed block bounds fields and will be skipped from redirecting", className);
                blockSubclassExclusions.add(className);
            }
        }
    }

    public String[] getTransformerExclusions() {
        return new String[]{
            "org.lwjgl",
            "com.gtnewhorizons.angelica.glsm.",
            "com.gtnewhorizons.angelica.transform",
            "me.eigenraven.lwjgl3ify"
        };
    }

    public boolean shouldTransform(byte[] classBytes) {
        return cstPoolParser.find(classBytes);
    }

    /** @return Was the class changed? */
    public boolean transformClassNode(String transformedName, ClassNode cn) {
        boolean changed = false;
        if (BlockClass.equals(cn.name)) {
            changed = cn.fields.removeIf(field -> fieldNameToRedirect.containsKey(field.name));
        } else {
            trackBlockShadowingFields(cn);
        }
        changed |= redirectBlockBoundFields(cn, transformedName);
        return changed;
    }

    private void trackBlockShadowingFields(ClassNode cn) {
        // Only modded subclasses can shadow; vanilla declares these fields solely in Block.
        if (moddedBlockSubclasses.contains(cn.name)) {
            for (FieldNode field : cn.fields) {
                if (fieldNameToRedirect.containsKey(field.name)) {
                    LOGGER.info("Class '{}' shadows one or more block bounds fields, these accesses won't be redirected!", cn.name);
                    blockSubclassExclusions.add(cn.name);
                    break;
                }
            }
        }
    }

    private boolean redirectBlockBoundFields(ClassNode cn, String transformedName) {
        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode node = mn.instructions.getFirst(); node != null; node = node.getNext()) {
                if ((node.getOpcode() == GETFIELD || node.getOpcode() == PUTFIELD) && node instanceof FieldInsnNode fNode) {
                    String newFieldName = fieldNameToRedirect.get(fNode.name);
                    if (newFieldName != null && isBlockSubclass(fNode.owner) && !blockSubclassExclusions.contains(fNode.owner)) {
                        if (LOG_SPAM) {
                            LOGGER.info("Redirecting Block.{} in {} to thread-safe wrapper", fNode.name, transformedName);
                        }
                        // Perform the redirect
                        fNode.name = newFieldName; // use unobfuscated name
                        fNode.owner = ThreadedBlockData;
                        // Inject getter before the field access, to turn Block -> ThreadedBlockData
                        final MethodInsnNode getter = new MethodInsnNode(INVOKESTATIC, ThreadedBlockData, "get", "(L" + BlockClass + ";)L" + ThreadedBlockData + ";", false);
                        if (node.getOpcode() == GETFIELD) {
                            mn.instructions.insertBefore(fNode, getter);
                        } else if (node.getOpcode() == PUTFIELD) {
                            // FIXME: this code assumes doubles
                            // Stack: Block, double
                            final InsnList beforePut = new InsnList();
                            beforePut.add(new InsnNode(DUP2_X1));
                            // Stack: double, Block, double
                            beforePut.add(new InsnNode(POP2));
                            // Stack: double, Block
                            beforePut.add(getter);
                            // Stack: double, ThreadedBlockData
                            beforePut.add(new InsnNode(DUP_X2));
                            // Stack: ThreadedBlockData, double, ThreadedBlockData
                            beforePut.add(new InsnNode(POP));
                            // Stack: ThreadedBlockData, double
                            mn.instructions.insertBefore(fNode, beforePut);
                        }
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }
}
