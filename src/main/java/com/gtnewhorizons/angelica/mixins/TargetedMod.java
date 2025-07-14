package com.gtnewhorizons.angelica.mixins;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;
import lombok.Getter;

@Getter
public enum TargetedMod implements ITargetMod {

    ARCHAICFIX("org.embeddedt.archaicfix.ArchaicCore", "archaicfix"),
    BACKHAND("xonin.backhand.coremod.BackhandLoadingPlugin", "backhand"),
    CAMPFIRE_BACKPORT(null, "campfirebackport"),
    COFHCORE( "cofh.asm.LoadingPlugin", "CoFHCore"),
    DYNAMIC_SURROUNDINGS_MIST("org.blockartistry.mod.DynSurround.mixinplugin.DynamicSurroundingsEarlyMixins", "dsurround"),
    DYNAMIC_SURROUNDINGS_ORIGINAL("org.blockartistry.mod.DynSurround.asm.TransformLoader", "dsurround"),
    EXTRAUTILS(null, "ExtraUtilities"),
    MINEFACTORY_RELOADED(null, "MineFactoryReloaded"),
    IC2("ic2.core.coremod.IC2core", "IC2"),
    NATURA(null, "Natura"),
    SECURITYCRAFT(null, "securitycraft"),
    THAUMCRAFT(null, "Thaumcraft"),
    TINKERS_CONSTRUCT(null, "TConstruct"),
    TWILIGHT_FOREST(null, "TwilightForest"),
    WITCHERY(null, "witchery");

    private final TargetModBuilder builder;

    TargetedMod(String coreModClass, String modId) {
        this.builder = new TargetModBuilder().setCoreModClass(coreModClass).setModId(modId);
    }
}
