package com.gtnewhorizons.angelica.glsm.loading;

import com.gtnewhorizons.angelica.glsm.loading.TransformerNarrower.NarrowRule;

import java.util.List;

public final class EcosystemNarrowRules {

    public static final List<NarrowRule> ALL = List.of(
        new NarrowRule("DragonAPI", "Reika.DragonAPI.ASM", List.of(
            "Reika.DragonAPI.ASM.Patchers",
            "Reika.DragonAPI.ASM.Profiling",
            "Reika.DragonAPI.ASM.APIStripper",
            "Reika.DragonAPI.ASM.ClassReparenter",
            "Reika.DragonAPI.ASM.DependentMethodStripper",
            "Reika.DragonAPI.ASM.DragonAPIClassTransformer",
            "Reika.DragonAPI.ASM.FMLItemBlockPatch",
            "Reika.DragonAPI.ASM.FluidNamePatch",
            "Reika.DragonAPI.ASM.InterfaceInjector",
            "Reika.DragonAPI.ASM.SpecialBiomePlacement",
            "Reika.DragonAPI.ASM.StructureLootHooks"
        )),

        new NarrowRule("Xaeros_common", "xaero.common.core", List.of(
            "xaero.common.core.transformer"
        )),

        new NarrowRule("Xaeros_map", "xaero.map.core", List.of(
            "xaero.map.core.transformer"
        )),

        new NarrowRule("AdvancedLightsabers", "com.fiskmods.lightsabers.asm", List.of(
            "com.fiskmods.lightsabers.asm.ALLoadingPlugin",
            "com.fiskmods.lightsabers.asm.ASMHooks",
            "com.fiskmods.lightsabers.asm.transformers"
        )),

        new NarrowRule("Ears", "com.unascribed.ears", List.of(
            "com.unascribed.ears.asm",
            "com.unascribed.ears.common.agent"
        )),

        new NarrowRule("Alfheim", "alfheim.common.core.asm.hook", List.of(
            "alfheim.common.core.asm.hook.AlfheimFieldHookHandler",
            "alfheim.common.core.asm.hook.AlfheimHPHooks",
            "alfheim.common.core.asm.hook.Botania18AndUpBackport",
            "alfheim.common.core.asm.hook.ElementalDamageAdapter",
            "alfheim.common.core.asm.hook.integration",
            "alfheim.common.core.asm.hook.extender.FurnaceExtender",
            "alfheim.common.core.asm.hook.extender.ItemAuraRingExtender",
            "alfheim.common.core.asm.hook.extender.ItemLensExtender",
            "alfheim.common.core.asm.hook.extender.ItemTwigWandExtender",
            "alfheim.common.core.asm.hook.extender.LightRelayExtender",
            "alfheim.common.core.asm.hook.extender.PureDaisyExtender",
            "alfheim.common.core.asm.hook.extender.QuartzExtender",
            "alfheim.common.core.asm.hook.extender.RelicHooks",
            "alfheim.common.core.asm.hook.extender.SparkExtender",
            "alfheim.common.core.asm.hook.fixes.BotaniaGlowingRenderFixes",
            "alfheim.common.core.asm.hook.fixes.CorporeaInputFix",
            "alfheim.common.core.asm.hook.fixes.GodAttributesHooks",
            "alfheim.common.core.asm.hook.fixes.RecipeAncientWillsFix"
        ))
    );

    public static final String[] LWJGL3IFY_EXCLUSIONS_SHARED = {
        "org.embeddedt.embeddium",
        "org.taumc.celeritas",
        "com.mitchej123.lwjgl.lwjgl3",
        "com.mitchej123.glsm",
        "com.gtnewhorizons.angelica.lwjgl3",
    };

    public static final String[] EARLY_REDIRECTOR_TARGETS = {
        "cn.tesseract.mycelium.",
    };

    private EcosystemNarrowRules() {}
}
