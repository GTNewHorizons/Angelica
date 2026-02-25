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

    private static final Map<String, Map<String, String>> overloadedMethods = new HashMap<>();

    private record FieldAccessRecord(int nodeIndex, FieldInsnNode fieldNode, int paramSlot, String fieldRedirect) {}

    private static class FieldAccessInfo {
        /**
         * The number of times fields of incoming parameters that need to be redirected are read or written in the function.
         * non-`Block` parameters are always 0.
         * **paramSlot -> count**
         */
        private final int[] paramUsedCount;
        /**
         * Records of instructions that read or write fields requiring redirection.
         */
        private final ArrayList<FieldAccessRecord> records = new ArrayList<>();
        /**
         * **paramSlot -> cacheSlot**
         */
        private int[] cacheSlots;

        FieldAccessInfo(MethodNode mn) {
            paramUsedCount = new int[maxParamSlots(mn)];
        }

        private void addRecord(int nodeIndex, FieldInsnNode fieldNode, int paramSlot, String fieldRedirect) {
            if (paramSlot >= 0) {
                paramUsedCount[paramSlot]++;
            }
            records.add(new FieldAccessRecord(nodeIndex, fieldNode, paramSlot, fieldRedirect));
        }

        /** Put getters at the start of the method to redirect the field accesses, and cache them in local variables if they're used multiple times */
        private void prepareCaches(MethodNode mn) {
            cacheSlots = CeleritasBlockTransform.prepareCaches(mn, paramUsedCount);
        }
    }

    private record MethodInvokeRecord(int nodeIndex, MethodInsnNode methodNode, int paramSlot, String newDesc) {}

    private static class MethodInvokeInfo {
        private final int[] paramUsedCount;
        private final ArrayList<MethodInvokeRecord> records = new ArrayList<>();
        private int[] cacheSlots;

        MethodInvokeInfo(MethodNode mn) {
            paramUsedCount = new int[maxParamSlots(mn)];
        }

        private void addRecord(int nodeIndex, MethodInsnNode methodNode, int paramSlot, String newDesc) {
            if (paramSlot >= 0) {
                paramUsedCount[paramSlot]++;
            }
            records.add(new MethodInvokeRecord(nodeIndex, methodNode, paramSlot, newDesc));
        }

        private void prepareCaches(MethodNode mn) {
            cacheSlots = CeleritasBlockTransform.prepareCaches(mn, paramUsedCount);
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
        for (int i = 0; i < cn.methods.size(); i++) {
            MethodNode mn = cn.methods.get(i);
            if (mn.instructions.size() == 0) continue;

            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
            boolean analyzeSuccess = false;
            try {
                // FIXME The game launches fine but analyze fails on some classes, that's strange.
                // If analysis fails, fall back to the original implementation.
                // This will not cause any issues other than potential performance impact.
                analyzer.analyze(cn.name, mn);
                analyzeSuccess = true;
            } catch (Exception e) {
                LOGGER.warn("Failed to analyze method {} in {}, falling back to the old implementation.", mn.name, transformedName);
            }

            FieldAccessInfo info = getFieldAccessInfo(mn, analyzeSuccess ? analyzer.getFrames() : null); // Pass null to fall back.

            if (info.records.isEmpty()) continue;

            changed = true;

            if (!Modifier.isStatic(mn.access) && info.paramUsedCount[0] > 0) {
                MethodNode overload = createOverload(mn, info.records);
                cn.methods.add(overload);
                overloadedMethods.computeIfAbsent(cn.name, _ -> new HashMap<>()).put(mn.name + mn.desc, overload.desc);
            }

            info.prepareCaches(mn); // Get ThreadedBlockData at the start of the function and cache it.

            for (FieldAccessRecord record : info.records) {
                FieldInsnNode node = record.fieldNode;
                if (LOG_SPAM) {
                    LOGGER.info("Redirecting Block.{} in {} to thread-safe wrapper, param slot: {}", node.name, transformedName, record.paramSlot);
                }
                // Perform the redirect
                node.name = record.fieldRedirect; // use unobfuscated name
                node.owner = ThreadedBlockData;
                mn.instructions.insertBefore(node, makeRedirect(node, record.paramSlot, info));
            }
        }
        for (MethodNode mn : cn.methods) {
            if (mn.instructions.size() == 0) continue;

            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
            try {
                // FIXME The game launches fine but analyze fails on some classes, that's strange.
                analyzer.analyze(cn.name, mn);
            } catch (Exception e) {
                LOGGER.warn("Failed to analyze method {} in {}, do nothing in this method.", mn.name, transformedName);
                continue;
            }

            MethodInvokeInfo info = getMethodInvokeInfo(mn, analyzer.getFrames());

            if (info.records.isEmpty()) continue;

            info.prepareCaches(mn);

            for (MethodInvokeRecord record : info.records) {
                MethodInsnNode node = record.methodNode;
                if (info.paramUsedCount[record.paramSlot] > 1) {
                    int cacheSlot = info.cacheSlots[record.paramSlot];
                    node.desc = record.newDesc;
                    mn.instructions.insertBefore(node, new VarInsnNode(Opcodes.ALOAD, cacheSlot));
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static @NotNull MethodInvokeInfo getMethodInvokeInfo(MethodNode mn, Frame<SourceValue>[] frames) {
        MethodInvokeInfo info = new MethodInvokeInfo(mn);
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode node = mn.instructions.get(i);
            Frame<SourceValue> frame = frames[i];
            if (frame == null) continue;

            if ((node.getOpcode() == Opcodes.INVOKEVIRTUAL || node.getOpcode() == Opcodes.INVOKESPECIAL) && node instanceof MethodInsnNode mNode) {
                Map<String, String> map = overloadedMethods.get(mNode.owner);
                if (map == null) continue;
                String newDesc = map.get(mNode.name + mNode.desc);
                if (newDesc == null) continue;
                int stackSize = frame.getStackSize();
                int argSize = Type.getArgumentTypes(mNode.desc).length;
                SourceValue receiver = frame.getStack(stackSize - argSize - 1);
                if (receiver.insns.size() != 1) continue;
                int paramSlot = getParamSlot(receiver.insns.iterator().next(), mn);
                if (paramSlot < 0) continue;
                info.addRecord(i, mNode, paramSlot, newDesc);
            }
        }
        return info;
    }

    private static @NotNull InsnList makeRedirect(@NotNull FieldInsnNode node, int paramSlot, @NotNull FieldAccessInfo info) {
        assert info.cacheSlots != null : "Caches should have been prepared before applying redirects";
        final InsnList code = new InsnList();
        if (paramSlot >= 0 && info.paramUsedCount[paramSlot] > 1) {
            int cacheSlot = info.cacheSlots[paramSlot];
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
        return code;
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

    private static @NotNull MethodNode createOverload(@NotNull MethodNode original, @NotNull List<FieldAccessRecord> records) {
        if (Modifier.isStatic(original.access)) throw new IllegalArgumentException("Can't create overload of static method");
        MethodNode mn = new MethodNode(
            Opcodes.ASM5,
            original.access,
            original.name,
            original.desc.substring(0, original.desc.indexOf(')')) + "L" + ThreadedBlockData + ";" + original.desc.substring(original.desc.indexOf(')')),
            original.signature,
            original.exceptions.toArray(new String[0])
        );
        mn.maxLocals = original.maxLocals + 1;
        mn.maxStack = original.maxStack;

        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode node : original.instructions.toArray()) {
            if (node instanceof LabelNode label) {
                labelMap.computeIfAbsent(label, _ -> new LabelNode(new Label()));
            }
        }
        for (AbstractInsnNode node : original.instructions.toArray()) {
            mn.instructions.add(node.clone(labelMap));
        }
        Map<Integer, FieldInsnNode> nodeMap = new HashMap<>();
        for (FieldAccessRecord record : records) {
            nodeMap.put(record.nodeIndex, (FieldInsnNode) mn.instructions.get(record.nodeIndex));
        }

        LabelNode methodStart = new LabelNode(new Label());
        LabelNode initEnd = new LabelNode(new Label());
        LabelNode methodEnd = new LabelNode(new Label());
        final InsnList init = new InsnList();
        init.add(methodStart);
        init.add(new VarInsnNode(Opcodes.ALOAD, maxParamSlots(original)));
        init.add(initEnd);
        init.add(new VarInsnNode(Opcodes.ASTORE, original.maxLocals));
        mn.instructions.insertBefore(mn.instructions.getFirst(), init);
        mn.instructions.insertBefore(mn.instructions.getLast(), methodEnd);

        if (mn.localVariables == null) mn.localVariables = new ArrayList<>();
        mn.localVariables.clear();
        mn.localVariables.add(new LocalVariableNode("angelica$this", "L" + ThreadedBlockData + ";", null, methodStart, initEnd, maxParamSlots(original)));
        mn.localVariables.add(new LocalVariableNode("angelica$this", "L" + ThreadedBlockData + ";", null, initEnd, methodEnd, original.maxLocals));
        for (LocalVariableNode var : original.localVariables) {
            mn.localVariables.add(new LocalVariableNode(var.name, var.desc, var.signature, labelMap.get(var.start), labelMap.get(var.end), var.index));
        }

        for (FieldAccessRecord record : records) {
            if (record.paramSlot != 0) continue; // Only redirect accesses to the original "this" parameter.
            FieldInsnNode node = nodeMap.get(record.nodeIndex);
            // Perform the redirect
            node.name = record.fieldRedirect; // use unobfuscated name
            node.owner = ThreadedBlockData;
            final InsnList code = new InsnList();
            if (node.getOpcode() == Opcodes.GETFIELD) {
                code.add(new InsnNode(Opcodes.POP));
                code.add(new VarInsnNode(Opcodes.ALOAD, original.maxLocals));
            } else {
                // FIXME: this code assumes doubles
                // Stack: Block, double
                code.add(new InsnNode(Opcodes.DUP2_X1));
                // Stack: double, Block, double
                code.add(new InsnNode(Opcodes.POP2));
                // Stack: double, Block
                code.add(new InsnNode(Opcodes.POP));
                // Stack: double
                code.add(new VarInsnNode(Opcodes.ALOAD, original.maxLocals));
                // Stack: double, ThreadedBlockData
                code.add(new InsnNode(Opcodes.DUP_X2));
                // Stack: ThreadedBlockData, double, ThreadedBlockData
                code.add(new InsnNode(Opcodes.POP));
                // Stack: ThreadedBlockData, double
            }
            mn.instructions.insertBefore(node, code);
        }
        return mn;
    }

    /** Scan the instructions to find all accesses to fields that need to be redirected, and which parameters they come from (if any) */
    private @NotNull FieldAccessInfo getFieldAccessInfo(MethodNode mn, Frame<SourceValue>[] frames) {
        FieldAccessInfo info = new FieldAccessInfo(mn);
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode node = mn.instructions.get(i);
            Frame<SourceValue> frame = frames != null ? frames[i] : null;

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
                    info.addRecord(i, fNode, paramSlot, fieldRedirect);
                }
            }
        }
        return info;
    }

    private static int maxParamSlots(@NotNull MethodNode mn) {
        int size = Type.getArgumentsAndReturnSizes(mn.desc) >> 2;
        if (Modifier.isStatic(mn.access)) size -= 1;
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
