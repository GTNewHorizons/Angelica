package com.gtnewhorizons.angelica.loading.shared.transformers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashSet;

public class DarkModeFontTransform {

    private static final Logger LOGGER = LogManager.getLogger("DarkModeFontTransformer");

    private static final String BATCHINGFONTRENDERER = "com/gtnewhorizons/angelica/client/font/BatchingFontRenderer";

    public static class MethodInfo {
        public final String className;
        public final String classNameSlash;
        private final String name;
        private final String obfName;
        public final String desc;

        public MethodInfo(@NotNull String className, @NotNull String name, @Nullable String obfName, @NotNull String desc) {
            this.className = className;
            this.classNameSlash = className.replaceAll("\\.", "/");
            this.name = name;
            this.obfName = obfName;
            this.desc = desc;
        }

        public String getName(boolean isObf) {
            if (obfName == null) { return name; }
            if (isObf) { return obfName; }
            return name;
        }

        @Override
        public String toString() {
            return className + "#" + name + desc;
        }
    }

    /**
     * A marker to surround every {@code calledMethod} call in {@code callingMethod} with calls that inform
     * the {@link com.gtnewhorizons.angelica.client.font.BatchingFontRenderer} it's inside a GUI and should
     * recolor text according to the rules in {@link com.gtnewhorizons.angelica.client.font.DarkModeUtils}.
     * @param callingMethod
     * @param calledMethod
     */
    public record FlagAtCallSitesTarget(MethodInfo callingMethod, MethodInfo calledMethod) {}
    public static final FlagAtCallSitesTarget[] targets = {
        // Vanilla methods. Many mods use these; several draw text in the "background" layer...
        new FlagAtCallSitesTarget(
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer", "drawScreen", "func_73863_a", "(IIF)V"),
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer", "drawGuiContainerForegroundLayer", "func_146979_b", "(II)V")
        ),
        new FlagAtCallSitesTarget(
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer", "drawScreen", "func_73863_a", "(IIF)V"),
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer", "drawGuiContainerBackgroundLayer", "func_146976_a", "(FII)V")
        ),
        // Avaritiaddons infinity chest
        new FlagAtCallSitesTarget(
            new MethodInfo("wanion.avaritiaddons.block.chest.infinity.GuiInfinityChest", "drawScreen", null, "(IIF)V"),
            new MethodInfo("wanion.avaritiaddons.block.chest.infinity.GuiInfinityChest", "drawGuiContainerForegroundLayer", null, "(II)V")
        ),
        // SC2
        new FlagAtCallSitesTarget(
            new MethodInfo("vswe.stevescarts.Interfaces.GuiNEIKiller", "drawScreen", null, "(IIF)V"),
            new MethodInfo("vswe.stevescarts.Interfaces.GuiNEIKiller", "drawGuiContainerForegroundLayer", null, "(II)V")
        ),
        // Binnie
        new FlagAtCallSitesTarget(
            new MethodInfo("binnie.core.craftgui.minecraft.GuiCraftGUI", "drawScreen", null, "(IIF)V"),
            new MethodInfo("binnie.core.craftgui.minecraft.Window", "render", null, "()V")
        ),
    };
    public static final HashSet<String> targetClasses = new HashSet<>(targets.length);
    static {
        for (FlagAtCallSitesTarget target : targets) {
            targetClasses.add(target.callingMethod.className);
        }
    }

    public boolean transformClassNode(ClassNode cn, String className, boolean isObf) {
        boolean changed = false;
        for (FlagAtCallSitesTarget tg : targets) {
            if (!className.equals(tg.callingMethod.className)) { continue; }
            for (MethodNode mn : cn.methods) {
                if (!mn.name.equals(tg.callingMethod.getName(isObf))) { continue; }
                if (!mn.desc.equals(tg.callingMethod.desc)) { continue; }
                changed = flagMethodAtCallSites(mn, tg.calledMethod, isObf) || changed;
            }
        }
        return changed;
    }

    private boolean flagMethodAtCallSites(MethodNode mn, MethodInfo calledMethod, boolean isObf) {
        boolean changed = false;
        InsnList insnList = mn.instructions;
        for (AbstractInsnNode insn = insnList.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode min) {
                if (!min.owner.equals(calledMethod.classNameSlash)) { continue; }
                if (!min.name.equals(calledMethod.getName(isObf))) { continue; }
                if (!min.desc.equals(calledMethod.desc)) { continue; }
                int maxLocals = mn.maxLocals;

                insnList.insertBefore(min, new MethodInsnNode(Opcodes.INVOKESTATIC, BATCHINGFONTRENDERER, "enterRecolorSection", "()Z", false));
                insnList.insertBefore(min, new VarInsnNode(Opcodes.ISTORE, maxLocals));

                insnList.insert(min, new MethodInsnNode(Opcodes.INVOKESTATIC, BATCHINGFONTRENDERER, "exitRecolorSection", "(Z)V", false));
                insnList.insert(min, new VarInsnNode(Opcodes.ILOAD, maxLocals));

                LOGGER.info("Added flags at call site of {}", calledMethod.toString());
                changed = true;
            }
        }
        return changed;
    }
}
