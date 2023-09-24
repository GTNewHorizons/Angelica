package com.gtnewhorizons.angelica.mixins;

import lombok.Getter;

public enum TargetedMod {

    VANILLA("Minecraft", null),
    FASTCRAFT("FastCraft", "fastcraft.Tweaker"),
    OPTIFINE("Optifine", "optifine.OptiFineForgeTweaker", "Optifine"),
    GTNHLIB("GTNHLib", "com.gtnewhorizon.gtnhlib.core.GTNHLibCore", "gtnhlib"),
    LWJGL3IFY("lwjgl3ify", "me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod", "lwjgl3ify"),
    CHICKENCHUNKS("ChickenChunks", null, "ChickenChunks"),
    MRTJPCORE("MrTJPCore", null, "MrTJPCoreMod"),
    BOTANIA("Botania", null, "Botania"),
    COFHCORE("CoFHCore", "cofh.asm.LoadingPlugin", "CoFHCore"),
    EXTRAUTILS("ExtraUtilities", null, "ExtraUtilities"),
    JOURNEYMAP("JourneyMap", null, "journeymap"),
    PROJECTRED_ILLUMINATION("ProjectRed Illumination", null, "ProjRed|Illumination"),
    SMART_RENDER("Smart Render", null, "SmartRender");

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
