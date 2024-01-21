package com.gtnewhorizons.angelica.mixins;

import lombok.Getter;

public enum TargetedMod {

      VANILLA("Minecraft", null)
    , FASTCRAFT("FastCraft", "fastcraft.Tweaker")
    , OPTIFINE("Optifine", "optifine.OptiFineForgeTweaker", "Optifine")
    , ARCHAICFIX("ArchaicFix", "org.embeddedt.archaicfix.ArchaicCore", "archaicfix")
    , BOTANIA("Botania", null, "Botania")
    , CAMPFIRE_BACKPORT("CampfireBackport", null, "campfirebackport")
    , CHICKENCHUNKS("ChickenChunks", null, "ChickenChunks")
    , COFHCORE("CoFHCore", "cofh.asm.LoadingPlugin", "CoFHCore")
    , DYNAMIC_SURROUNDINGS_MIST("Dynamic Surroundings", "org.blockartistry.mod.DynSurround.mixinplugin.DynamicSurroundingsEarlyMixins", "dsurround")
    , DYNAMIC_SURROUNDINGS_ORIGINAL("Dynamic Surroundings", "org.blockartistry.mod.DynSurround.asm.TransformLoader", "dsurround")
    , EXTRAUTILS("ExtraUtilities", null, "ExtraUtilities")
    , GTNHLIB("GTNHLib", "com.gtnewhorizon.gtnhlib.core.GTNHLibCore", "gtnhlib")
    , IC2("IC2", "ic2.core.coremod.IC2core", "IC2")
    , JABBA("JABBA", null, "JABBA")
    , JOURNEYMAP("JourneyMap", null, "journeymap")
    , LWJGL3IFY("lwjgl3ify", "me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod", "lwjgl3ify")
    , MRTJPCORE("MrTJPCore", null, "MrTJPCoreMod")
    , PROJECTRED_ILLUMINATION("ProjectRed Illumination", null, "ProjRed|Illumination")
    , SMART_RENDER("Smart Render", null, "SmartRender")
    , THAUMCRAFT("Thaumcraft", null, "Thaumcraft")
    , TWILIGHT_FOREST("TwilightForest", null, "TwilightForest")
    , WITCHERY("Witchery", null, "witchery")
    ;


    /** The "name" in the @Mod annotation */
    @Getter
    public final String modName;
    /** Class that implements the IFMLLoadingPlugin interface */
    @Getter
    public final String coreModClass;
    /** The "modid" in the @Mod annotation */
    @Getter
    public final String modId;

    TargetedMod(String modName, String coreModClass) {
        this(modName, coreModClass, null);
    }

    TargetedMod(String modName, String coreModClass, String modId) {
        this.modName = modName;
        this.coreModClass = coreModClass;
        this.modId = modId;
    }

    @Override
    public String toString() {
        return "TargetedMod{modName='" + modName + "', coreModClass='" + coreModClass + "', modId='" + modId + "'}";
    }
}
