package com.gtnewhorizons.angelica.mixins;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import lombok.Getter;
import org.embeddedt.archaicfix.config.ArchaicConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum ArchaicMixins {
    // COMMON MIXINS
    common_chickenchunks_MixinPlayerChunkViewerManager(Side.COMMON, Phase.EARLY, require(TargetedMod.CHICKENCHUNKS), "chickenchunks.MixinPlayerChunkViewerManager"),
    common_core(Side.COMMON, Phase.EARLY, always(),
        "core.AccessorEntityLiving",
        "core.MixinASMData",
        "core.MixinAxisAlignedBB",
        "core.MixinBlock",
        "core.MixinChunk",
        "core.MixinChunkProviderHell",
        "core.MixinChunkProviderServer",
        "core.MixinEnchantmentHelper",
        "core.MixinEntityLiving",
        "core.MixinEntityPlayerMP",
        "core.MixinEntityTrackerEntry",
        "core.MixinEntityVillager",
        "core.MixinEntityXPOrb",
        "core.MixinForgeChunkManager",
        "core.MixinLongHashMap",
        "core.MixinMapGenStructure",
        "core.MixinMaterialLiquid",
        "core.MixinMerchantRecipe",
        "core.MixinSpawnerAnimals",
        "core.MixinStructureStart",
        "core.MixinWorld",
        "core.MixinWorldChunkManager",
        "core.MixinWorldServer"
    ),
    common_core_client(Side.CLIENT, Phase.EARLY, always(),
        "core.AccessorSplashProgress",
        "core.MixinBlockFence",
        "core.MixinChunkProviderClient",
        "core.MixinEntityRenderer",
        "core.MixinFMLClientHandler",
        "core.MixinGuiBeaconButton",
        "core.MixinGuiButton",
        "core.MixinGuiCreateWorld",
        "core.MixinGuiIngameForge",
        "core.MixinIntegratedServer",
        "core.MixinMinecraft",
        "core.MixinNetHandlerPlayClient",
        "core.MixinSplashProgress",
        "core.MixinThreadDownloadImageData",
        "core.MixinRenderItem"
    ),

    common_core_MixinModCandidate(Side.COMMON, Phase.EARLY, avoid(TargetedMod.COFHCORE), "core.MixinModCandidate"),
    common_core_MixinNetworkDispatcher(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinNetworkDispatcher"),
    common_core_MixinNetworkManager(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinNetworkManager"),
    common_core_MixinEmbeddedChannel(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinEmbeddedChannel"),
    common_core_MixinNetHandlerPlayServer(Side.COMMON, Phase.EARLY, always(), "core.MixinNetHandlerPlayServer"),
    common_core_MixinObjectIntIdentityMap(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.optimizeObjectIntIdentityMap, "core.MixinObjectIntIdentityMap"),

    common_mrtjp_MixinBlockUpdateHandler(Side.COMMON, Phase.LATE, require(TargetedMod.MRTJPCORE), "mrtjp.MixinBlockUpdateHandler"),

    // CLIENT MIXINS



    client_core_MixinSkinManager(Side.CLIENT, Phase.EARLY, m -> ArchaicConfig.fixSkinMemoryLeak, "core.MixinSkinManager"),
    client_core_MixinWorldRenderer(Side.CLIENT, Phase.EARLY, m -> !Boolean.parseBoolean(System.getProperty("archaicFix.disableMC129", "false")), "core.MixinWorldRenderer"),

    client_core_MixinNetHandlerLoginClient(Side.CLIENT, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinNetHandlerLoginClient"),

    client_lighting_client(Side.CLIENT, Phase.EARLY, phosphor(),
        "lighting.MixinMinecraft",
        "lighting.MixinWorld",
        "lighting.MixinChunkCache"),

    client_lighting_common(Side.COMMON, Phase.EARLY, phosphor(),
        "lighting.MixinAnvilChunkLoader",
        "lighting.MixinChunk",
        "lighting.MixinChunkProviderServer",
        "lighting.MixinChunkVanilla",
        "lighting.MixinExtendedBlockStorage",
        "lighting.MixinSPacketChunkData",
        "lighting.MixinWorld_Lighting"),

    client_renderdistance(Side.CLIENT, Phase.EARLY, m -> ArchaicConfig.raiseMaxRenderDistance,
        "renderdistance.MixinGameSettings",
        "renderdistance.MixinRenderGlobal",
        "renderdistance.MixinPlayerManager"),

    common_botania_MixinBlockSpecialFlower(Side.COMMON, Phase.LATE, require(TargetedMod.BOTANIA), "botania.MixinBlockSpecialFlower"),

    common_extrautils(Side.COMMON, Phase.LATE, require(TargetedMod.EXTRAUTILS),
        "extrautils.MixinEventHandlerSiege",
        "extrautils.MixinEventHandlerServer",
        "extrautils.MixinItemDivisionSigil",
        "extrautils.MixinTileEntityTrashCan"),

    client_journeymap_MixinTileDrawStep(Side.CLIENT, Phase.LATE, require(TargetedMod.JOURNEYMAP).and(m -> ArchaicConfig.removeJourneymapDebug), "journeymap.MixinTileDrawStep"),


    // The modFilter argument is a predicate, so you can also use the .and(), .or(), and .negate() methods to mix and match multiple predicates.
    ;

    @Getter
    public final Side side;
    @Getter
    public final Phase phase;

    @Getter
    public final Predicate<Collection<TargetedMod>> filter;
    private final List<String> mixinClasses = new ArrayList<>();

    ArchaicMixins(Side side, Phase phase, Predicate<Collection<TargetedMod>> filter, String... mixinClasses) {
        this.side = side;
        this.phase = phase;
        this.filter = filter;
        this.mixinClasses.addAll(Arrays.asList(mixinClasses));
    }

    static Predicate<Collection<TargetedMod>> phosphor() {
        return m -> ArchaicConfig.enablePhosphor;
    }

    static Predicate<Collection<TargetedMod>> require(TargetedMod in) {
        return modList -> modList.contains(in);
    }

    static Predicate<Collection<TargetedMod>> avoid(TargetedMod in) {
        return modList -> !modList.contains(in);
    }

    static Predicate<Collection<TargetedMod>> always() {
        return m -> true;
    }

    enum Side {
        COMMON,
        CLIENT
    }

    public enum Phase {
        EARLY,
        LATE
    }

    public boolean shouldLoadSide() {
        return (side == Side.COMMON
                || (side == Side.CLIENT && FMLLaunchHandler.side().isClient()));
    }

    public List<String> getMixins() {
        return this.mixinClasses.stream().map((m) -> "archaic." + side.name().toLowerCase(Locale.ROOT) + "." + m).collect(Collectors.toList());

    }
}
