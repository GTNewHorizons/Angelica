package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import com.gtnewhorizons.angelica.loading.shared.ClassHierarchyHelper;
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
    private static final String ThreadedBlockData = "com/gtnewhorizons/angelica/glsm/ThreadedBlockData";

    private final Map<String, Boolean> blockCache = new ConcurrentHashMap<>();
    // Block owners we *shouldn't* redirect because they shadow one of our fields
    private final Set<String> blockOwnerExclusions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ClassConstantPoolParser cstPoolParser;
    private final Map<String, String> fieldNamesToRedirect = new HashMap<>();
    private final MethodHandle celeritasConfigGetter;
    private boolean isCeleritasEnabled;

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
            this.fieldNamesToRedirect.put(name, pair.getLeft());
        }
        this.cstPoolParser = new ClassConstantPoolParser(this.fieldNamesToRedirect.keySet().toArray(new String[0]));
        try {
            // Needed because the config is loaded in LaunchClassLoader, but we need to access it in the parent system loader.
            final Class<?> angelicaConfig = Class.forName("com.gtnewhorizons.angelica.config.AngelicaConfig", true, Launch.classLoader);
            celeritasConfigGetter = MethodHandles.lookup().findStaticGetter(angelicaConfig, "enableCeleritas", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public void setCeleritasSetting() {
        isCeleritasEnabled = true;
    }

    private boolean isCeleritasEnabled() {
        if (isCeleritasEnabled) return true;
        try {
            return (boolean) celeritasConfigGetter.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
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
        if (!isCeleritasEnabled()) {
            return false;
        }
        boolean changed = false;
        if ("net.minecraft.block.Block".equals(transformedName)) {
            changed = cn.fields.removeIf(field -> fieldNamesToRedirect.containsKey(field.name));
        }
        trackBlockFieldShadowing(cn);
        changed |= redirectFields(cn, transformedName);
        return changed;
    }

    private void trackBlockFieldShadowing(ClassNode cn) {
        // Check if this class is a block and declares fields with
        // the same names as the ones we redirect
        if (ClassHierarchyHelper.isSubclassOf(blockCache, cn.name, BlockClass) && !cn.name.startsWith(BlockClass)) {
            // If a superclass shadows, then so do we, because JVM will resolve a reference on our class to that
            // superclass
            boolean doWeShadow = false;
            if (blockOwnerExclusions.contains(cn.superName)) {
                doWeShadow = true;
            } else {
                // Check if we declare any known field names
                for (FieldNode field : cn.fields) {
                    if (fieldNamesToRedirect.containsKey(field.name)) {
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
    }

    private boolean redirectFields(ClassNode cn, String transformedName) {
        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if ((node.getOpcode() == GETFIELD || node.getOpcode() == PUTFIELD) && node instanceof FieldInsnNode fNode) {
                    final String newFieldName = fieldNamesToRedirect.get(fNode.name);
                    if (newFieldName != null) {
                        if (!blockOwnerExclusions.contains(fNode.owner) && ClassHierarchyHelper.isSubclassOf(blockCache, fNode.owner, BlockClass)) {
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
        }
        return changed;
    }
}
