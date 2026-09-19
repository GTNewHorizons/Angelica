package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.DarkModeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DarkModeFontTransform {

    private static final Logger LOGGER = LogManager.getLogger("DarkModeFontTransformer");

    private static final String BATCHINGFONTRENDERER = "com/gtnewhorizons/angelica/client/font/BatchingFontRenderer";

    public static class MethodInfo {
        public final String className;
        public final String classNameSlash;
        private final String name;
        private final String obfName;
        public final String desc;

        // Accepts an AT entry, examples:
        // net.minecraft.client.gui.inventory.GuiContainer func_73863_a(IIF)V # drawScreen
        // vswe.stevescarts.Interfaces.GuiNEIKiller drawScreen(IIF)V
        public MethodInfo(@NotNull String atEntry) {
            int firstSpace = atEntry.indexOf(' ');
            int descStart = atEntry.indexOf('(');
            int hashIndex = atEntry.indexOf('#');

            String className = atEntry.substring(0, firstSpace);
            String identifierBeforeDesc = atEntry.substring(firstSpace, descStart).trim();
            String name, obfName, desc;
            if (hashIndex != -1) {
                obfName = identifierBeforeDesc;
                desc = atEntry.substring(descStart, hashIndex).trim();
                name = atEntry.substring(hashIndex + 1).trim();
            } else {
                name = identifierBeforeDesc;
                desc = atEntry.substring(descStart);
                obfName = null;
            }

            this(className, name, obfName, desc);
        }

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
     * Data used to tell the transformer that it should use some calls
     * ({@link BatchingFontRenderer#enterRecolorSection(boolean)}, {@link BatchingFontRenderer#exitRecolorSection(boolean)})
     * to mark areas where text should be recolored according to the rules in {@link DarkModeUtils}.
     * Can either apply to an entire method (placing markers in it) or a specific method call in some method (wrapping the call).
     * A button target wraps the same way, but with a button section that also carries the button's three text colors.
     */
    public record RecolorTarget(@NotNull MethodInfo method, @Nullable MethodInfo calledMethod, boolean recolorEnabled, @Nullable ButtonColors buttonColors) {
        public RecolorTarget(MethodInfo method, MethodInfo calledMethod, boolean recolorEnabled) { this(method, calledMethod, recolorEnabled, null); }
        public static RecolorTarget includeMethod(MethodInfo method) { return new RecolorTarget(method, null, true); }
        public static RecolorTarget excludeMethod(MethodInfo method) { return new RecolorTarget(method, null, false); }
        public static RecolorTarget includeMethodCall(MethodInfo callingMethod, MethodInfo calledMethod) { return new RecolorTarget(callingMethod, calledMethod, true); }
        public static RecolorTarget excludeMethodCall(MethodInfo callingMethod, MethodInfo calledMethod) { return new RecolorTarget(callingMethod, calledMethod, false); }

        public static RecolorTarget includeButtonMethod(MethodInfo method, int enabledColor, int hoveredColor, int disabledColor) {
            return new RecolorTarget(method, null, true, new ButtonColors(enabledColor, hoveredColor, disabledColor));
        }
        public static RecolorTarget includeButtonMethodCall(MethodInfo callingMethod, MethodInfo calledMethod, int enabledColor, int hoveredColor, int disabledColor) {
            return new RecolorTarget(callingMethod, calledMethod, true, new ButtonColors(enabledColor, hoveredColor, disabledColor));
        }

        public boolean targetsCall() { return calledMethod != null; }
    }

    /** The colors a button draws its text in for each of its three states. */
    public record ButtonColors(int enabled, int hovered, int disabled) {}

    // A target method must contain the obf name if it's
    // a) a vanilla method
    // or b) a modded method belonging to a class that extends (or is) a vanilla class that declares said method (so, an override).
    // Notably, "GuiNEIKiller" does not override drawGuiContainerForegroundLayer because it doesn't inherit GuiContainer.
    public static final RecolorTarget[] recolorTargets = {
        // Vanilla methods. Many mods use these; several draw text in the "background" layer...
        RecolorTarget.includeMethodCall(
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer func_146979_b(II)V # drawGuiContainerForegroundLayer")
        ),
        RecolorTarget.includeMethodCall(
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("net.minecraft.client.gui.inventory.GuiContainer func_146976_a(FII)V # drawGuiContainerBackgroundLayer")
        ),
        RecolorTarget.excludeMethod(
            new MethodInfo("net.minecraft.client.renderer.entity.RenderItem func_94148_a(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V # renderItemOverlayIntoGUI")
        ),
        // Modded stuff. Some mods copy-paste vanilla code.
            // Avaritia Addons
        RecolorTarget.includeMethodCall(
            new MethodInfo("wanion.avaritiaddons.block.chest.infinity.GuiInfinityChest func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("wanion.avaritiaddons.block.chest.infinity.GuiInfinityChest func_146979_b(II)V # drawGuiContainerForegroundLayer")
        ),
            // Steve's Carts 2
        RecolorTarget.includeMethodCall(
            new MethodInfo("vswe.stevescarts.Interfaces.GuiNEIKiller func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("vswe.stevescarts.Interfaces.GuiNEIKiller drawGuiContainerForegroundLayer(II)V")
        ),
            // Binnie 
        RecolorTarget.includeMethodCall(
            new MethodInfo("binnie.core.craftgui.minecraft.GuiCraftGUI func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("binnie.core.craftgui.minecraft.Window render()V")
        ),
            // Malisis Core (Malisis' Doors)
        RecolorTarget.includeMethodCall(
            new MethodInfo("net.malisis.core.client.gui.MalisisGui func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("net.malisis.core.client.gui.GuiRenderer drawScreen(Lnet/malisis/core/client/gui/component/container/UIContainer;IIF)V")
        ),
            // Nuclear Control
        RecolorTarget.includeMethod(
            new MethodInfo("shedar.mods.ic2.nuclearcontrol.gui.controls.GuiHowlerAlarmSlider func_146112_a(Lnet/minecraft/client/Minecraft;II)V # drawButton")
        ),
        RecolorTarget.includeMethod(
            new MethodInfo("shedar.mods.ic2.nuclearcontrol.gui.controls.GuiHowlerAlarmListBox func_146112_a(Lnet/minecraft/client/Minecraft;II)V # drawButton")
        ),
        RecolorTarget.includeMethod(
            new MethodInfo("shedar.mods.ic2.nuclearcontrol.gui.GuiScreenColor func_73863_a(IIF)V # drawScreen")
        ),
            // Draconic Evolution
        RecolorTarget.includeMethodCall(
            new MethodInfo("com.brandon3055.draconicevolution.client.gui.GUIFlowGate func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("net.minecraft.client.gui.FontRenderer func_78276_b(Ljava/lang/String;III)I # drawString")
        ),
        RecolorTarget.includeMethodCall(
            new MethodInfo("com.brandon3055.draconicevolution.client.gui.GUIParticleGenerator func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("com.brandon3055.draconicevolution.client.gui.GUIParticleGenerator$IProperty drawLabel(Lnet/minecraft/client/gui/FontRenderer;II)V")
        ),
        RecolorTarget.includeMethodCall(
            new MethodInfo("com.brandon3055.draconicevolution.client.gui.GUIParticleGenerator func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("net.minecraft.client.gui.FontRenderer func_78279_b(Ljava/lang/String;IIII)V # drawSplitString")
        ),
            // Logistics Pipes
        RecolorTarget.includeMethodCall(
            new MethodInfo("logisticspipes.utils.gui.SubGuiScreen func_73863_a(IIF)V # drawScreen"),
            new MethodInfo("logisticspipes.utils.gui.SubGuiScreen drawGuiContainerForegroundLayer(II)V")
        ),
            // Minecraft-Backpack-Mod
        RecolorTarget.includeMethod(
            new MethodInfo("de.eydamos.guiadvanced.form.Label draw(Lnet/minecraft/client/Minecraft;IIF)V")
        ),
        // Additional exclusions
            // Applied Energistics 2 
        RecolorTarget.excludeMethod(
            new MethodInfo("appeng.client.render.StackSizeRenderer drawStackSize(IILjava/lang/String;Lnet/minecraft/client/gui/FontRenderer;Lappeng/api/config/TerminalFontSize;)V")
        ),
            // Thaumcraft
        RecolorTarget.excludeMethod(
            new MethodInfo("thaumcraft.client.gui.GuiResearchTable drawAspects(II)V")
        ),
            // Steve's Carts 2
        RecolorTarget.excludeMethod(
            new MethodInfo("vswe.stevescarts.Interfaces.GuiBase drawMouseOver(Ljava/lang/String;II)V")
        ),
        // Buttons. The three colors are the button's own: enabled, hovered, disabled.
            // Vanilla
        RecolorTarget.includeButtonMethodCall(
            new MethodInfo("net.minecraft.client.gui.GuiButton func_146112_a(Lnet/minecraft/client/Minecraft;II)V # drawButton"),
            new MethodInfo("net.minecraft.client.gui.GuiButton func_73732_a(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V # drawCenteredString"),
            0xE0E0E0, 0xFFFFA0, 0xA0A0A0
        ),
            // CodeChickenCore
        RecolorTarget.includeButtonMethod(
            new MethodInfo("codechicken.core.gui.GuiCCButton drawText(II)V"),
            0xE0E0E0, 0xFFFFA0, 0xA0A0A0
        ),
            // NotEnoughItems
        RecolorTarget.includeButtonMethodCall(
            new MethodInfo("codechicken.nei.LayoutStyleMinecraft drawButton(Lcodechicken/nei/Button;II)V"),
            new MethodInfo("codechicken.lib.gui.GuiDraw drawStringC(Ljava/lang/String;III)V"),
            0xE0E0E0, 0xFFFFA0, 0x601010
        ),
        RecolorTarget.includeButtonMethod(
            new MethodInfo("codechicken.nei.GuiNEIButton drawContent(Lnet/minecraft/client/Minecraft;IIZ)V"),
            0xE0E0E0, 0xFFFFA0, 0xA0A0A0
        ),
        RecolorTarget.includeButtonMethodCall(
            new MethodInfo("codechicken.nei.config.OptionButton drawButton(II)V"),
            new MethodInfo("codechicken.lib.gui.GuiDraw drawStringC(Ljava/lang/String;IIIII)V"),
            0xE0E0E0, 0xFFFFA0, 0xA0A0A0
        ),
        RecolorTarget.includeButtonMethodCall(
            new MethodInfo("codechicken.nei.config.DataDumper drawButton(IILcodechicken/lib/vec/Rectangle4i;Ljava/lang/String;)V"),
            new MethodInfo("codechicken.lib.gui.GuiDraw drawStringC(Ljava/lang/String;IIIII)V"),
            0xE0E0E0, 0xFFFFA0, 0xA0A0A0
        ),
    };

    public static final Map<String, List<RecolorTarget>> classesToTransform = new HashMap<>();
    static {
        for (RecolorTarget target : recolorTargets) {
            classesToTransform.computeIfAbsent(target.method.className, _ -> new ArrayList<>()).add(target);
        }
    }

    public boolean transformClassNode(ClassNode cn, String className, boolean isObf) {
        boolean changed = false;

        List<RecolorTarget> targets = classesToTransform.get(className);
        if (targets == null) { return false; }
        for (RecolorTarget target : targets) {
            for (MethodNode mn : cn.methods) {
                if (!mn.name.equals(target.method.getName(isObf))) { continue; }
                if (!mn.desc.equals(target.method.desc)) { continue; }
                if (target.targetsCall()) {
                    changed = markRecolorCallScoped(mn, target.calledMethod, isObf, target.recolorEnabled, target.buttonColors) || changed;
                } else {
                    changed = markRecolorMethodScoped(mn, target.method, target.recolorEnabled, target.buttonColors) || changed;
                }
            }
        }

        return changed;
    }

    private static void insertEnterSection(InsnList insnList, AbstractInsnNode before, boolean recolorEnabled, @Nullable ButtonColors buttonColors) {
        if (buttonColors == null) {
            insnList.insertBefore(before, new InsnNode(recolorEnabled ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
            insnList.insertBefore(before, new MethodInsnNode(Opcodes.INVOKESTATIC, BATCHINGFONTRENDERER, "enterRecolorSection", "(Z)Z", false));
        } else {
            insnList.insertBefore(before, new LdcInsnNode(buttonColors.enabled()));
            insnList.insertBefore(before, new LdcInsnNode(buttonColors.hovered()));
            insnList.insertBefore(before, new LdcInsnNode(buttonColors.disabled()));
            insnList.insertBefore(before, new MethodInsnNode(Opcodes.INVOKESTATIC, BATCHINGFONTRENDERER, "enterButtonSection", "(III)Z", false));
        }
    }

    private static MethodInsnNode exitSectionCall(@Nullable ButtonColors buttonColors) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, BATCHINGFONTRENDERER, buttonColors == null ? "exitRecolorSection" : "exitButtonSection", "(Z)V", false);
    }

    private boolean markRecolorCallScoped(MethodNode mn, MethodInfo calledMethod, boolean isObf, boolean recolorEnabled, @Nullable ButtonColors buttonColors) {
        boolean changed = false;
        InsnList insnList = mn.instructions;
        for (AbstractInsnNode insn = insnList.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode min) {
                if (!min.owner.equals(calledMethod.classNameSlash)) { continue; }
                if (!min.name.equals(calledMethod.getName(isObf))) { continue; }
                if (!min.desc.equals(calledMethod.desc)) { continue; }
                int maxLocals = mn.maxLocals;

                insertEnterSection(insnList, min, recolorEnabled, buttonColors);
                insnList.insertBefore(min, new VarInsnNode(Opcodes.ISTORE, maxLocals));

                insnList.insert(min, exitSectionCall(buttonColors));
                insnList.insert(min, new VarInsnNode(Opcodes.ILOAD, maxLocals));

                LOGGER.info("Added {}-recolor flags at call site of {}", recolorEnabled ? "enable" : "disable", calledMethod.toString());
                changed = true;
            }
        }
        return changed;
    }

    private boolean markRecolorMethodScoped(MethodNode mn, MethodInfo method, boolean recolorEnabled, @Nullable ButtonColors buttonColors) {
        InsnList insnList = mn.instructions;
        AbstractInsnNode firstInsn = insnList.getFirst();
        AbstractInsnNode lastReturn = null;
        for (AbstractInsnNode insn = insnList.getLast(); insn != null; insn = insn.getPrevious()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                lastReturn = insn;
                break;
            }
        }
        if (lastReturn == null) { return false; }
        int maxLocals = mn.maxLocals;

        insertEnterSection(insnList, firstInsn, recolorEnabled, buttonColors);
        insnList.insertBefore(firstInsn, new VarInsnNode(Opcodes.ISTORE, maxLocals));

        insnList.insertBefore(lastReturn, new VarInsnNode(Opcodes.ILOAD, maxLocals));
        insnList.insertBefore(lastReturn, exitSectionCall(buttonColors));

        LOGGER.info("Added {}-recolor flags in {}", recolorEnabled ? "enable" : "disable", method.toString());
        return true;
    }
}
