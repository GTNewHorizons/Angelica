package org.embeddedt.archaicfix.asm;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.embeddedt.archaicfix.helpers.DragonAPIHelper;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@RequiredArgsConstructor
public enum Mixin {
    // COMMON MIXINS
    common_chickenchunks_MixinPlayerChunkViewerManager(Side.COMMON, Phase.EARLY, require(TargetedMod.CHICKENCHUNKS), "chickenchunks.MixinPlayerChunkViewerManager"),
    common_core_AccessorEntityLiving(Side.COMMON, Phase.EARLY, always(), "core.AccessorEntityLiving"),
    common_core_MixinEntityPlayerMP(Side.COMMON, Phase.EARLY, always(), "core.MixinEntityPlayerMP"),
    common_core_MixinWorldServer(Side.COMMON, Phase.EARLY, always(), "core.MixinWorldServer"),
    common_core_MixinMapGenStructure(Side.COMMON, Phase.EARLY, always(), "core.MixinMapGenStructure"),
    common_core_MixinEntityVillager(Side.COMMON, Phase.EARLY, always(), "core.MixinEntityVillager"),
    common_core_MixinMerchantRecipe(Side.COMMON, Phase.EARLY, always(), "core.MixinMerchantRecipe"),
    common_core_MixinAxisAlignedBB(Side.COMMON, Phase.EARLY, always(), "core.MixinAxisAlignedBB"),
    common_core_MixinMaterialLiquid(Side.COMMON, Phase.EARLY, always(), "core.MixinMaterialLiquid"),
    common_core_MixinChunkProviderServer(Side.COMMON, Phase.EARLY, always(), "core.MixinChunkProviderServer"),
    common_core_MixinSpawnerAnimals(Side.COMMON, Phase.EARLY, always(), "core.MixinSpawnerAnimals"),
    common_core_MixinShapedOreRecipe(Side.COMMON, Phase.EARLY, always(), "core.MixinShapedOreRecipe"),
    common_core_MixinLongHashMap(Side.COMMON, Phase.EARLY, always(), "core.MixinLongHashMap"),
    common_core_MixinBlock(Side.COMMON, Phase.EARLY, always(), "core.MixinBlock"),
    common_core_MixinEnchantmentHelper(Side.COMMON, Phase.EARLY, always(), "core.MixinEnchantmentHelper"),
    common_core_MixinWorldChunkManager(Side.COMMON, Phase.EARLY, always(), "core.MixinWorldChunkManager"),
    common_core_MixinShapedRecipes(Side.COMMON, Phase.EARLY, always(), "core.MixinShapedRecipes"),
    common_core_MixinShapelessOreRecipe(Side.COMMON, Phase.EARLY, always(), "core.MixinShapelessOreRecipe"),
    common_core_MixinShapelessRecipes(Side.COMMON, Phase.EARLY, always(), "core.MixinShapelessRecipes"),
    common_core_MixinEntityLiving(Side.COMMON, Phase.EARLY, always(), "core.MixinEntityLiving"),
    common_core_MixinWorld(Side.COMMON, Phase.EARLY, always(), "core.MixinWorld"),
    common_core_MixinEntityTrackerEntry(Side.COMMON, Phase.EARLY, always(), "core.MixinEntityTrackerEntry"),
    common_core_MixinEntityXPOrb(Side.COMMON, Phase.EARLY, always(), "core.MixinEntityXPOrb"),
    common_core_MixinEntityItem(Side.COMMON, Phase.EARLY, avoid(TargetedMod.SHIPSMOD).and(m -> ArchaicConfig.itemLagReduction), "core.MixinEntityItem"),
    common_core_MixinEntity(Side.COMMON, Phase.EARLY, always(), "core.MixinEntity"),
    common_core_MixinForgeChunkManager(Side.COMMON, Phase.EARLY, always(), "core.MixinForgeChunkManager"),
    common_core_MixinChunk(Side.COMMON, Phase.EARLY, always(), "core.MixinChunk"),
    common_core_MixinStructureStart(Side.COMMON, Phase.EARLY, always(), "core.MixinStructureStart"),
    common_core_MixinOreDictionary(Side.COMMON, Phase.EARLY, always(), "core.MixinOreDictionary"),
    common_core_MixinChunkProviderHell(Side.COMMON, Phase.EARLY, always(), "core.MixinChunkProviderHell"),
    common_core_MixinASMData(Side.COMMON, Phase.EARLY, always(), "core.MixinASMData"),
    common_core_MixinModCandidate(Side.COMMON, Phase.EARLY, avoid(TargetedMod.COFHCORE), "core.MixinModCandidate"),
    common_core_MixinNetworkDispatcher(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinNetworkDispatcher"),
    common_core_MixinNetworkManager(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinNetworkManager"),
    common_core_MixinEmbeddedChannel(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinEmbeddedChannel"),
    common_core_MixinNetHandlerPlayServer(Side.COMMON, Phase.EARLY, always(), "core.MixinNetHandlerPlayServer"),
    common_core_MixinObjectIntIdentityMap(Side.COMMON, Phase.EARLY, m -> ArchaicConfig.optimizeObjectIntIdentityMap, "core.MixinObjectIntIdentityMap"),
    common_gt6_MixinCR(Side.COMMON, Phase.LATE, require(TargetedMod.GREGTECH6), "gt6.MixinCR"),
    common_lighting_MixinAnvilChunkLoader(Side.COMMON, Phase.EARLY, phosphor(), "lighting.MixinAnvilChunkLoader"),
    common_lighting_MixinChunk(Side.COMMON, Phase.EARLY, phosphor(), "lighting.MixinChunk"),
    common_lighting_MixinChunkProviderServer(Side.COMMON, Phase.EARLY, phosphor(), "lighting.MixinChunkProviderServer"),
    common_lighting_MixinChunkVanilla(Side.COMMON, Phase.EARLY, phosphor(), "lighting.MixinChunkVanilla"),
    common_lighting_MixinExtendedBlockStorage(Side.COMMON, Phase.EARLY, phosphor(), "lighting.MixinExtendedBlockStorage"),
    common_lighting_MixinSPacketChunkData(Side.COMMON, Phase.EARLY, phosphor(), "lighting.MixinSPacketChunkData"),
    common_lighting_MixinWorld(Side.COMMON, Phase.EARLY, phosphor(), "lighting.MixinWorld_Lighting"),
    common_mrtjp_MixinBlockUpdateHandler(Side.COMMON, Phase.LATE, require(TargetedMod.MRTJPCORE), "mrtjp.MixinBlockUpdateHandler"),
    // CLIENT MIXINS
    client_core_MixinThreadDownloadImageData(Side.CLIENT, Phase.EARLY, always(), "core.MixinThreadDownloadImageData"),
    client_core_MixinBlockFence(Side.CLIENT, Phase.EARLY, always(), "core.MixinBlockFence"),
    client_core_MixinEntityRenderer(Side.CLIENT, Phase.EARLY, always(), "core.MixinEntityRenderer"),
    client_core_MixinGuiBeaconButton(Side.CLIENT, Phase.EARLY, always(), "core.MixinGuiBeaconButton"),
    client_core_MixinGuiButton(Side.CLIENT, Phase.EARLY, always(), "core.MixinGuiButton"),
    client_core_MixinGuiContainerCreative(Side.CLIENT, Phase.EARLY, always(), "core.MixinGuiContainerCreative"),
    client_core_MixinIntegratedServer(Side.CLIENT, Phase.EARLY, always(), "core.MixinIntegratedServer"),
    client_core_MixinSkinManager(Side.CLIENT, Phase.EARLY, m -> ArchaicConfig.fixSkinMemoryLeak, "core.MixinSkinManager"),
    client_core_MixinChunkProviderClient(Side.CLIENT, Phase.EARLY, always(), "core.MixinChunkProviderClient"),
    client_core_MixinWorldRenderer(Side.CLIENT, Phase.EARLY, m -> !Boolean.valueOf(System.getProperty("archaicFix.disableMC129", "false")), "core.MixinWorldRenderer"),
    client_core_MixinMinecraft(Side.CLIENT, Phase.EARLY, always(), "core.MixinMinecraft"),
    client_core_MixinNetHandlerPlayClient(Side.CLIENT, Phase.EARLY, always(), "core.MixinNetHandlerPlayClient"),
    client_core_MixinGuiCreateWorld(Side.CLIENT, Phase.EARLY, always(), "core.MixinGuiCreateWorld"),
    client_core_MixinGuiIngameForge(Side.CLIENT, Phase.EARLY, always(), "core.MixinGuiIngameForge"),
    client_core_MixinFMLClientHandler(Side.CLIENT, Phase.EARLY, always(), "core.MixinFMLClientHandler"),
    client_core_MixinNetHandlerLoginClient(Side.CLIENT, Phase.EARLY, m -> ArchaicConfig.fixLoginRaceCondition, "core.MixinNetHandlerLoginClient"),
    client_core_MixinSplashProgress(Side.CLIENT, Phase.EARLY, always(), "core.MixinSplashProgress"),
    client_core_AccessorSplashProgress(Side.CLIENT, Phase.EARLY, always(), "core.AccessorSplashProgress"),
    client_core_MixinRenderItem(Side.CLIENT, Phase.EARLY, always(), "core.MixinRenderItem"),
    client_lighting_MixinMinecraft(Side.CLIENT, Phase.EARLY, phosphor(), "lighting.MixinMinecraft"),
    client_lighting_MixinWorld(Side.CLIENT, Phase.EARLY, phosphor(), "lighting.MixinWorld"),
    client_lighting_MixinChunkCache(Side.CLIENT, Phase.EARLY, phosphor(), "lighting.MixinChunkCache"),

    client_optifine_MixinVersionCheckThread(Side.CLIENT, Phase.EARLY, require(TargetedMod.OPTIFINE).and(m -> ArchaicConfig.disableOFVersionCheck), "optifine.MixinVersionCheckThread"),

    client_occlusion_MixinChunk(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableOcclusionTweaks), "occlusion.MixinChunk"),
    client_occlusion_MixinEntityRenderer(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableOcclusionTweaks), "occlusion.MixinEntityRenderer"),
    client_occlusion_MixinRenderGlobal(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableOcclusionTweaks), "occlusion.MixinRenderGlobal"),
    client_occlusion_MixinGuiVideoSettings(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableOcclusionTweaks), "occlusion.MixinGuiVideoSettings"),
    client_occlusion_MixinWorldRenderer(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableOcclusionTweaks), "occlusion.MixinWorldRenderer"),

    client_renderdistance_MixinGameSettings(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.raiseMaxRenderDistance), "renderdistance.MixinGameSettings"),
    client_renderdistance_MixinRenderGlobal(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.raiseMaxRenderDistance), "renderdistance.MixinRenderGlobal"),
    common_renderdistance_MixinPlayerManager(Side.COMMON, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.raiseMaxRenderDistance), "renderdistance.MixinPlayerManager"),

    client_threadedupdates_MixinRenderBlocks(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableThreadedChunkUpdates && ArchaicConfig.enableOcclusionTweaks), "threadedupdates.MixinRenderBlocks"),
    client_threadedupdates_MixinWorldRenderer(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableThreadedChunkUpdates && ArchaicConfig.enableOcclusionTweaks), "threadedupdates.MixinWorldRenderer"),
    client_threadedupdates_MixinTessellator(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableThreadedChunkUpdates && ArchaicConfig.enableOcclusionTweaks), "threadedupdates.MixinTessellator"),
    client_threadedupdates_MixinTessellator_Debug(Side.CLIENT, Phase.EARLY, avoid(TargetedMod.OPTIFINE).and(avoid(TargetedMod.FASTCRAFT)).and(m -> ArchaicConfig.enableThreadedChunkUpdates && ArchaicConfig.enableOcclusionTweaks && Boolean.parseBoolean(System.getProperty("archaicfix.debug.verifyTessellatorAccessThread"))), "threadedupdates.MixinTessellator_Debug"),

    // MOD-FILTERED MIXINS
    common_lighting_fastcraft_MixinChunk(Side.COMMON, Phase.EARLY, require(TargetedMod.FASTCRAFT).and(phosphor()), "lighting.fastcraft.MixinChunk"),
    common_lighting_fastcraft_MixinChunkProviderServer(Side.COMMON, Phase.EARLY, require(TargetedMod.FASTCRAFT).and(phosphor()), "lighting.fastcraft.MixinChunkProviderServer"),
    common_lighting_fastcraft_MixinWorld(Side.COMMON, Phase.EARLY, require(TargetedMod.FASTCRAFT).and(phosphor()), "lighting.fastcraft.MixinWorld"),

    common_botania_MixinBlockSpecialFlower(Side.COMMON, Phase.LATE, require(TargetedMod.BOTANIA), "botania.MixinBlockSpecialFlower"),

    common_extrautils_MixinEventHandlerSiege(Side.COMMON, Phase.LATE, require(TargetedMod.EXTRAUTILS), "extrautils.MixinEventHandlerSiege"),
    common_extrautils_MixinEventHandlerServer(Side.COMMON, Phase.LATE, require(TargetedMod.EXTRAUTILS), "extrautils.MixinEventHandlerServer"),
    common_extrautils_MixinItemDivisionSigil(Side.COMMON, Phase.LATE, require(TargetedMod.EXTRAUTILS), "extrautils.MixinItemDivisionSigil"),
    common_extrautils_MixinTileEntityTrashCan(Side.COMMON, Phase.LATE, require(TargetedMod.EXTRAUTILS), "extrautils.MixinTileEntityTrashCan"),

    client_journeymap_MixinTileDrawStep(Side.CLIENT, Phase.LATE, require(TargetedMod.JOURNEYMAP).and(m -> ArchaicConfig.removeJourneymapDebug), "journeymap.MixinTileDrawStep"),


    // The modFilter argument is a predicate, so you can also use the .and(), .or(), and .negate() methods to mix and match multiple predicates.
    ;

    @Getter
    public final Side side;
    @Getter
    public final Phase phase;
    @Getter
    public final Predicate<Collection<TargetedMod>> filter;
    private final String mixin;

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

    public String getMixin() {
        return side.name().toLowerCase(Locale.ROOT) + "." + mixin;
    }
}
