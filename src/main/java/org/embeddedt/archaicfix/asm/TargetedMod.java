package org.embeddedt.archaicfix.asm;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;

@RequiredArgsConstructor
public enum TargetedMod {
    CHICKENCHUNKS("ChickenChunks", "ChickenChunks"),
    MRTJPCORE("MrTJPCore", "MrTJPCoreMod"),
    CHUNK_PREGENERATOR("ChunkPregenerator", "chunkpregenerator"),
    THERMALEXPANSION("ThermalExpansion", "ThermalExpansion"),
    THERMALFOUNDATION("ThermalFoundation", "ThermalFoundation"),
    GREGTECH6("GregTech", "gregtech"),
    MATTEROVERDRIVE("MatterOverdrive", "mo"),
    PROJECTE("ProjectE", "ProjectE"),
    TC4TWEAKS("TC4Tweaks", "tc4tweak"),
    FASTCRAFT("FastCraft", null),
    OPTIFINE("OptiFine", null),
    MEKANISM("Mekanism", "Mekanism"),
    BOTANIA("Botania", "Botania"),
    COFHCORE("CoFHCore", "CoFHCore"),
    EXTRAUTILS("ExtraUtilities", "ExtraUtilities"),
    DIVINERPG("DivineRPG", "divinerpg"),
    SHIPSMOD("ShipsMod", "cuchaz.ships"),
    JOURNEYMAP("JourneyMap", "journeymap"),
    AM2("ArsMagica2", "arsmagica2"),
    FOODPLUS("FoodPlus", "FoodPlus"),
    DIVERSITY("Diversity", "diversity"),
    AOA("AdventOfAscension", "nevermine")
    ;

    @Getter
    private final String modName;
    @Getter
    private final String modId;
}
