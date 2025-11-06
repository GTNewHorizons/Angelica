package com.gtnewhorizons.angelica.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import jss.notfine.config.MCPatcherForgeConfig;
import jss.notfine.config.NotFineConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum Mixins implements IMixins {

    ANGELICA_STARTUP(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.startup.MixinInitGLStateManager"
        )
    ),

    ANGELICA(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.MixinActiveRenderInfo"
            , "angelica.MixinEntityRenderer"
            , "angelica.MixinGameSettings"
            , "angelica.MixinMinecraft"
            , "angelica.MixinMinecraftServer"
            , "angelica.optimizations.MixinRendererLivingEntity"
            , "angelica.MixinFMLClientHandler"
            , "angelica.bugfixes.MixinRenderGlobal_DestroyBlock"
        )
    ),
    ANGELICA_VBO(
        new MixinBuilder()
            .setApplyIf(() -> AngelicaConfig.enableVBO)
            .setPhase(Phase.EARLY)
            .addClientMixins(
                "angelica.vbo.MixinGLAllocation"
                , "angelica.vbo.MixinModelRenderer"
                , "angelica.vbo.MixinRenderGlobal"
                , "angelica.vbo.MixinWavefrontObject"
            )
    ),
    ANGELICA_FONT_RENDERER(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableFontRenderer)
        .addClientMixins(
            "angelica.fontrenderer.MixinGuiIngameForge"
            , "angelica.fontrenderer.MixinFontRenderer"
            , "angelica.fontrenderer.MixinMCResourceAccessor"
        )
    ),

    ANGELICA_ENABLE_DEBUG(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaMod.lwjglDebug)
        .addClientMixins(
            "angelica.debug.MixinProfiler"
            , "angelica.debug.MixinSplashProgress"
            , "angelica.debug.MixinTextureManager"
        )
    ),
    ANGELICA_DYNAMIC_LIGHTS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableDynamicLights)
        .addClientMixins(
            "angelica.dynamiclights.MixinEntityRenderer"
            , "angelica.dynamiclights.MixinEntity"
            , "angelica.dynamiclights.MixinWorld"
            , "angelica.dynamiclights.MixinItemRenderer"
        )
    ),

    ANGELICA_FIX_FLUID_RENDERER_CHECKING_BLOCK_AGAIN(
        new MixinBuilder("Fix RenderBlockFluid reading the block type from the world access multiple times")
            .setPhase(Phase.EARLY)
            .addClientMixins("angelica.bugfixes.MixinRenderBlockFluid")
            .setApplyIf(() -> AngelicaConfig.fixFluidRendererCheckingBlockAgain)),

    ANGELICA_LIMIT_DROPPED_ITEM_ENTITIES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins("angelica.optimizations.MixinRenderGlobal_ItemRenderDist")
        .setApplyIf(() -> AngelicaConfig.dynamicItemRenderDistance)),

    ANGELICA_ITEM_RENDERER_OPTIMIZATION(new MixinBuilder("Optimizes in-world item rendering")
        .setPhase(Phase.EARLY)
        .addClientMixins("angelica.itemrenderer.MixinItemRenderer")
        .setApplyIf(() -> AngelicaConfig.optimizeInWorldItemRendering)),

    // Not compatible with the lwjgl debug callbacks, so disable if that's enabled
    ARCHAIC_SPLASH(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.showSplashMemoryBar && !AngelicaMod.lwjglDebug)
        .addClientMixins(
            "angelica.archaic.MixinSplashProgress$3",
            "angelica.archaic.AccessorSplashProgress"
        )
    ),

    ARCHAIC_CORE(new MixinBuilder()
        .addExcludedMod(TargetedMod.ARCHAICFIX)
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.archaic.MixinBlockFence"
            , "angelica.archaic.MixinFMLClientHandler"
            , "angelica.archaic.MixinGuiIngameForge"
            , "angelica.archaic.MixinNetHandlerPlayClient"
            , "angelica.archaic.MixinThreadDownloadImageData"
        )
    ),

    IRIS_STARTUP(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addClientMixins(
            "shaders.startup.MixinGameSettings"
            , "shaders.startup.MixinMinecraft"
            , "shaders.startup.MixinInitRenderer"
            , "shaders.startup.MixinAbstractTexture"
            , "shaders.startup.MixinTextureAtlasSprite"
            , "shaders.startup.MixinTextureMap"
        )
    ),

    SODIUM(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableSodium)
        .addClientMixins(
            "sodium.MixinBlock"
            , "sodium.MixinBlockFluidBase"
            , "sodium.AccessorBiomeColorEvent"
            , "sodium.MixinBiomeGenBase"
            , "sodium.MixinChunk"
            , "sodium.MixinChunkProviderServer"
            , "sodium.MixinClientRegistry"
            , "sodium.MixinEntity_RenderDist"
            , "sodium.MixinEntityItem_RenderDist"
            , "sodium.MixinRenderManager"
            , "sodium.MixinEntityRenderer"
            , "sodium.MixinFMLClientHandler"
            , "sodium.MixinForgeHooksClient"
            , "sodium.MixinGameSettings"
            , "sodium.MixinFrustrum"
            , "sodium.MixinMinecraft"
            , "sodium.MixinRenderBlocks"
            , "sodium.MixinRenderGlobal"
            , "sodium.MixinWorldClient"
            , "sodium.MixinTileEntity"
            , "sodium.MixinTileEntityMobSpawner"
            , "sodium.MixinEffectRenderer"
            , "sodium.MixinTileEntityRendererDispatcher"
            , "sodium.MixinLongHashMap"
            , "sodium.MixinRenderingRegistry"
            , "sodium.MixinPlayerManager"
        )
    ),

    IRIS_SHADERS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addClientMixins(
            "shaders.MixinEntityRenderer"
            , "shaders.MixinGuiIngameForge"
            , "shaders.MixinFramebuffer"
            , "shaders.MixinItem"
            , "shaders.MixinLocale"
            , "shaders.MixinOpenGlHelper"
            , "shaders.MixinRender"
            , "shaders.MixinRendererLivingEntity"
            , "shaders.MixinRenderGlobal"
            , "shaders.MixinTileEntityBeaconRenderer"
        )
    ),

    IRIS_RENDERING_NOBACKHAND(new MixinBuilder("Iris Hand Shaders")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addExcludedMod(TargetedMod.BACKHAND)
        .addClientMixins(
            "shaders.MixinItemRenderer"
        )
    ),

    IRIS_RENDERING_BACKHAND(new MixinBuilder("Iris Hand Shaders (Backhand)")
        .addRequiredMod(TargetedMod.BACKHAND)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addClientMixins(
            "shaders.MixinItemRendererBackhand"
        )
    ),

    ANGELICA_TEXTURE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris || AngelicaConfig.enableSodium)
        .addClientMixins(
            "angelica.textures.MixinTextureAtlasSprite"
            , "angelica.textures.MixinTextureUtil"
        )),

    ANGELICA_ZOOM(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableZoom)
        .addClientMixins(
            "angelica.zoom.MixinEntityRenderer_Zoom",
            "angelica.zoom.MixinMinecraft_Zoom",
            "angelica.zoom.MixinMouseFilter"
        )),

    HUD_CACHING(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableHudCaching)
        .addClientMixins(
            "angelica.hudcaching.MixinGuiIngame",
            "angelica.hudcaching.MixinGuiIngameForge",
            "angelica.hudcaching.MixinRenderGameOverlayEvent",
            "angelica.hudcaching.MixinEntityRenderer_HUDCaching",
            "angelica.hudcaching.MixinFramebuffer_HUDCaching",
            "angelica.hudcaching.MixinGuiIngame_HUDCaching",
            "angelica.hudcaching.MixinGuiIngameForge_HUDCaching",
            "angelica.hudcaching.MixinRenderItem")
    ),

    OPTIMIZE_WORLD_UPDATE_LIGHT(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.ARCHAICFIX)
        .setApplyIf(() -> AngelicaConfig.optimizeWorldUpdateLight)
        .addCommonMixins("angelica.lighting.MixinWorld_FixLightUpdateLag")),

    SPEEDUP_VANILLA_ANIMATIONS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.speedupAnimations)
        .addClientMixins(
            "angelica.animation.MixinTextureAtlasSprite",
            "angelica.animation.MixinTextureMap",
            "angelica.animation.MixinBlockFire",
            "angelica.animation.MixinMinecraftForgeClient",
            "angelica.animation.MixinChunkCache",
            "angelica.animation.MixinRenderBlocks",
            "angelica.animation.MixinRenderBlockFluid",
            "angelica.animation.MixinWorldRenderer",
            "angelica.animation.MixinRenderItem")),

    SCALED_RESOUTION_UNICODE_FIX(new MixinBuilder("Removes unicode languages gui scaling being forced to even values")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.removeUnicodeEvenScaling)
        .addClientMixins("angelica.bugfixes.MixinScaledResolution_UnicodeFix")),

    SECURITYCRAFT_COMPAT(new MixinBuilder("Fix reflection in SecurityCraft for compat with Angelica")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.SECURITYCRAFT)
        .setApplyIf(() -> CompatConfig.fixSecurityCraft)
        .addClientMixins(
            "client.securitycraft.MixinBlockReinforcedFenceGate",
            "client.securitycraft.MixinBlockReinforcedIronBars"
        )),

    EXTRA_UTILITIES_THREAD_SAFETY(new MixinBuilder("Enable thread safety fixes in Extra Utilities")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.EXTRAUTILS)
        .setApplyIf(() -> CompatConfig.fixExtraUtils)
        .addClientMixins(
            "client.extrautils.MixinRenderBlockConnectedTextures",
            "client.extrautils.MixinRenderBlockConnectedTexturesEthereal",
            "client.extrautils.MixinIconConnectedTexture")),

    MFR_THREAD_SAFETY(new MixinBuilder("Enable thread safety fixes for MineFactory Reloaded")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.MINEFACTORY_RELOADED)
        .setApplyIf(() -> CompatConfig.fixMinefactoryReloaded)
        .addClientMixins("client.minefactoryreloaded.MixinRedNetCableRenderer")),

    SPEEDUP_CAMPFIRE_BACKPORT_ANIMATIONS(new MixinBuilder("Add animation speedup support to Campfire Backport")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.CAMPFIRE_BACKPORT)
        .setApplyIf(() -> AngelicaConfig.speedupAnimations)
        .addClientMixins("client.campfirebackport.MixinRenderBlockCampfire")),

    IC2_FLUID_RENDER_FIX(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addRequiredMod(TargetedMod.IC2)
        .setApplyIf(() -> AngelicaConfig.speedupAnimations)
        .addClientMixins("angelica.textures.ic2.MixinRenderLiquidCell")),

    OPTIMIZE_TEXTURE_LOADING(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins("angelica.textures.MixinTextureUtil_OptimizeMipmap")
        .setApplyIf(() -> AngelicaConfig.optimizeTextureLoading)),

    //From NotFine
    NOTFINE_BASE_MOD(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addClientMixins(addPrefix("notfine.",
            "clouds.MixinEntityRenderer",
            "clouds.MixinGameSettings",
            //"clouds.MixinRenderGlobal",
            "clouds.MixinWorldType",

            "fix.MixinRenderItem",

            "gui.MixinGuiSlot",

            "glint.MixinRenderBiped",
            "glint.MixinRenderPlayer",

            "optimization.MixinRenderItemFrame",

            "leaves.MixinBlockLeaves",
            "leaves.MixinBlockLeavesBase",

            "particles.MixinBlockEnchantmentTable",
            "particles.MixinEffectRenderer",
            "particles.MixinWorldClient",

            "renderer.MixinRenderGlobal",

            "toggle.MixinEntityRenderer",
            "toggle.MixinGuiIngame",
            "toggle.MixinRender",
            "toggle.MixinRenderItem",

            "interpolatedtexturemap.MixinTextureMap"
        ))
    ),
    BETTER_FACE_CULLING(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> NotFineConfig.betterBlockFaceCulling)
        .addClientMixins(addPrefix("notfine.faceculling.",
            "MixinBlock",
            "MixinBlockCactus",
            "MixinBlockCarpet",
            "MixinBlockEnchantmentTable",
            "MixinBlockFarmland",
            "MixinBlockSlab",
            "MixinBlockSnow",
            "MixinBlockStairs",
            "MixinRenderBlocks"
        ))
    ),
    NOTFINE_NO_DYNAMIC_SURROUNDINGS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_MIST)
        .addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_ORIGINAL)
        .addClientMixins("notfine.toggle.MixinEntityRenderer$RenderRainSnow")
    ),
    NOTFINE_NO_CUSTOM_ITEM_TEXTURES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> !AngelicaConfig.enableMCPatcherForgeFeatures || !MCPatcherForgeConfig.CustomItemTextures.enabled)
        .addClientMixins(addPrefix("notfine.glint.",
            "MixinItemRenderer",
            "MixinRenderItem"
        ))
    ),
    NOTFINE_NATURA_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.NATURA)
        .addClientMixins(addPrefix("notfine.leaves.natura.",
            "MixinBerryBush",
            "MixinNetherBerryBush"
        ))
    ),
    NOTFINE_THAUMCRAFT_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.THAUMCRAFT)
        .addClientMixins("notfine.leaves.thaumcraft.MixinBlockMagicalLeaves")
    ),
    THAUMCRAFT_BETTER_FACE_CULLING(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> NotFineConfig.betterBlockFaceCulling)
        .addRequiredMod(TargetedMod.THAUMCRAFT)
        .addClientMixins(addPrefix("notfine.faceculling.thaumcraft.",
            "MixinBlockWoodenDevice",
            "MixinBlockStoneDevice",
            "MixinBlockTable"
        ))
    ),
    NOTFINE_TINKERS_CONSTRUCT_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.TINKERS_CONSTRUCT)
        .addClientMixins("notfine.leaves.tconstruct.MixinOreberryBush")
    ),
    NOTFINE_WITCHERY_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.WITCHERY)
        .addClientMixins("notfine.leaves.witchery.MixinBlockWitchLeaves")
    ),
    NOTFINE_TWILIGHT_FOREST_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.TWILIGHT_FOREST)
        .addClientMixins(addPrefix("notfine.leaves.twilightforest.",
            "MixinBlockTFLeaves",
            "MixinBlockTFLeaves3",
            // TODO: Verify 2.3.8.18 or later to support non NH builds?
            "MixinBlockTFMagicLeaves"
        ))
    ),
    MCPATCHER_FORGE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures)
        .addClientMixins(addPrefix("mcpatcherforge.",
            "base.MixinBlockGrass",
            "base.MixinBlockMycelium",
            "base.MixinAbstractTexture",
            "base.MixinTextureAtlasSprite",
            "base.MixinSimpleReloadableResourceManager",
            "base.MixinMinecraft"
        ))
    ),
    MCPATCHER_FORGE_RENDERPASS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> NotFineConfig.renderPass)
        .addClientMixins(addPrefix("mcpatcherforge.",
            "renderpass.MixinEntityRenderer",
            "renderpass.MixinRenderBlocks",
            "renderpass.MixinRenderGlobal",
            "renderpass.MixinWorldRenderer"
        ))
    ),
    MCPATCHER_FORGE_CUSTOM_COLORS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.cc.",
            "block.material.MixinMapColor",

            "block.MixinBlock",
            "block.MixinBlockDoublePlant",
            "block.MixinBlockGrass",
            "block.MixinBlockLeaves",
            "block.MixinBlockLilyPad",
            "block.MixinBlockLiquid",
            "block.MixinBlockOldLeaf",
            "block.MixinBlockRedstoneWire",
            "block.MixinBlockReed",
            "block.MixinBlockStem",
            "block.MixinBlockTallGrass",
            "block.MixinBlockVine",

            "client.particle.MixinEntityAuraFX",
            "client.particle.MixinEntityBubbleFX",
            "client.particle.MixinEntityDropParticleFX",
            "client.particle.MixinEntityPortalFX",
            "client.particle.MixinEntityRainFX",
            "client.particle.MixinEntityRedDustFX",
            "client.particle.MixinEntitySplashFX",
            "client.particle.MixinEntitySuspendFX",

            "client.renderer.entity.MixinRenderWolf",
            "client.renderer.entity.MixinRenderXPOrb",

            "client.renderer.tileentity.MixinTileEntitySignRenderer",

            "client.renderer.MixinEntityRenderer",
            "client.renderer.MixinItemRenderer",
            "client.renderer.MixinRenderBlocks",
            "client.renderer.MixinRenderGlobal",

            "entity.MixinEntityList",

            "item.crafting.MixinRecipesArmorDyes",

            "item.MixinItemArmor",
            "item.MixinItemBlock",
            "item.MixinItemMonsterPlacer",

            "potion.MixinPotion",
            "potion.MixinPotionHelper",

            "world.MixinWorld",
            "world.MixinWorldProvider",
            "world.MixinWorldProviderEnd",
            "world.MixinWorldProviderHell"
        ))
    ),
    MCPATCHER_FORGE_CUSTOM_ITEM_TEXTURES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomItemTextures.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.cit.",
            "client.renderer.entity.MixinRenderBiped",
            "client.renderer.entity.MixinRenderEntityLiving",
            "client.renderer.entity.MixinRenderItem",
            "client.renderer.entity.MixinRenderPlayer",
            "client.renderer.entity.MixinRenderSnowball",
            "client.renderer.MixinItemRenderer",
            "client.renderer.MixinRenderGlobal",
            "entity.MixinEntityLivingBase",
            "item.MixinItem",
            "nbt.MixinNBTTagCompound",
            "nbt.MixinNBTTagList",
            "world.MixinWorld"
        ))
    ),
    MCPATCHER_FORGE_CONNECTED_TEXTURES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ConnectedTextures.enabled)
        .addClientMixins("mcpatcherforge.ctm.MixinRenderBlocks")
    ),
    MCPATCHER_FORGE_EXTENDED_HD(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ExtendedHD.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.hd.",
            "MixinTextureClock",
            "MixinTextureCompass",
            "MixinTextureManager"
        ))
    ),
    MCPATCHER_FORGE_EXTENDED_HD_FONT(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> (AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ExtendedHD.enabled && MCPatcherForgeConfig.ExtendedHD.hdFont))
        .addExcludedMod(TargetedMod.COFHCORE)
        .addClientMixins("mcpatcherforge.hd.MixinFontRenderer")
    ),
    MCPATCHER_FORGE_RANDOM_MOBS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.RandomMobs.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.mob.",
            "MixinRender",
            "MixinRenderEnderman",
            "MixinRenderFish",
            "MixinRenderLiving",
            "MixinRenderMooshroom",
            "MixinRenderSheep",
            "MixinRenderSnowMan",
            "MixinRenderSpider",
            "MixinRenderWolf",
            "MixinEntityLivingBase"
        ))
    ),
    MCPATCHER_FORGE_SKY(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.BetterSkies.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.sky.",
            "MixinEffectRenderer",
            "MixinRenderGlobal"
        ))
    ),
    MCPATCHER_FORGE_CC_NO_CTM(new MixinBuilder("MCP:F Custom Colors, no Connected Textures")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && !MCPatcherForgeConfig.ConnectedTextures.enabled
                          && MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinRenderBlocksNoCTM")
    ),
    MCPATCHER_FORGE_CTM_NO_CC(new MixinBuilder("MCP:F Connected Textures, no Custom Colours")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && MCPatcherForgeConfig.ConnectedTextures.enabled
                          && !MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinRenderBlocksNoCC")
    ),
    MCPATCHER_FORGE_CTM_AND_CC(new MixinBuilder("MCP:F Connected Textures and Custom Colors")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && MCPatcherForgeConfig.ConnectedTextures.enabled
                          && MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinRenderBlocks")
    ),
    MCPATCHER_FORGE_CTM_OR_CC(new MixinBuilder("MCP:F Connected Textures or Custom Colors")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && MCPatcherForgeConfig.ConnectedTextures.enabled
                          || MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinTextureMap")
    ),
    //End from NotFine
    ;

    private final MixinBuilder builder;

    private static String[] addPrefix(String prefix, String... values) {
        List<String> list = new ArrayList<>(values.length);
        for (String s : values) {
            list.add(prefix + s);
        }
        return list.toArray(new String[values.length]);
    }
}
