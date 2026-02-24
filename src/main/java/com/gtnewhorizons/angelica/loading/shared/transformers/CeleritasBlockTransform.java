package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.google.common.collect.ImmutableList;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CeleritasBlockTransform {

    private final List<String> blockFieldNames = new ArrayList<>();
    private final Map<String, String> blockFieldRedirects = new HashMap<>();

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
            this.blockFieldNames.add(name);
            this.blockFieldRedirects.put(name, pair.getLeft());
        }
    }

    private static final boolean LOG_SPAM = Boolean.getBoolean("angelica.redirectorLogspam");
    private static final Logger LOGGER = LogManager.getLogger("CeleritasBlockTransformer");
    private static final String BlockClass = "net/minecraft/block/Block";
    private static final String BlockPackage = "net/minecraft/block/Block";
    private static final String ThreadedBlockData = "com/gtnewhorizons/angelica/glsm/ThreadedBlockData";
    /** All classes in <tt>net.minecraft.block.*</tt> are the block subclasses save for these. */
    private static final String[] VanillaBlockExclusions = {
        "net/minecraft/block/IGrowable",
        "net/minecraft/block/ITileEntityProvider",
        "net/minecraft/block/BlockEventData",
        "net/minecraft/block/BlockSourceImpl",
        "net/minecraft/block/material/"
    };

    private static final Set<String> moddedBlockSubclasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Block owners we *shouldn't* redirect because they shadow one of our fields
    private static final Set<String> blockOwnerExclusions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Needed because the config is loaded in LaunchClassLoader, but we need to access it in the parent system loader.
    private static final MethodHandle angelicaConfigCeleritasEnabledGetter;
    private static boolean isCeleritasEnabled;

    static {
        try {
            final Class<?> angelicaConfig = Class.forName("com.gtnewhorizons.angelica.config.AngelicaConfig", true, Launch.classLoader);
            angelicaConfigCeleritasEnabledGetter = MethodHandles.lookup().findStaticGetter(angelicaConfig, "enableCeleritas", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isCeleritasEnabled() {
        if (isCeleritasEnabled) return true;
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

    public String[] getTransformerExclusions() {
        return new String[]{
            "org.lwjgl",
            "com.gtnewhorizons.angelica.glsm.",
            "com.gtnewhorizons.angelica.transform",
            "com.gtnewhorizon.gtnhlib.asm.",
            "me.eigenraven.lwjgl3ify"
        };
    }

    private record FieldAccessRecord(FieldInsnNode fieldNode, int paramSlot, String fieldRedirect) {}

    private static class FieldAccessInfo {
        private final int[] paramUsedCount;
        private final ArrayList<FieldAccessRecord> records = new ArrayList<>();

        FieldAccessInfo(MethodNode mn) {
            paramUsedCount = new int[maxParamSlots(mn)];
        }

        private void addRecord(FieldInsnNode fieldNode, int paramSlot, String fieldRedirect) {
            if (paramSlot >= 0) {
                paramUsedCount[paramSlot]++;
            }
            records.add(new FieldAccessRecord(fieldNode, paramSlot, fieldRedirect));
        }
    }

    /** @return Was the class changed? */
    public boolean transformClassNode(String transformedName, ClassNode cn) {
        if ("net.minecraft.block.Block".equals(transformedName) && isCeleritasEnabled()) {
            cn.fields.removeIf(field -> blockFieldNames.stream().anyMatch(name -> field.name.equals(name)));
        }

        // Track subclasses of Block
        if (!isVanillaBlockSubclass(cn.name) && isBlockSubclass(cn.superName)) {
            moddedBlockSubclasses.add(cn.name);
        }

        // Check if this class shadows any fields of the parent class
        if (moddedBlockSubclasses.contains(cn.name)) {
            // If a superclass shadows, then so do we, because JVM will resolve a reference on our class to that
            // superclass
            boolean doWeShadow = false;
            if (blockOwnerExclusions.contains(cn.superName)) {
                doWeShadow = true;
            } else {
                // Check if we declare any known field names
                for (FieldNode field : cn.fields) {
                    if (blockFieldNames.contains(field.name)) {
                        doWeShadow = true;
                        break;
                    }
                }
            }
            if (doWeShadow) {
                LOGGER.info("Class '{}' shadows one or more block bounds fields, these accesses won't be redirected!", cn.name);
                blockOwnerExclusions.add(cn.name);
            }
        }

        if (!isCeleritasEnabled()) {
            return false;
        }

        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            if (mn.instructions.size() == 0) {
                continue;
            }

            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
            boolean analyzeSuccess = false;
            try {
                analyzer.analyze(cn.name, mn);
                analyzeSuccess = true;
            } catch (Exception e) {
                LOGGER.warn("Failed to analyze method {} in {}, falling back to the old implementation.", mn.name, transformedName);
                e.printStackTrace();
            }

            Frame<SourceValue>[] frames = analyzer.getFrames();

            FieldAccessInfo info = new FieldAccessInfo(mn);
            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode node = mn.instructions.get(i);
                Frame<SourceValue> frame = analyzeSuccess ? frames[i] : null;

                if ((node.getOpcode() == Opcodes.GETFIELD || node.getOpcode() == Opcodes.PUTFIELD) && node instanceof FieldInsnNode fNode) {
                    if (!blockOwnerExclusions.contains(fNode.owner) && isBlockSubclass(fNode.owner)) {
                        String fieldRedirect = blockFieldRedirects.get(fNode.name);
                        if (fieldRedirect == null) {
                            continue;
                        }
                        int paramSlot = -1;
                        if (frame != null) {
                            int stackSize = frame.getStackSize();
                            SourceValue receiver;
                            if (fNode.getOpcode() == Opcodes.GETFIELD) {
                                receiver = frame.getStack(stackSize - 1);
                            } else {
                                receiver = frame.getStack(stackSize - 2);
                            }
                            if (receiver.insns.size() == 1) {
                                paramSlot = getParamSlot(receiver.insns.iterator().next(), mn);
                            }
                        }
                        info.addRecord(fNode, paramSlot, fieldRedirect);
                    }
                }
            }

            if (info.records.isEmpty()) continue;

            changed = true;

            int[] caches = prepareCaches(mn, info.paramUsedCount);

            for (FieldAccessRecord record : info.records) {
                FieldInsnNode node = record.fieldNode;
                int paramSlot = record.paramSlot;
                String fieldRedirect = record.fieldRedirect;
                if (LOG_SPAM) {
                    LOGGER.info("Redirecting Block.{} in {} to thread-safe wrapper, param slot: {}", node.name, transformedName, paramSlot);
                }
                // Perform the redirect
                node.name = fieldRedirect; // use unobfuscated name
                node.owner = ThreadedBlockData;

                final InsnList code = new InsnList();
                if (paramSlot >= 0 && info.paramUsedCount[paramSlot] > 1) {
                    int cacheSlot = caches[paramSlot];
                    // Use the cached value instead of calling the getter again
                    if (node.getOpcode() == Opcodes.GETFIELD) {
                        code.add(new InsnNode(Opcodes.POP));
                        code.add(new VarInsnNode(Opcodes.ALOAD, cacheSlot));
                    } else {
                        // FIXME: this code assumes doubles
                        // Stack: Block, double
                        code.add(new InsnNode(Opcodes.DUP2_X1));
                        // Stack: double, Block, double
                        code.add(new InsnNode(Opcodes.POP2));
                        // Stack: double, Block
                        code.add(new InsnNode(Opcodes.POP));
                        // Stack: double
                        code.add(new VarInsnNode(Opcodes.ALOAD, cacheSlot));
                        // Stack: double, ThreadedBlockData
                        code.add(new InsnNode(Opcodes.DUP_X2));
                        // Stack: ThreadedBlockData, double, ThreadedBlockData
                        code.add(new InsnNode(Opcodes.POP));
                        // Stack: ThreadedBlockData, double
                    }
                } else {
                    // Inject getter before the field access, to turn Block -> ThreadedBlockData
                    final MethodInsnNode getter = new MethodInsnNode(Opcodes.INVOKESTATIC, ThreadedBlockData, "get", "(L" + BlockClass + ";)L" + ThreadedBlockData + ";", false);
                    if (node.getOpcode() == Opcodes.GETFIELD) {
                        code.add(getter);
                    } else {
                        // FIXME: this code assumes doubles
                        // Stack: Block, double
                        code.add(new InsnNode(Opcodes.DUP2_X1));
                        // Stack: double, Block, double
                        code.add(new InsnNode(Opcodes.POP2));
                        // Stack: double, Block
                        code.add(getter);
                        // Stack: double, ThreadedBlockData
                        code.add(new InsnNode(Opcodes.DUP_X2));
                        // Stack: ThreadedBlockData, double, ThreadedBlockData
                        code.add(new InsnNode(Opcodes.POP));
                        // Stack: ThreadedBlockData, double
                    }
                }
                mn.instructions.insertBefore(node, code);
            }
        }

        return changed;
    }

    /** Put getters at the start of the method to redirect the field accesses, and cache them in local variables if they're used multiple times */
    private static int @NotNull [] prepareCaches(@NotNull MethodNode mn, int @NotNull [] paramUsedCount) {
        LabelNode methodStart = new LabelNode(new Label());
        LabelNode methodEnd = new LabelNode(new Label());
        int[] caches = new int[paramUsedCount.length];
        final InsnList initCache = new InsnList();
        for (int i = 0; i < paramUsedCount.length; i++) {
            if (paramUsedCount[i] > 1) {
                caches[i] = mn.maxLocals++;
                mn.localVariables.add(new LocalVariableNode("angelica$blockDataCache" + i, "L" + ThreadedBlockData + ";", null, methodStart, methodEnd, caches[i]));
                initCache.add(new VarInsnNode(Opcodes.ALOAD, i));
                initCache.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ThreadedBlockData, "get", "(L" + BlockClass + ";)L" + ThreadedBlockData + ";", false));
                initCache.add(new VarInsnNode(Opcodes.ASTORE, caches[i]));
            }
        }
        mn.instructions.insertBefore(mn.instructions.getFirst(), initCache);
        mn.instructions.insertBefore(mn.instructions.getFirst(), methodStart);
        mn.instructions.insertBefore(mn.instructions.getLast(), methodEnd);
        return caches;
    }

    private static int maxParamSlots(@NotNull MethodNode mn) {
        int size = Type.getArgumentsAndReturnSizes(mn.desc) >> 2;
        if (!Modifier.isStatic(mn.access)) size += 1;
        return size;
    }

    private static int getParamSlot(@NotNull AbstractInsnNode src, @NotNull MethodNode mn) {
        if (!(src instanceof VarInsnNode)) return -1;
        if (src.getOpcode() != Opcodes.ALOAD) return -1;
        int var = ((VarInsnNode) src).var;
        int paramSlots = maxParamSlots(mn);
        return var < paramSlots ? var : -1;
    }

    public void setCeleritasSetting() {
        isCeleritasEnabled = true;
    }
}
